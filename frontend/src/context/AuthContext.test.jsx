import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth } from './AuthContext'
import { ApiError, ApiErrorKind, request } from '../services/apiClient'
import * as authService from '../services/authService'
import { getAccessToken, getSession, saveSession } from '../services/tokenStorage'

vi.mock('../services/authService', () => ({
  register: vi.fn(),
  login: vi.fn(),
  exchangeGoogleCode: vi.fn(),
  getCurrentUser: vi.fn(),
}))

const USER = {
  id: 1,
  name: 'Test Customer',
  email: 'customer@example.com',
  role: 'CUSTOMER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
}
const FIFTEEN_MINUTES = 900_000
const DAY = 86_400_000

// Emulates the real authService.login, which stores the session before resolving.
function mockSuccessfulLogin(expiresInSeconds = 900) {
  authService.login.mockImplementation(async (email) => {
    const expiresAt = saveSession(`token-for-${email}`, expiresInSeconds)
    return { user: { ...USER, email }, expiresAt }
  })
}

// reactStrictMode applies StrictMode at the root, as in main.jsx. A <StrictMode>
// inside the wrapper does not trigger React's simulated unmount/remount.
function renderAuth({ strict = false } = {}) {
  return renderHook(() => useAuth(), { wrapper: AuthProvider, reactStrictMode: strict })
}

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i += 1) {
      await Promise.resolve()
    }
  })
}

