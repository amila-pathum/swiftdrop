import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import Alert from '../../components/Alert'
import AuthCard from '../../components/AuthCard'
import Button, { ButtonLink } from '../../components/Button'
import FormField from '../../components/FormField'
import { useForm } from '../../hooks/useForm'
import { confirmPasswordReset } from '../../services/authService'
import { describeError } from '../../services/errorMessages'
import { validateNewPasswordForm } from './authForm'

const INVALID_LINK_MESSAGE = 'This password reset link is invalid, has expired or was already '
  + 'used. Ask a SwiftDrop administrator for a new one.'

/**
 * /reset-password#token=... : chooses a new password with a one-time token.
 * The token travels in the URL fragment (never sent to a server) and is removed
 * from the address bar and history as soon as the page has read it.
 */
export default function ResetPasswordPage() {
  const location = useLocation()
  const navigate = useNavigate()
  // Read once: the fragment is cleared right after.
  const [token] = useState(
    () => new URLSearchParams(location.hash.replace(/^#/, '')).get('token') ?? '')
  const [done, setDone] = useState(false)
  const form = useForm({
    initialValues: { password: '', confirmPassword: '' },
    validate: validateNewPasswordForm,
    idPrefix: 'reset',
  })

  useEffect(() => {
    if (location.hash) {
      navigate(location.pathname, { replace: true })
    }
  }, [location.hash, location.pathname, navigate])

  const onSubmit = form.handleSubmit(async ({ password }) => {
    try {
      await confirmPasswordReset(token, password)
      setDone(true)
    } catch (error) {
      form.setFormError(describeError(error, { byStatus: { 400: INVALID_LINK_MESSAGE } }))
    }
  })

  const footer = <p className="card-footer-text"><Link to="/login">Back to sign in</Link></p>

  if (done) {
    return (
      <AuthCard title="Password changed" footer={footer}>
        <Alert variant="success">
          Your password has been changed and you have been signed out everywhere.
        </Alert>
        <ButtonLink to="/login" fullWidth>Sign in with your new password</ButtonLink>
      </AuthCard>
    )
  }

  if (token === '') {
    return (
      <AuthCard title="Choose a new password" footer={footer}>
        <Alert variant="error">{INVALID_LINK_MESSAGE}</Alert>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title="Choose a new password"
      subtitle="This link can be used once. Afterwards you will be signed out on every device."
      footer={footer}
    >
      {form.formError && <Alert variant="error">{form.formError}</Alert>}
      <form className="form" onSubmit={onSubmit} noValidate>
        <FormField {...form.field('password')} label="New password" type="password" revealable
          autoComplete="new-password" hint="Use 8 to 72 characters." />
        <FormField {...form.field('confirmPassword')} label="Confirm new password" type="password"
          revealable autoComplete="new-password" />
        <Button type="submit" fullWidth loading={form.submitting} loadingText="Saving…">
          Change password
        </Button>
      </form>
    </AuthCard>
  )
}
