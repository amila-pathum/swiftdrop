import { useState } from 'react'
import { useLocation } from 'react-router-dom'
import Button from './Button'
import { postLoginPath } from '../utils/postLoginPath'
import { isGoogleLoginEnabled } from '../services/config'
import { startGoogleSignIn } from '../services/googleAuth'

function GoogleMark() {
  return (
    <svg className="google-mark" viewBox="0 0 18 18" aria-hidden="true" focusable="false">
      <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62Z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18Z" />
      <path fill="#FBBC05" d="M3.97 10.72A5.4 5.4 0 0 1 3.68 9c0-.6.1-1.18.29-1.72V4.95H.96A9 9 0 0 0 0 9c0 1.45.35 2.83.96 4.05l3.01-2.33Z" />
      <path fill="#EA4335" d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58Z" />
    </svg>
  )
}

/**
 * "Continue with Google", shared by the sign-in and registration pages. Renders
 * nothing unless VITE_GOOGLE_LOGIN_ENABLED is "true". The page the user was
 * originally heading to is kept for after the sign-in.
 */
export default function GoogleSignInButton() {
  const location = useLocation()
  const [redirecting, setRedirecting] = useState(false)
  const [error, setError] = useState(null)

  if (!isGoogleLoginEnabled()) {
    return null
  }

  async function handleClick() {
    setError(null)
    setRedirecting(true)
    try {
      await startGoogleSignIn(postLoginPath(location.state?.from))
      // The browser is now leaving for Google; keep the loading state.
    } catch {
      setRedirecting(false)
      setError('Google sign-in is not available in this browser. Check that it allows '
        + 'this site to store data, or sign in with your email and password.')
    }
  }

  return (
    <div className="google-sign-in">
      {error && (
        <div className="alert alert-error" role="alert">
          <p>{error}</p>
        </div>
      )}
      <Button
        variant="secondary"
        className="button-google"
        fullWidth
        loading={redirecting}
        loadingText="Redirecting to Google…"
        onClick={handleClick}
      >
        <GoogleMark />
        Continue with Google
      </Button>
    </div>
  )
}
