import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE_URL } from './config'
import { merchantService } from './merchantService'
import { saveSession } from './tokenStorage'

function jsonResponse(status, body) {
  return new Response(body !== undefined ? JSON.stringify(body) : null, {
    status,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : {},
  })
}

describe('merchantService', () => {
  let fetchMock

  beforeEach(() => {
    sessionStorage.clear()
    saveSession('test-token', 900)
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getMyMerchant calls GET /api/merchants/me with auth header', async () => {
    const profile = { id: 10, businessName: 'UrbanNest', status: 'APPROVED' }
    fetchMock.mockResolvedValue(jsonResponse(200, profile))

    const result = await merchantService.getMyMerchant()
    expect(result).toEqual(profile)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/api/merchants/me`)
    expect(init.method).toBe('GET')
    expect(init.headers.Authorization).toBe('Bearer test-token')
  })

  it('registers business details using the current session without sending owner or approval fields', async () => {
    const details = { businessName: 'Store', registrationNumber: 'BR-001', contactName: 'Owner',
      email: 'owner@example.test', phone: '0771234567', address: 'Example Street' }
    fetchMock.mockResolvedValue(jsonResponse(201, { id: 42, status: 'PENDING' }))
    expect(await merchantService.registerMyMerchant({ ...details, userId: 999, status: 'APPROVED' }))
      .toEqual({ id: 42, status: 'PENDING' })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/api/merchants/register`)
    expect(init.method).toBe('POST')
    expect(init.headers.Authorization).toBe('Bearer test-token')
    expect(JSON.parse(init.body)).toEqual(details)
  })

  it('getAllMerchants calls GET /api/merchants/admin without or with status query', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, [])))

    await merchantService.getAllMerchants()
    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE_URL}/api/merchants/admin`)

    await merchantService.getAllMerchants('PENDING')
    expect(fetchMock.mock.calls[1][0]).toBe(`${API_BASE_URL}/api/merchants/admin?status=PENDING`)
  })

  it('getMerchantById calls GET /api/merchants/admin/{id}', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 42 }))

    const result = await merchantService.getMerchantById(42)
    expect(result).toEqual({ id: 42 })
    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE_URL}/api/merchants/admin/42`)
  })

  it('approveMerchant calls PATCH /api/merchants/admin/{id}/approve', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 42, status: 'APPROVED' }))

    const result = await merchantService.approveMerchant(42)
    expect(result.status).toBe('APPROVED')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/api/merchants/admin/42/approve`)
    expect(init.method).toBe('PATCH')
  })

  it('rejectMerchant calls PATCH /api/merchants/admin/{id}/reject with reason', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { id: 42, status: 'REJECTED' }))

    const result = await merchantService.rejectMerchant(42, 'Invalid paperwork')
    expect(result.status).toBe('REJECTED')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/api/merchants/admin/42/reject`)
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body)).toEqual({ reason: 'Invalid paperwork' })
  })

  it('getApiKeys calls GET /api/merchants/me/api-keys', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, []))

    await merchantService.getApiKeys()
    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE_URL}/api/merchants/me/api-keys`)
  })

  it('createApiKey calls POST /api/merchants/me/api-keys with key name', async () => {
    const created = { apiKey: { id: 1, name: 'Storefront' }, secretKey: 'sd_live_xxx' }
    fetchMock.mockResolvedValue(jsonResponse(201, created))

    const result = await merchantService.createApiKey('Storefront')
    expect(result).toEqual(created)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/api/merchants/me/api-keys`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ name: 'Storefront' })
  })

  it('revokeApiKey calls DELETE /api/merchants/me/api-keys/{id}', async () => {
    fetchMock.mockResolvedValue(jsonResponse(204))

    const result = await merchantService.revokeApiKey(5)
    expect(result).toBeNull()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/api/merchants/me/api-keys/5`)
    expect(init.method).toBe('DELETE')
  })

  it('getWebhookSettings calls GET /api/merchants/me/webhook', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { url: 'https://test.com', enabled: true }))

    const result = await merchantService.getWebhookSettings()
    expect(result.url).toBe('https://test.com')
    expect(fetchMock.mock.calls[0][0]).toBe(`${API_BASE_URL}/api/merchants/me/webhook`)
  })

  it('updateWebhookSettings calls PUT /api/merchants/me/webhook', async () => {
    const res = { settings: { url: 'https://test.com', enabled: true }, signingSecret: 'whsec_abc' }
    fetchMock.mockResolvedValue(jsonResponse(200, res))

    const result = await merchantService.updateWebhookSettings('https://test.com', true)
    expect(result).toEqual(res)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/api/merchants/me/webhook`)
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body)).toEqual({ url: 'https://test.com', enabled: true })
  })

  it('rotateWebhookSecret calls POST /api/merchants/me/webhook/rotate-secret', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { signingSecret: 'whsec_new' }))

    const result = await merchantService.rotateWebhookSecret()
    expect(result.signingSecret).toBe('whsec_new')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/api/merchants/me/webhook/rotate-secret`)
    expect(init.method).toBe('POST')
  })
})
