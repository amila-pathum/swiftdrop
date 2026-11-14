import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiErrorKind, callEndpoint, onUnauthorized, request } from './apiClient'
import { API_BASE_URL } from './config'
import { buildPath, ENDPOINTS } from './endpoints'
import { saveSession } from './tokenStorage'

const json = (status, body) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
})

describe('endpoint registry', () => {
  let fetchMock

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('marks only implemented and routed endpoints as available', () => {
    // Merchant applications use the existing registration/profile API.
    const available = Object.entries(ENDPOINTS)
      .filter(([, endpoint]) => endpoint.available)
      .map(([name]) => name)
    expect(available.sort()).toEqual([
      'calculateQuote', 'confirmPasswordReset', 'createPricingRule', 'createStaffUser',
      'createZone', 'currentUser', 'disablePricingRule', 'disableZone',
      'getPricingRuleById', 'getPricingRules', 'getZoneById', 'getZones',
      'googleExchange', 'login', 'logout', 'markNotificationAsRead',
      'merchantApplication', 'myMerchantApplication', 'notifications',
      'pricingQuote', 'register', 'registerCourier', 'registerMerchant',
      'trackParcel', 'unreadNotificationsCount', 'updatePricingRule', 'updateZone',
    ])
    expect(available.every((name) => ENDPOINTS[name].path.startsWith('/api/auth/')
      || ENDPOINTS[name].path.startsWith('/api/notifications')
      || ENDPOINTS[name].path.startsWith('/api/merchants/')
      || ENDPOINTS[name].path === '/api/users/me'
      || ENDPOINTS[name].path === '/api/admin/users'
      || ENDPOINTS[name].path === '/api/zones'
      || ENDPOINTS[name].path.startsWith('/api/zones/')
      || ENDPOINTS[name].path === '/api/pricing/rules'
      || ENDPOINTS[name].path.startsWith('/api/pricing/rules/')
      || ENDPOINTS[name].path === '/api/pricing/quote'
      || ENDPOINTS[name].path === '/api/parcels/track/:trackingNumber')).toBe(true)
  })

  it.each(Object.entries(ENDPOINTS).filter(([, e]) => !e.available))(
    'never calls the planned endpoint %s and reports NOT_IMPLEMENTED',
    async (_name, endpoint) => {
      const error = await callEndpoint(endpoint, {
        params: { trackingNumber: 'SD-1' }, body: {},
      }).catch((e) => e)

      expect(error.kind).toBe(ApiErrorKind.NOT_IMPLEMENTED)
      expect(error.status).toBe(501)
      expect(error.message).toMatch(/not available yet/)
      expect(fetchMock).not.toHaveBeenCalled()
    },
  )

  it('calls an available endpoint with its method and auth setting', async () => {
    saveSession('token-1', 900)
    fetchMock.mockResolvedValue(json(200, { id: 1 }))

    await expect(callEndpoint(ENDPOINTS.currentUser)).resolves.toEqual({ id: 1 })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe(`${API_BASE_URL}/api/users/me`)
    expect(init.method).toBe('GET')
    expect(init.headers.Authorization).toBe('Bearer token-1')
  })

  it('URL-encodes path parameters and requires them', () => {
    expect(buildPath('/api/parcels/track/:trackingNumber', { trackingNumber: 'A/B ?' }))
      .toBe('/api/parcels/track/A%2FB%20%3F')
    expect(() => buildPath('/api/x/:id', {})).toThrow(/Missing path parameter "id"/)
  })

  describe('onUnauthorized', () => {
    it('notifies listeners when an authenticated request gets 401', async () => {
      saveSession('token-1', 900)
      const listener = vi.fn()
      const unsubscribe = onUnauthorized(listener)
      fetchMock.mockResolvedValue(json(401, { error: 'Unauthorized' }))

      await request('/api/parcels', { auth: true }).catch(() => {})
      expect(listener).toHaveBeenCalledTimes(1)

      unsubscribe()
      await request('/api/parcels', { auth: true }).catch(() => {})
      expect(listener).toHaveBeenCalledTimes(1)
    })

    it('ignores 401 from unauthenticated requests such as a wrong password', async () => {
      const listener = vi.fn()
      const unsubscribe = onUnauthorized(listener)
      fetchMock.mockResolvedValue(json(401, { error: 'Invalid email or password' }))

      await request('/api/auth/login', { method: 'POST', body: {} }).catch(() => {})
      expect(listener).not.toHaveBeenCalled()
      unsubscribe()
    })
  })

  it('offers PUT, PATCH and DELETE helpers', async () => {
    const { apiClient } = await import('./apiClient')
    fetchMock.mockImplementation(() => Promise.resolve(json(200, {})))

    await apiClient.put('/api/a', { x: 1 })
    await apiClient.patch('/api/b', { y: 2 })
    await apiClient.delete('/api/c')

    expect(fetchMock.mock.calls.map(([, init]) => init.method)).toEqual(['PUT', 'PATCH', 'DELETE'])
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ y: 2 })
  })
})
