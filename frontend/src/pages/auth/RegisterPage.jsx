import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import GoogleSignInButton from '../../components/GoogleSignInButton'
import { useAuth } from '../../context/AuthContext'
import { isServiceUnavailable, SERVICE_UNAVAILABLE_MESSAGE, validateRegistration } from './authForm'

const EMPTY_FORM = { name: '', email: '', password: '', confirmPassword: '' }

function registrationErrorMessage(error) {
  if (isServiceUnavailable(error)) return SERVICE_UNAVAILABLE_MESSAGE
  if (error?.status === 400) return 'Some details are invalid. Check the form and try again.'
  return 'Something went wrong. Please try again.'
}

// Public registration always creates a CUSTOMER account; the backend ignores any role.
export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [values, setValues] = useState(EMPTY_FORM)
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
      setErrors(validateRegistration(next))
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (submittingRef.current) return

    setAttempted(true)
    const found = validateRegistration(values)
    setErrors(found)
    const firstInvalid = Object.keys(found)[0]
    if (firstInvalid) {
      document.getElementById(`register-${firstInvalid}`)?.focus()
      return
    }

    submittingRef.current = true
    setSubmitting(true)
    setFormError(null)
    try {
      // confirmPassword is checked here only; the API accepts name, email and password.
      const user = await register(values.name.trim(), values.email.trim(), values.password)
      navigate('/login', { state: { registeredEmail: user?.email ?? values.email.trim() } })
    } catch (error) {
      if (error?.status === 409) {
        setErrors((current) => ({ ...current, email: 'This email is already registered.' }))
        document.getElementById('register-email')?.focus()
      } else {
        setFormError(registrationErrorMessage(error))
      }
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  return (
    <div className="card auth-card">
      <header className="card-header">
        <h1 className="card-title">Create a customer account</h1>
        <p className="card-subtitle">
          Sending for a business? Merchant sign-up is coming soon.
        </p>
      </header>

      {formError && (
        <div className="alert alert-error" role="alert">
          <p>{formError}</p>
        </div>
      )}

      <form className="form" onSubmit={handleSubmit} noValidate>
        <FormField
          id="register-name"
          name="name"
          label="Full name"
          autoComplete="name"
          value={values.name}
          onChange={handleChange}
          error={errors.name}
        />
        <FormField
          id="register-email"
          name="email"
          label="Email"
          type="email"
          autoComplete="email"
          value={values.email}
          onChange={handleChange}
          error={errors.email}
        />
        <FormField
          id="register-password"
          name="password"
          label="Password"
          type="password"
          revealable
          autoComplete="new-password"
          hint="Use 8 to 72 characters."
          value={values.password}
          onChange={handleChange}
          error={errors.password}
        />
        <FormField
          id="register-confirmPassword"
          name="confirmPassword"
          label="Confirm password"
          type="password"
          revealable
          autoComplete="new-password"
          value={values.confirmPassword}
          onChange={handleChange}
          error={errors.confirmPassword}
        />
        <p className="form-note">
          Courier, dispatcher and admin accounts are created by SwiftDrop administrators and
          cannot be selected here.
        </p>
        <Button type="submit" fullWidth loading={submitting} loadingText="Creating account…">
          Create account
        </Button>
      </form>

      <div className="divider" role="separator"><span>or</span></div>

      <GoogleSignInButton />

      <p className="card-footer-text">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </div>
  )
}
