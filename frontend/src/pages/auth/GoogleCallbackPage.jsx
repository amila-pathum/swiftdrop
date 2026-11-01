import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Button from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { safeReturnPath } from '../../utils/postLoginPath'
import { useAuth } from '../../context/AuthContext'
import { ApiError } from '../../services/apiClient'
import { takePendingGoogleSignIn } from '../../services/googleAuth'
import { isServiceUnavailable, SERVICE_UNAVAILABLE_MESSAGE } from './authForm'

const RETRY = 'Please try again, or sign in with your email and password.'

// Reasons the backend reports in the callback fragment (#error=...).
const REASON_MESSAGES = {
  account_exists: 'An account with this email already exists. Sign in with your email and '
    + 'password instead.',
  email_unverified: 'Your Google account email is not verified. Verify it with Google, or '
    + 'sign in with your email and password.',
  account_inactive: 'This account is not permitted to sign in.',
  google_unavailable: 'Google sign-in is not available right now. Sign in with your email '
    + 'and password.',
}
const FAILED_MESSAGE = `Google sign-in could not be completed. ${RETRY}`
const EXPIRED_MESSAGE = `This Google sign-in has expired or was already used. ${RETRY}`
const NOT_STARTED_MESSAGE = `This Google sign-in was not started in this browser tab, or it took too long. ${RETRY}`

function exchangeErrorMessage(error) {
  if (isServiceUnavailable(error)) return SERVICE_UNAVAILABLE_MESSAGE
  if (!(error instanceof ApiError)) {
    return 'Unable to sign in. Check that your browser allows this site to store data.'
  }
  // 400 (malformed) and 401 (unknown, used, expired or wrong verifier) look alike.
  if (error.status === 400 || error.status === 401) return EXPIRED_MESSAGE
  if (error.status === 403) return REASON_MESSAGES.account_inactive
  return FAILED_MESSAGE
}

/**
 * Where the backend sends the browser after Google: /auth/google/callback#code=...
 * or #error=<reason>. The one-time code is removed from the address bar and
 * history straight away and exchanged exactly once, even under StrictMode.
 */
export default function GoogleCallbackPage() {
  const { completeGoogleLogin } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [errorMessage, setErrorMessage] = useState(null)
  const startedRef = useRef(false)
  const mountedRef = useRef(false)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  useEffect(() => {
    // StrictMode runs this effect twice; the code may only be used once.
    if (startedRef.current) return
    startedRef.current = true

    const fragment = new URLSearchParams(location.hash.replace(/^#/, ''))
    const code = fragment.get('code')
    const reason = fragment.get('error')
    navigate(location.pathname, { replace: true })
    // Always taken, so the verifier is removed whatever happens next.
    const pending = takePendingGoogleSignIn()

    async function complete() {
      if (reason !== null) {
        return REASON_MESSAGES[reason] ?? FAILED_MESSAGE
      }
      if (!code) {
        return FAILED_MESSAGE
      }
      if (pending === null) {
        return NOT_STARTED_MESSAGE
      }
      try {
        await completeGoogleLogin(code, pending.verifier)
      } catch (error) {
        return exchangeErrorMessage(error)
      }
      if (mountedRef.current) {
        // Role-restricted pages still redirect elsewhere if the role does not allow them.
        navigate(safeReturnPath(pending.returnTo), { replace: true })
      }
      return null
    }

    complete().then((message) => {
      if (message !== null && mountedRef.current) {
        setErrorMessage(message)
      }
    })
  }, [completeGoogleLogin, location.hash, location.pathname, navigate])

  if (errorMessage) {
    return (
      <section className="card status-card" role="alert">
        <h1 className="card-title">Google sign-in failed</h1>
        <p className="card-subtitle">{errorMessage}</p>
        <Button fullWidth onClick={() => navigate('/login', { replace: true })}>
          Back to sign in
        </Button>
      </section>
    )
  }

  return (
    <div className="card status-card status-card-loading">
      <LoadingSpinner />
      <p role="status">Signing you in with Google…</p>
    </div>
  )
}
