import { afterEach, describe, expect, it, vi } from 'vitest'
import { createDispatcher } from './adminUserService'
import { saveSession, clearSession } from './tokenStorage'

describe('admin user service', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    clearSession()
  })

  it('creates only a dispatcher and authenticates the request', async () => {
    saveSession('admin-token', 900)
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 7, name: 'Dispatcher One', email: 'dispatcher@example.test',
      role: 'DISPATCHER', status: 'ACTIVE',
    }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    const created = await createDispatcher({
      name: ' Dispatcher One ', email: ' dispatcher@example.test ', password: 'temporary-pass-123',
    })

    expect(created.role).toBe('DISPATCHER')
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toContain('/api/admin/users')
    expect(init.headers.Authorization).toBe('Bearer admin-token')
    expect(JSON.parse(init.body)).toEqual({
      name: 'Dispatcher One', email: 'dispatcher@example.test',
      password: 'temporary-pass-123', role: 'DISPATCHER',
    })
  })
})