function setVisibility(state) {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
  act(() => {
    document.dispatchEvent(new Event('visibilitychange'))
  })
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    vi.setSystemTime(new Date('2026-06-01T12:00:00Z'))
    sessionStorage.clear()
    vi.resetAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
    delete document.visibilityState
  })

  describe('session restoration', () => {
    it('starts anonymous without calling the server when there is no session', async () => {
      const { result } = renderAuth()
      await flush()

      expect(result.current.status).toBe('anonymous')
      expect(result.current.user).toBeNull()
      expect(result.current.error).toBeNull()
      expect(result.current.sessionExpired).toBe(false)
      expect(authService.getCurrentUser).not.toHaveBeenCalled()
    })

    it('restores a valid session from the backend profile', async () => {
      saveSession('stored-token', 900)
      authService.getCurrentUser.mockResolvedValue(USER)

      const { result } = renderAuth()
      expect(result.current.status).toBe('loading')
      await flush()

      expect(result.current.status).toBe('authenticated')
      expect(result.current.user).toEqual(USER)
      expect(authService.getCurrentUser).toHaveBeenCalledTimes(1)
      expect(vi.getTimerCount()).toBe(1)
    })

    it('ends an expired stored session without calling the server', async () => {
      saveSession('old-token', 60, Date.now() - 120_000)

      const { result } = renderAuth()
      await flush()

      expect(result.current.status).toBe('anonymous')
      expect(result.current.sessionExpired).toBe(true)
      expect(getSession()).toBeNull()
      expect(authService.getCurrentUser).not.toHaveBeenCalled()
    })

    it('clears the session when the backend rejects the token', async () => {
      saveSession('stale-token', 900)
      authService.getCurrentUser.mockRejectedValue(
        new ApiError('Invalid or expired access token', { status: 401 }))

      const { result } = renderAuth()
      await flush()

      expect(result.current.status).toBe('anonymous')
      expect(result.current.sessionExpired).toBe(true)
      expect(getSession()).toBeNull()
      expect(vi.getTimerCount()).toBe(0)
    })

    it.each([
      ['a network error', new ApiError('offline', { kind: ApiErrorKind.NETWORK })],
      ['an unavailable service', new ApiError('down', {
        status: 502, kind: ApiErrorKind.SERVICE_UNAVAILABLE })],
    ])('keeps the session and reports a recoverable error on %s', async (_label, failure) => {
      saveSession('stored-token', 900)
      authService.getCurrentUser.mockRejectedValueOnce(failure).mockResolvedValueOnce(USER)

      const { result } = renderAuth()
      await flush()

      expect(result.current.status).toBe('loading')
      expect(result.current.error).toBe(failure)
      expect(result.current.user).toBeNull()
      expect(getAccessToken()).toBe('stored-token')

      await act(() => result.current.refreshUser())

      expect(result.current.status).toBe('authenticated')
      expect(result.current.user).toEqual(USER)
      expect(result.current.error).toBeNull()
      expect(vi.getTimerCount()).toBe(1)
    })

    it('still expires a session that could not be verified', async () => {
      saveSession('stored-token', 900)
      authService.getCurrentUser.mockRejectedValue(
        new ApiError('offline', { kind: ApiErrorKind.NETWORK }))

      const { result } = renderAuth()
      await flush()
      act(() => {
        vi.advanceTimersByTime(FIFTEEN_MINUTES)
      })

      expect(result.current.status).toBe('anonymous')
      expect(result.current.sessionExpired).toBe(true)
      expect(result.current.error).toBeNull()
      expect(getSession()).toBeNull()
    })

    it('ignores a restoration response that arrives after logout', async () => {
      saveSession('stored-token', 900)
      let resolveProfile
      authService.getCurrentUser.mockReturnValue(new Promise((resolve) => {
        resolveProfile = resolve
      }))

      const { result } = renderAuth()
      act(() => result.current.logout())
      resolveProfile(USER)
      await flush()

      expect(result.current.status).toBe('anonymous')
      expect(result.current.user).toBeNull()
      expect(vi.getTimerCount()).toBe(0)
    })

    it('initializes once under StrictMode', async () => {
      saveSession('stored-token', 900)
      authService.getCurrentUser.mockResolvedValue(USER)

      const { result } = renderAuth({ strict: true })
      await flush()

      expect(authService.getCurrentUser).toHaveBeenCalledTimes(1)
      expect(result.current.status).toBe('authenticated')
      expect(vi.getTimerCount()).toBe(1)

      // The expiry timer must survive StrictMode's simulated unmount.
      act(() => {
        vi.advanceTimersByTime(FIFTEEN_MINUTES)
      })
      expect(result.current.status).toBe('anonymous')
      expect(result.current.sessionExpired).toBe(true)
    })
  })

  describe('login', () => {
    it('authenticates the user and schedules expiry', async () => {
      mockSuccessfulLogin()
      const { result } = renderAuth()
      await flush()

      let user
      await act(async () => {
        user = await result.current.login('customer@example.com', 'password123')
      })

      expect(authService.login)
        .toHaveBeenCalledWith('customer@example.com', 'password123', { remember: false })
      expect(user).toEqual(USER)
      expect(result.current.status).toBe('authenticated')
      expect(result.current.user).toEqual(USER)
      expect(vi.getTimerCount()).toBe(1)
    })

    it('rethrows a 401 as a login error and stays anonymous', async () => {
      const failure = new ApiError('Invalid email or password', { status: 401 })
      authService.login.mockRejectedValue(failure)
      const { result } = renderAuth()
      await flush()

      await act(async () => {
        await expect(result.current.login('customer@example.com', 'wrong')).rejects.toBe(failure)
      })

      expect(result.current.status).toBe('anonymous')
      expect(result.current.error).toBeNull()
      expect(result.current.sessionExpired).toBe(false)
    })

    it('leaves an existing session untouched when a new login fails', async () => {
      saveSession('stored-token', 900)
      authService.getCurrentUser.mockResolvedValue(USER)
      authService.login.mockRejectedValue(
        new ApiError('Invalid email or password', { status: 401 }))
      const { result } = renderAuth()
      await flush()

      await act(async () => {
        await result.current.login('other@example.com', 'wrong').catch(() => {})
      })

      expect(result.current.status).toBe('authenticated')
      expect(result.current.user).toEqual(USER)
      expect(getAccessToken()).toBe('stored-token')
      expect(vi.getTimerCount()).toBe(1)
    })

    it('clears a previous session-expired notice', async () => {
      saveSession('old-token', 60, Date.now() - 120_000)
      mockSuccessfulLogin()
      const { result } = renderAuth()
      await flush()
      expect(result.current.sessionExpired).toBe(true)

      await act(() => result.current.login('customer@example.com', 'password123'))

      expect(result.current.sessionExpired).toBe(false)
    })
  })

  describe('completeGoogleLogin', () => {
    const VERIFIER = 'v'.repeat(43)

    it('authenticates the user from the exchanged code and schedules expiry', async () => {
      authService.exchangeGoogleCode.mockImplementation(async () => {
        const expiresAt = saveSession('google-token', 900)
        return { user: USER, expiresAt }
      })
      const { result } = renderAuth()
      await flush()

      let user
      await act(async () => {
        user = await result.current.completeGoogleLogin('one-time-code', VERIFIER)
      })

      expect(authService.exchangeGoogleCode).toHaveBeenCalledWith('one-time-code', VERIFIER)
      expect(user).toEqual(USER)
      expect(result.current.status).toBe('authenticated')
      expect(getAccessToken()).toBe('google-token')
      expect(vi.getTimerCount()).toBe(1)

      act(() => {
        vi.advanceTimersByTime(FIFTEEN_MINUTES)
      })
      expect(result.current.status).toBe('anonymous')
      expect(result.current.sessionExpired).toBe(true)
    })

    it('rethrows a rejected code and leaves an existing session untouched', async () => {
      saveSession('stored-token', 900)
      authService.getCurrentUser.mockResolvedValue(USER)
      const failure = new ApiError('Invalid or expired sign-in code', { status: 401 })
      authService.exchangeGoogleCode.mockRejectedValue(failure)
      const { result } = renderAuth()
      await flush()

      await act(async () => {
        await expect(result.current.completeGoogleLogin('used-code', VERIFIER)).rejects.toBe(failure)
      })

      expect(result.current.status).toBe('authenticated')
      expect(getAccessToken()).toBe('stored-token')
    })
  })

  describe('register', () => {
    it('delegates to the auth service without signing in', async () => {
      authService.register.mockResolvedValue(USER)
      const { result } = renderAuth()
      await flush()

      let created
      await act(async () => {
        created = await result.current.register({
          name: 'Test Customer', email: USER.email, password: 'password123',
        })
      })

      expect(authService.register).toHaveBeenCalledWith({
        name: 'Test Customer', email: USER.email, password: 'password123',
      })
      expect(created).toEqual(USER)
      expect(result.current.status).toBe('anonymous')
    })
  })

  describe('logout', () => {
    it('clears the session, state and timer and can be called repeatedly', async () => {
      mockSuccessfulLogin()
      const { result } = renderAuth()
      await flush()
      await act(() => result.current.login('customer@example.com', 'password123'))

      act(() => result.current.logout())
      act(() => result.current.logout())

      expect(result.current.status).toBe('anonymous')
      expect(result.current.user).toBeNull()
      expect(result.current.sessionExpired).toBe(false)
      expect(getSession()).toBeNull()
      expect(vi.getTimerCount()).toBe(0)
    })

    it('handles repeated login and logout cycles with a single timer', async () => {
      mockSuccessfulLogin()
      const { result } = renderAuth()
      await flush()

      await act(() => result.current.login('first@example.com', 'password123'))
      await act(() => result.current.login('second@example.com', 'password123'))
      expect(result.current.user.email).toBe('second@example.com')
      expect(getAccessToken()).toBe('token-for-second@example.com')
      expect(vi.getTimerCount()).toBe(1)

      act(() => result.current.logout())
      await act(() => result.current.login('third@example.com', 'password123'))
      act(() => result.current.logout())
      act(() => result.current.logout())

      expect(result.current.status).toBe('anonymous')
      expect(vi.getTimerCount()).toBe(0)
    })
  })

  describe('expiry', () => {
    it('logs out automatically when the session expires', async () => {
      mockSuccessfulLogin()
      const { result } = renderAuth()
      await flush()
      await act(() => result.current.login('customer@example.com', 'password123'))

      act(() => {
        vi.advanceTimersByTime(FIFTEEN_MINUTES - 1)
      })
      expect(result.current.status).toBe('authenticated')

      act(() => {
        vi.advanceTimersByTime(1)
      })
      expect(result.current.status).toBe('anonymous')
      expect(result.current.sessionExpired).toBe(true)
      expect(getSession()).toBeNull()
      expect(vi.getTimerCount()).toBe(0)

      act(() => result.current.dismissSessionExpired())
      expect(result.current.sessionExpired).toBe(false)
    })

    it('expires the session when a suspended tab becomes visible again', async () => {
      mockSuccessfulLogin()
      const { result } = renderAuth()
      await flush()
      await act(() => result.current.login('customer@example.com', 'password123'))

      setVisibility('visible')
      expect(result.current.status).toBe('authenticated')

      // Clock moves on while the tab's timers are paused.
      vi.setSystemTime(Date.now() + FIFTEEN_MINUTES + 1_000)
      setVisibility('hidden')
      expect(result.current.status).toBe('authenticated')

      setVisibility('visible')
      expect(result.current.status).toBe('anonymous')
      expect(result.current.sessionExpired).toBe(true)
      expect(getSession()).toBeNull()
      expect(vi.getTimerCount()).toBe(0)
    })

    it('handles expiry delays beyond the setTimeout limit', async () => {
      mockSuccessfulLogin(40 * 24 * 3600)
      const { result } = renderAuth()
      await flush()
      await act(() => result.current.login('customer@example.com', 'password123'))

      act(() => {
        vi.advanceTimersByTime(30 * DAY)
      })
      expect(result.current.status).toBe('authenticated')
      expect(vi.getTimerCount()).toBe(1)

      act(() => {
        vi.advanceTimersByTime(10 * DAY)
      })
      expect(result.current.status).toBe('anonymous')
    })
  })

  describe('cleanup', () => {
    it('removes the timer and visibility listener on unmount', async () => {
      mockSuccessfulLogin()
      const removeListener = vi.spyOn(document, 'removeEventListener')
      const { result, unmount } = renderAuth()
      await flush()
      await act(() => result.current.login('customer@example.com', 'password123'))
      expect(vi.getTimerCount()).toBe(1)

      unmount()

      expect(vi.getTimerCount()).toBe(0)
      expect(removeListener).toHaveBeenCalledWith('visibilitychange', expect.any(Function))
      removeListener.mockRestore()
    })
  })

  it('requires an AuthProvider', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})

    expect(() => renderHook(() => useAuth()))
      .toThrow('useAuth must be used within an AuthProvider')
    consoleError.mockRestore()
  })

  describe('a token rejected by another service', () => {
    afterEach(() => {
      vi.unstubAllGlobals()
    })

    it('ends the session with an expiry notice', async () => {
      saveSession('stored-token', 900)
      authService.getCurrentUser.mockResolvedValue(USER)
      const { result } = renderAuth()
      await flush()
      expect(result.current.status).toBe('authenticated')

      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 })))
      await act(async () => {
        await request('/api/parcels', { auth: true }).catch(() => {})
      })

      expect(result.current.status).toBe('anonymous')
      expect(result.current.sessionExpired).toBe(true)
      expect(getSession()).toBeNull()
    })
  })

  describe('remember me', () => {
    it('passes the choice to the auth service', async () => {
      mockSuccessfulLogin()
      const { result } = renderAuth()
      await flush()

      await act(async () => {
        await result.current.login('customer@example.com', 'password123', { remember: true })
      })

      expect(authService.login)
        .toHaveBeenCalledWith('customer@example.com', 'password123', { remember: true })
    })
  })
})
