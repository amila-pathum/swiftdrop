import { useState } from 'react'
import { Link } from 'react-router-dom'
import Alert from '../../components/Alert'
import AuthCard from '../../components/AuthCard'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import { useForm } from '../../hooks/useForm'
import { requestPasswordReset } from '../../services/authService'
import { describeError } from '../../services/errorMessages'
import { validateForgotPassword } from './authForm'

const NOT_AVAILABLE_MESSAGE = 'Password reset by email is not available yet. Please contact a '
  + 'SwiftDrop administrator to reset your password.'

export default function ForgotPasswordPage() {
  const [sentTo, setSentTo] = useState(null)
  const form = useForm({
    initialValues: { email: '' },
    validate: validateForgotPassword,
    idPrefix: 'forgot',
  })

  const onSubmit = form.handleSubmit(async ({ email }) => {
    try {
      await requestPasswordReset(email.trim())
      setSentTo(email.trim())
    } catch (error) {
      form.setFormError(describeError(error, { notImplemented: NOT_AVAILABLE_MESSAGE }))
    }
  })

  return (
    <AuthCard
      title="Reset your password"
      subtitle="Enter your account email and we will send you a link to choose a new password."
      footer={<p className="card-footer-text"><Link to="/login">Back to sign in</Link></p>}
    >
      {sentTo ? (
        <Alert variant="success" title="Check your email">
          If an account exists for {sentTo}, a password reset link is on its way.
        </Alert>
      ) : (
        <>
          {form.formError && <Alert variant="error">{form.formError}</Alert>}
          <form className="form" onSubmit={onSubmit} noValidate>
            <FormField {...form.field('email')} label="Email" type="email" autoComplete="email" />
            <Button type="submit" fullWidth loading={form.submitting} loadingText="Sending…">
              Send reset link
            </Button>
          </form>
        </>
      )}
    </AuthCard>
  )
}
