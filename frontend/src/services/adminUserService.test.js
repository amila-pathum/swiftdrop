import { afterEach, describe, expect, it, vi } from 'vitest'
import { createDispatcher, createStaffAccount } from './adminUserService'
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

  it('creates an administrator account', async () => {
    saveSession('admin-token', 900)
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 8, name: 'Second Admin', email: 'admin2@example.test', role: 'ADMIN', status: 'ACTIVE',
    }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    const created = await createStaffAccount({
      name: ' Second Admin ', email: ' admin2@example.test ',
      password: 'temporary-pass-123', role: 'ADMIN',
    })

    expect(created.role).toBe('ADMIN')
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      name: 'Second Admin', email: 'admin2@example.test',
      password: 'temporary-pass-123', role: 'ADMIN',
    })
  })

  it.each(['CUSTOMER', 'MERCHANT', 'COURIER', undefined])(
    'refuses to create a %s account without calling the API', async (role) => {
      saveSession('admin-token', 900)
      const fetchMock = vi.fn()
      vi.stubGlobal('fetch', fetchMock)

      await expect(createStaffAccount({
        name: 'X', email: 'x@example.test', password: 'temporary-pass-123', role,
      })).rejects.toThrow('Only dispatcher and admin accounts can be created.')
      expect(fetchMock).not.toHaveBeenCalled()
    })
})
