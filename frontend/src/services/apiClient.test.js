import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  ApiErrorKind,
  apiClient,
  onUnauthorized,
  refreshSession,
  request,
} from './apiClient'
import { API_BASE_URL } from './config'
import { clearSession, getSession, saveSession } from './tokenStorage'

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

  it('preserves field validation messages for forms', async () => {
    fetchMock.mockResolvedValue(jsonResponse(400, {
      error: 'Please correct the highlighted fields', fields: { weight: 'Weight must be positive' },
    }))
    await expect(apiClient.post('/api/pricing/quote', {})).rejects.toMatchObject({
      status: 400, fields: { weight: 'Weight must be positive' },
    })
  })

  describe('successful requests', () => {
    it('lets the browser provide multipart boundaries for image uploads', async () => {
      fetchMock.mockResolvedValue(jsonResponse(202, { syncStatus: 'PENDING' }))
      const body = new FormData()
      body.append('photo', new File(['bytes'], 'pod.png', { type: 'image/png' }))
      await request('/upload', { method: 'POST', body })
      expect(fetchMock.mock.calls[0][1].body).toBe(body)
      expect(fetchMock.mock.calls[0][1].headers).not.toHaveProperty('Content-Type')
    })
    it('reads private image responses as blobs', async () => {
      const blob = new Blob(['bytes'], { type: 'image/png' })
      fetchMock.mockResolvedValue({ ok: true, blob: async () => blob })
      expect(await request('/photo', { responseType: 'blob' })).toBe(blob)
    })
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

    it('returns text for authenticated CSV exports', async () => {
      saveSession('secret-token', 900)
      fetchMock.mockResolvedValue(new Response('"status","count"\r\n', { status: 200 }))

      await expect(request('/api/example.csv', { auth: true, responseType: 'text' }))
        .resolves.toBe('"status","count"\r\n')
      expect(fetchMock.mock.calls[0][1].headers.Accept).toBe('text/csv')
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

  describe('refresh tokens', () => {
    const REFRESH_URL = `${API_BASE_URL}/api/auth/refresh`
    const refreshed = (n = 2) => ({
      accessToken: `access-${n}`,
      tokenType: 'Bearer',
      expiresIn: 900,
      refreshToken: `refresh-${n}`,
      refreshExpiresIn: 86_400,
      user: { id: 1, role: 'CUSTOMER' },
    })
    let unauthorized
    let unsubscribe

    // An access token that has already expired, with a usable refresh token.
    const saveExpiredSession = ({ persistent = false } = {}) => saveSession('access-1', 900,
      Date.now() - 901_000, {
        persistent, refreshToken: 'refresh-1', refreshExpiresInSeconds: 86_400,
      })
    const saveValidSession = () => saveSession('access-1', 900, undefined, {
      refreshToken: 'refresh-1', refreshExpiresInSeconds: 86_400,
    })
    const urls = () => fetchMock.mock.calls.map(([url]) => url)
    const authHeaders = () => fetchMock.mock.calls
      .filter(([url]) => url !== REFRESH_URL)
      .map(([, init]) => init.headers.Authorization)

    beforeEach(() => {
      localStorage.clear()
      unauthorized = vi.fn()
      unsubscribe = onUnauthorized(unauthorized)
    })

    afterEach(() => {
      unsubscribe()
    })

    it('renews an expired access token before an authenticated request', async () => {
      saveExpiredSession()
      fetchMock
        .mockResolvedValueOnce(jsonResponse(200, refreshed()))
        .mockResolvedValueOnce(jsonResponse(200, { ok: true }))

      await expect(apiClient.get('/api/example', { auth: true })).resolves.toEqual({ ok: true })

      expect(urls()).toEqual([REFRESH_URL, `${API_BASE_URL}/api/example`])
      const [, refreshInit] = fetchMock.mock.calls[0]
      expect(refreshInit.method).toBe('POST')
      expect(refreshInit.headers.Authorization).toBeUndefined()
      expect(JSON.parse(refreshInit.body)).toEqual({ refreshToken: 'refresh-1' })
      expect(authHeaders()).toEqual(['Bearer access-2'])
      expect(getSession()).toMatchObject({ accessToken: 'access-2', refreshToken: 'refresh-2' })
      expect(unauthorized).not.toHaveBeenCalled()
    })

    it('retries a request rejected with 401 once, with the renewed token', async () => {
      saveValidSession()
      fetchMock
        .mockResolvedValueOnce(jsonResponse(401, { error: 'Invalid or expired access token' }))
        .mockResolvedValueOnce(jsonResponse(200, refreshed()))
        .mockResolvedValueOnce(jsonResponse(200, { ok: true }))

      await expect(apiClient.post('/api/example', { a: 1 }, { auth: true }))
        .resolves.toEqual({ ok: true })

      expect(authHeaders()).toEqual(['Bearer access-1', 'Bearer access-2'])
      expect(fetchMock.mock.calls[2][1].body).toBe('{"a":1}')
      expect(unauthorized).not.toHaveBeenCalled()
    })

    it('gives up after one retry and reports the session as unusable', async () => {
      saveValidSession()
      fetchMock
        .mockResolvedValueOnce(jsonResponse(401, { error: 'Invalid or expired access token' }))
        .mockResolvedValueOnce(jsonResponse(200, refreshed()))
        .mockResolvedValueOnce(jsonResponse(401, { error: 'Invalid or expired access token' }))

      const error = await apiClient.get('/api/example', { auth: true }).catch((e) => e)

      expect(error.status).toBe(401)
      expect(fetchMock).toHaveBeenCalledTimes(3)
      expect(unauthorized).toHaveBeenCalledTimes(1)
    })

    it('shares one refresh between concurrent requests', async () => {
      saveExpiredSession()
      fetchMock.mockImplementation(async (url) => (url === REFRESH_URL
        ? jsonResponse(200, refreshed())
        : jsonResponse(200, { ok: true })))

      await Promise.all([
        apiClient.get('/api/a', { auth: true }),
        apiClient.get('/api/b', { auth: true }),
        apiClient.get('/api/c', { auth: true }),
      ])

      expect(urls().filter((url) => url === REFRESH_URL)).toHaveLength(1)
      expect(authHeaders()).toEqual(['Bearer access-2', 'Bearer access-2', 'Bearer access-2'])
    })

    it('ends the session when the server refuses the refresh token', async () => {
      saveExpiredSession()
      fetchMock.mockResolvedValueOnce(
        jsonResponse(401, { error: 'Session has expired. Please sign in again' }))

      const error = await apiClient.get('/api/example', { auth: true }).catch((e) => e)

      expect(error).toBeInstanceOf(ApiError)
      expect(error.status).toBe(401)
      expect(error.kind).toBe(ApiErrorKind.UNAUTHENTICATED)
      expect(urls()).toEqual([REFRESH_URL])
      expect(unauthorized).toHaveBeenCalledTimes(1)
    })

    it('keeps the session when the refresh cannot reach the server', async () => {
      saveExpiredSession()
      fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

      const error = await apiClient.get('/api/example', { auth: true }).catch((e) => e)

      expect(error.kind).toBe(ApiErrorKind.NETWORK)
      expect(unauthorized).not.toHaveBeenCalled()
      expect(getSession()).toMatchObject({ refreshToken: 'refresh-1' })
    })

    it('does not refresh without a refresh token', async () => {
      saveSession('access-1', 900, Date.now() - 901_000)

      const error = await apiClient.get('/api/example', { auth: true }).catch((e) => e)

      expect(error.kind).toBe(ApiErrorKind.UNAUTHENTICATED)
      expect(fetchMock).not.toHaveBeenCalled()
    })

    it('does not refresh with an expired refresh token', async () => {
      saveSession('access-1', 900, Date.now() - 90_000_000, {
        refreshToken: 'refresh-1', refreshExpiresInSeconds: 86_400,
      })

      const error = await apiClient.get('/api/example', { auth: true }).catch((e) => e)

      expect(error.kind).toBe(ApiErrorKind.UNAUTHENTICATED)
      expect(fetchMock).not.toHaveBeenCalled()
    })

    it('keeps a remembered session in localStorage', async () => {
      saveExpiredSession({ persistent: true })
      fetchMock.mockResolvedValueOnce(jsonResponse(200, refreshed()))

      await refreshSession()

      expect(localStorage.getItem('swiftdrop.refreshToken')).toBe('refresh-2')
      expect(sessionStorage.getItem('swiftdrop.refreshToken')).toBeNull()
    })

    it('never restores a session that was signed out during the refresh', async () => {
      saveExpiredSession()
      let respond
      fetchMock.mockReturnValueOnce(new Promise((resolve) => {
        respond = resolve
      }))

      const pending = refreshSession()
      clearSession()
      respond(jsonResponse(200, refreshed()))

      await expect(pending).resolves.toMatchObject({ accessToken: 'access-2' })
      expect(getSession()).toBeNull()
    })

    it('uses the token another tab renewed first', async () => {
      saveExpiredSession({ persistent: true })
      fetchMock.mockImplementationOnce(async () => {
        saveSession('access-other-tab', 900, undefined, {
          persistent: true, refreshToken: 'refresh-other-tab', refreshExpiresInSeconds: 86_400,
        })
        return jsonResponse(401, { error: 'Session has expired. Please sign in again' })
      })

      await expect(refreshSession()).resolves.toMatchObject({ accessToken: 'access-other-tab' })
      expect(unauthorized).not.toHaveBeenCalled()
    })

    describe('across tabs', () => {
      // A minimal Web Locks stand-in: one holder at a time, in request order.
      function installLocks() {
        let tail = Promise.resolve()
        const request = vi.fn((name, callback) => {
          const run = tail.then(() => callback())
          tail = run.catch(() => {})
          return run
        })
        vi.stubGlobal('navigator', { ...globalThis.navigator, locks: { request } })
        return request
      }

      it('refreshes while holding the shared refresh lock', async () => {
        const request = installLocks()
        saveExpiredSession({ persistent: true })
        fetchMock.mockResolvedValueOnce(jsonResponse(200, refreshed()))

        await expect(refreshSession()).resolves.toMatchObject({ accessToken: 'access-2' })
        expect(request).toHaveBeenCalledWith('swiftdrop.refresh', expect.any(Function))
        expect(fetchMock).toHaveBeenCalledTimes(1)
      })

      it('never sends a refresh token another tab already used', async () => {
        installLocks()
        saveExpiredSession({ persistent: true })
        // Another tab holds the lock and renews the shared session first.
        let release
        const otherTab = navigator.locks.request('swiftdrop.refresh', () => new Promise((resolve) => {
          release = () => {
            saveSession('access-other-tab', 900, undefined, {
              persistent: true, refreshToken: 'refresh-other-tab', refreshExpiresInSeconds: 86_400,
            })
            resolve()
          }
        }))

        const pending = refreshSession()
        await Promise.resolve()
        release()
        await otherTab

        await expect(pending).resolves.toMatchObject({ accessToken: 'access-other-tab' })
        expect(fetchMock).not.toHaveBeenCalled()
        expect(unauthorized).not.toHaveBeenCalled()
      })

      it('sends the newest token when the other tab only renewed earlier', async () => {
        installLocks()
        saveExpiredSession({ persistent: true })
        fetchMock.mockResolvedValueOnce(jsonResponse(200, refreshed()))

        await refreshSession()

        expect(JSON.parse(fetchMock.mock.calls[0][1].body))
          .toEqual({ refreshToken: 'refresh-1' })
      })

      it('lets tabs take turns: the second refresh uses the first one\'s new token', async () => {
        installLocks()
        saveExpiredSession({ persistent: true })
        fetchMock
          .mockResolvedValueOnce(jsonResponse(200, refreshed(2)))
          .mockResolvedValueOnce(jsonResponse(200, refreshed(3)))

        await refreshSession()
        // The renewed access token expires; the next refresh must send refresh-2.
        saveSession('access-2', 1, Date.now() - 5_000, {
          persistent: true, refreshToken: 'refresh-2', refreshExpiresInSeconds: 86_400,
        })
        await refreshSession()

        expect(fetchMock.mock.calls.map(([, init]) => JSON.parse(init.body).refreshToken))
          .toEqual(['refresh-1', 'refresh-2'])
      })
    })

    it('rejects an incomplete refresh response without storing it', async () => {
      saveExpiredSession()
      fetchMock.mockResolvedValueOnce(jsonResponse(200, { ...refreshed(), refreshToken: '' }))

      const error = await refreshSession().catch((e) => e)

      expect(error.kind).toBe(ApiErrorKind.INVALID_RESPONSE)
      expect(getSession()).toMatchObject({ accessToken: 'access-1', refreshToken: 'refresh-1' })
    })
  })
})
