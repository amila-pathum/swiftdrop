import { Link, useLocation } from 'react-router-dom'
import Alert from '../../components/Alert'
import AuthCard from '../../components/AuthCard'
import Button from '../../components/Button'
import Checkbox from '../../components/Checkbox'
import FormField from '../../components/FormField'
import GoogleSignInButton from '../../components/GoogleSignInButton'
import { useAuth } from '../../context/AuthContext'
import { useForm } from '../../hooks/useForm'
import { isGoogleLoginEnabled } from '../../services/config'
import {
  ACCOUNT_NOT_ACTIVE_MESSAGE,
  describeError,
  STORAGE_ERROR_MESSAGE,
} from '../../services/errorMessages'
import { validateLogin } from './authForm'

function loginErrorMessage(error) {
  return describeError(error, {
    nonApi: STORAGE_ERROR_MESSAGE,
    byStatus: {
      401: 'Invalid email or password.',
      403: ACCOUNT_NOT_ACTIVE_MESSAGE,
      400: 'Check your email and password and try again.',
    },
  })
}

// One sign-in page for every role. After sign-in, PublicOnlyRoute sends the
// user to the page they asked for, their dashboard, or their application status.
export default function LoginPage() {
  const { login, sessionExpired, dismissSessionExpired } = useAuth()
  const location = useLocation()
  const registeredEmail = location.state?.registeredEmail

  const form = useForm({
    initialValues: { email: registeredEmail ?? '', password: '', remember: false },
    validate: validateLogin,
    idPrefix: 'login',
  })

  const onSubmit = form.handleSubmit(async ({ email, password, remember }) => {
    try {
      await login(email.trim(), password, { remember })
      // PublicOnlyRoute now redirects to the requested page or the dashboard.
    } catch (error) {
      form.setFormError(loginErrorMessage(error))
    }
  })

  return (
    <AuthCard
      title="Sign in to SwiftDrop"
      subtitle="Book, manage and track deliveries across Sri Lanka."
      footer={(
        <>
          <p className="card-footer-text">
            New to SwiftDrop? <Link to="/register">Create account</Link>
          </p>
          <p className="card-footer-text card-footer-muted">
            Business or courier? <Link to="/register/merchant">Apply as a merchant</Link>
            {' · '}
            <Link to="/register/courier">Apply as a courier</Link>
          </p>
        </>
      )}
    >
      {sessionExpired && (
        <Alert variant="warning" onDismiss={dismissSessionExpired}>
          Your session has expired. Please sign in again.
        </Alert>
      )}
      {registeredEmail && !form.formError && (
        <Alert variant="success">Your account has been created. Sign in to continue.</Alert>
      )}
      {form.formError && <Alert variant="error">{form.formError}</Alert>}

      <form className="form" onSubmit={onSubmit} noValidate>
        <FormField {...form.field('email')} label="Email" type="email" autoComplete="email" />
        <FormField
          {...form.field('password')}
          label="Password"
          type="password"
          revealable
          autoComplete="current-password"
        />
        <div className="form-row-between">
          <Checkbox
            id="login-remember"
            name="remember"
            label="Remember me"
            checked={form.values.remember}
            onChange={form.handleChange}
          />
          <Link to="/forgot-password" className="text-small">Forgot password?</Link>
        </div>
        <Button type="submit" fullWidth loading={form.submitting} loadingText="Signing in…">
          Sign in
        </Button>
      </form>

      {isGoogleLoginEnabled() && (
        <>
          <div className="divider" role="separator"><span>or</span></div>
          <GoogleSignInButton />
        </>
      )}
    </AuthCard>
  )
}
