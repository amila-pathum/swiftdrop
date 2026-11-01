import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import * as authService from '../services/authService'
import { clearSession, getSession, isSessionExpired } from '../services/tokenStorage'

const AuthStatus = Object.freeze({
  LOADING: 'loading',
  AUTHENTICATED: 'authenticated',
  ANONYMOUS: 'anonymous',
})

// setTimeout overflows above this delay (~24.8 days) and would fire immediately.
const MAX_TIMER_DELAY_MS = 2_147_483_647

const AuthContext = createContext(null)

const anonymousState = (sessionExpired = false) => ({
  user: null,
  status: AuthStatus.ANONYMOUS,
  error: null,
  sessionExpired,
})

/**
 * Provides authentication state to the application.
 *
 * - status `loading` + error: the stored session could not be verified because the
 *   server was unreachable. The session is kept; call refreshUser() to retry.
 * - sessionExpired: the previous session ended (expired or rejected by the server).
 */
export function AuthProvider({ children }) {
  const [state, setState] = useState({
    user: null,
    status: AuthStatus.LOADING,
    error: null,
    sessionExpired: false,
  })

  const expiryTimerRef = useRef(null)
  const mountedRef = useRef(false)
  const startedRef = useRef(false)
  // Incremented whenever the session changes, so in-flight profile requests
  // from an earlier session cannot overwrite newer state.
  const generationRef = useRef(0)

  const cancelExpiryTimer = useCallback(() => {
    if (expiryTimerRef.current !== null) {
      clearTimeout(expiryTimerRef.current)
      expiryTimerRef.current = null
    }
  }, [])

  const endSession = useCallback((sessionExpired) => {
    generationRef.current += 1
    cancelExpiryTimer()
    clearSession()
    setState(anonymousState(sessionExpired))
  }, [cancelExpiryTimer])

  const scheduleExpiry = useCallback((expiresAt) => {
    cancelExpiryTimer()
    const delay = Math.min(Math.max(expiresAt - Date.now(), 0), MAX_TIMER_DELAY_MS)
    expiryTimerRef.current = setTimeout(() => {
      expiryTimerRef.current = null
      const session = getSession()
      if (session === null || isSessionExpired()) {
        endSession(true)
      } else {
        scheduleExpiry(session.expiresAt)
      }
    }, delay)
  }, [cancelExpiryTimer, endSession])

  // Loads the profile for the stored session. 401 ends the session; any other
  // failure keeps it and reports a recoverable error.
  const loadUser = useCallback(async () => {
    const session = getSession()
    if (session === null) {
      endSession(false)
      return null
    }
    if (isSessionExpired()) {
      endSession(true)
      return null
    }

    const generation = generationRef.current
    setState((current) => (current.error === null ? current : { ...current, error: null }))

    // The expiry timer is scheduled only after the response: under StrictMode a
    // timer created synchronously here would be cleared by the simulated unmount.
    try {
      const user = await authService.getCurrentUser()
      if (!mountedRef.current || generation !== generationRef.current) {
        return null
      }
      scheduleExpiry(session.expiresAt)
      setState({ user, status: AuthStatus.AUTHENTICATED, error: null, sessionExpired: false })
      return user
    } catch (error) {
      if (!mountedRef.current || generation !== generationRef.current) {
        return null
      }
      if (error?.status === 401) {
        endSession(true)
      } else {
        scheduleExpiry(session.expiresAt)
        setState((current) => ({ ...current, error }))
      }
      return null
    }
  }, [endSession, scheduleExpiry])

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      cancelExpiryTimer()
    }
  }, [cancelExpiryTimer])

  // Runs once, including under StrictMode's double effect invocation.
  useEffect(() => {
    if (startedRef.current) {
      return
    }
    startedRef.current = true
    loadUser()
  }, [loadUser])

  // Timers can be paused in background tabs, so recheck when the tab is shown again.
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible'
          && expiryTimerRef.current !== null
          && isSessionExpired()) {
        endSession(true)
      }
    }
    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange)
  }, [endSession])

  // Errors are rethrown for the caller (e.g. a login form). A failed login leaves
  // the current session and state untouched.
  const login = useCallback(async (email, password) => {
    const { user, expiresAt } = await authService.login(email, password)
    generationRef.current += 1
    scheduleExpiry(expiresAt)
    setState({ user, status: AuthStatus.AUTHENTICATED, error: null, sessionExpired: false })
    return user
  }, [scheduleExpiry])

  // Same as login, for the one-time code from the Google callback.
  const completeGoogleLogin = useCallback(async (code, verifier) => {
    const { user, expiresAt } = await authService.exchangeGoogleCode(code, verifier)
    generationRef.current += 1
    scheduleExpiry(expiresAt)
    setState({ user, status: AuthStatus.AUTHENTICATED, error: null, sessionExpired: false })
    return user
  }, [scheduleExpiry])

  // Registration does not sign the user in; the backend returns no token.
  const register = useCallback(
    (name, email, password) => authService.register(name, email, password),
    [],
  )

  const logout = useCallback(() => endSession(false), [endSession])

  const dismissSessionExpired = useCallback(() => {
    setState((current) => (current.sessionExpired
      ? { ...current, sessionExpired: false }
      : current))
  }, [])

  const value = useMemo(() => ({
    ...state,
    login,
    completeGoogleLogin,
    register,
    logout,
    refreshUser: loadUser,
    dismissSessionExpired,
  }), [state, login, completeGoogleLogin, register, logout, loadUser, dismissSessionExpired])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- hook belongs with its provider
export function useAuth() {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
