import { useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import GoogleSignInButton from '../../components/GoogleSignInButton'
import { useAuth } from '../../context/AuthContext'
import { ApiError } from '../../services/apiClient'
import { isServiceUnavailable, SERVICE_UNAVAILABLE_MESSAGE, validateLogin } from './authForm'

function loginErrorMessage(error) {
  if (isServiceUnavailable(error)) return SERVICE_UNAVAILABLE_MESSAGE
  if (!(error instanceof ApiError)) {
    return 'Unable to sign in. Check that your browser allows this site to store data.'
  }
  if (error.status === 401) return 'Invalid email or password.'
  if (error.status === 403) return 'This account is not permitted to sign in.'
  if (error.status === 400) return 'Check your email and password and try again.'
  return 'Something went wrong. Please try again.'
}

export default function LoginPage() {
  const { login, sessionExpired, dismissSessionExpired } = useAuth()
  const location = useLocation()
  const registeredEmail = location.state?.registeredEmail

  const [values, setValues] = useState({ email: registeredEmail ?? '', password: '' })
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [attempted, setAttempted] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  // Guards against a second submit before the disabled state has rendered.
  const submittingRef = useRef(false)

  function handleChange(event) {
    const next = { ...values, [event.target.name]: event.target.value }
    setValues(next)
    if (attempted) {
      setErrors(validateLogin(next))
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submittingRef.current) return

    setAttempted(true)
    const found = validateLogin(values)
    setErrors(found)
    const firstInvalid = Object.keys(found)[0]
    if (firstInvalid) {
      document.getElementById(`login-${firstInvalid}`)?.focus()
      return
    }

    submittingRef.current = true
    setSubmitting(true)
    setFormError(null)
    try {
      await login(values.email.trim(), values.password)
      // PublicOnlyRoute now redirects to the requested page or the dashboard.
    } catch (error) {
      setFormError(loginErrorMessage(error))
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <div className="card auth-card">
      <header className="card-header">
        <h1 className="card-title">Sign in to SwiftDrop</h1>
        <p className="card-subtitle">Book, manage and track deliveries across Sri Lanka.</p>
      </header>

      {sessionExpired && (
        <div className="alert alert-warning" role="alert">
          <p>Your session has expired. Please sign in again.</p>
          <button type="button" className="alert-dismiss" onClick={dismissSessionExpired}>
            Dismiss
          </button>
        </div>
      )}
      {registeredEmail && !formError && (
        <div className="alert alert-success" role="status">
          <p>Your account has been created. Sign in to continue.</p>
        </div>
      )}
      {formError && (
        <div className="alert alert-error" role="alert">
          <p>{formError}</p>
        </div>
      )}

      <form className="form" onSubmit={handleSubmit} noValidate>
        <FormField
          id="login-email"
          name="email"
          label="Email"
          type="email"
          autoComplete="email"
          value={values.email}
          onChange={handleChange}
          error={errors.email}
        />
        <FormField
          id="login-password"
          name="password"
          label="Password"
          type="password"
          revealable
          autoComplete="current-password"
          value={values.password}
          onChange={handleChange}
          error={errors.password}
        />
        <Button type="submit" fullWidth loading={submitting} loadingText="Signing in…">
          Sign in
        </Button>
      </form>

      <div className="divider" role="separator"><span>or</span></div>

      <GoogleSignInButton />

      <p className="card-footer-text">
        New to SwiftDrop? <Link to="/register">Create account</Link>
      </p>
    </div>
  )
}
