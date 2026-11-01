import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, ApiErrorKind, apiClient, request } from './apiClient'
import { API_BASE_URL } from './config'
import { saveSession } from './tokenStorage'

function jsonResponse(status, body) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiClient', () => {
  let fetchMock

  beforeEach(() => {
    sessionStorage.clear()
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('successful requests', () => {
    it('sends GET requests to the gateway and parses JSON', async () => {
      fetchMock.mockResolvedValue(jsonResponse(200, { ok: true }))

      await expect(apiClient.get('/api/example')).resolves.toEqual({ ok: true })

      const [url, init] = fetchMock.mock.calls[0]
      expect(url).toBe(`${API_BASE_URL}/api/example`)
      expect(init.method).toBe('GET')
      expect(init.body).toBeUndefined()
      expect(init.headers.Accept).toBe('application/json')
      expect(init.headers['Content-Type']).toBeUndefined()
    })

    it('sends POST bodies as JSON', async () => {
      fetchMock.mockResolvedValue(jsonResponse(201, { id: 1 }))

      await expect(apiClient.post('/api/example', { name: 'A' })).resolves.toEqual({ id: 1 })

      const [, init] = fetchMock.mock.calls[0]
      expect(init.method).toBe('POST')
      expect(init.headers['Content-Type']).toBe('application/json')
      expect(init.body).toBe('{"name":"A"}')
    })

    it('supports other methods through request()', async () => {
      fetchMock.mockResolvedValue(jsonResponse(200, { updated: true }))

      await request('/api/example', { method: 'PATCH', body: { a: 1 } })

      expect(fetchMock.mock.calls[0][1].method).toBe('PATCH')
    })

    it('returns null for empty successful responses', async () => {
      fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

      await expect(apiClient.get('/api/example')).resolves.toBeNull()
    })
  })

  describe('authorization header', () => {
    it('does not attach a token to public requests', async () => {
      saveSession('secret-token', 900)
      fetchMock.mockResolvedValue(jsonResponse(200, {}))

      await apiClient.post('/api/example', {})

      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
    })

    it('attaches the stored bearer token to protected requests', async () => {
      saveSession('secret-token', 900)
      fetchMock.mockResolvedValue(jsonResponse(200, {}))

      await apiClient.get('/api/example', { auth: true })

      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer secret-token')
    })

    it.each([
      ['no session', () => {}],
      ['an expired session', () => saveSession('old-token', 1, Date.now() - 5_000)],
    ])('rejects protected requests with %s without calling the server', async (_label, setUp) => {
      setUp()

      const error = await apiClient.get('/api/example', { auth: true }).catch((e) => e)

      expect(error).toBeInstanceOf(ApiError)
      expect(error.status).toBe(401)
      expect(error.kind).toBe(ApiErrorKind.UNAUTHENTICATED)
      expect(fetchMock).not.toHaveBeenCalled()
    })
  })

  describe('error responses', () => {
    it.each([
      [400, 'Invalid request data'],
      [401, 'Invalid or expired access token'],
      [403, 'Access is denied'],
      [409, 'Email is already registered'],
    ])('uses the backend error message for %i', async (status, message) => {
      fetchMock.mockResolvedValue(jsonResponse(status, { error: message }))

      const error = await apiClient.get('/api/example').catch((e) => e)

      expect(error).toBeInstanceOf(ApiError)
      expect(error.message).toBe(message)
      expect(error.status).toBe(status)
      expect(error.kind).toBe(ApiErrorKind.HTTP)
    })

    it('falls back to a generic message when the error body is not usable', async () => {
      fetchMock.mockResolvedValue(new Response('<html>oops</html>', { status: 500 }))

      const error = await apiClient.get('/api/example').catch((e) => e)

      expect(error.message).toBe('Request failed with status 500.')
      expect(error.status).toBe(500)
    })

    it.each([502, 503, 504])('reports %i as service unavailable', async (status) => {
      fetchMock.mockResolvedValue(
        jsonResponse(status, { error: 'Upstream service is unavailable' }))

      const error = await apiClient.get('/api/example').catch((e) => e)

      expect(error.kind).toBe(ApiErrorKind.SERVICE_UNAVAILABLE)
      expect(error.status).toBe(status)
      expect(error.message).toMatch(/temporarily unavailable/)
    })

    it('reports invalid JSON in a successful response', async () => {
      fetchMock.mockResolvedValue(new Response('not json', { status: 200 }))

      const error = await apiClient.get('/api/example').catch((e) => e)

      expect(error.kind).toBe(ApiErrorKind.INVALID_RESPONSE)
    })
  })

  describe('network errors', () => {
    it('reports a network failure without exposing request details', async () => {
      saveSession('secret-token', 900)
      fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

      const error = await apiClient.post('/api/example', { password: 'p@ss' }, { auth: true })
        .catch((e) => e)

      expect(error).toBeInstanceOf(ApiError)
      expect(error.kind).toBe(ApiErrorKind.NETWORK)
      expect(error.status).toBe(0)
      expect(error.message).toMatch(/Unable to reach the server/)
      expect(error.message).not.toMatch(/secret-token|p@ss/)
    })

    it('passes aborts through unchanged', async () => {
      const abort = new DOMException('Aborted', 'AbortError')
      fetchMock.mockRejectedValue(abort)

      await expect(apiClient.get('/api/example')).rejects.toBe(abort)
    })
  })
})
