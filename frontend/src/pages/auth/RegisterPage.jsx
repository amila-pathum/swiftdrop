import { Link, useNavigate } from 'react-router-dom'
import Alert from '../../components/Alert'
import AuthCard from '../../components/AuthCard'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import GoogleSignInButton from '../../components/GoogleSignInButton'
import RegistrationTypeTabs from '../../components/RegistrationTypeTabs'
import { useAuth } from '../../context/AuthContext'
import { useForm } from '../../hooks/useForm'
import { isGoogleLoginEnabled } from '../../services/config'
import { describeError } from '../../services/errorMessages'
import { validateRegistration } from './authForm'

const EMPTY_FORM = {
  name: '', email: '', phone: '', address: '', password: '', confirmPassword: '',
}

// Customer sign-up. Public registration always creates a CUSTOMER account; the
// backend ignores any role. It returns no token, so the user signs in next.
export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const form = useForm({
    initialValues: EMPTY_FORM,
    validate: validateRegistration,
    idPrefix: 'register',
  })

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      // confirmPassword is checked here only.
      const user = await register({
        name: values.name.trim(),
        email: values.email.trim(),
        phone: values.phone.trim(),
        address: values.address.trim(),
        password: values.password,
      })
      navigate('/login', { state: { registeredEmail: user?.email ?? values.email.trim() } })
    } catch (error) {
      if (error?.status === 409) {
        form.setFieldError('email', 'This email is already registered.')
      } else {
        form.setFormError(describeError(error, {
          byStatus: { 400: 'Some details are invalid. Check the form and try again.' },
        }))
      }
    }
  })

  return (
    <AuthCard
      title="Create a customer account"
      subtitle="Send, receive and track parcels. Sending for a business? Apply as a merchant instead."
      footer={(
        <p className="card-footer-text">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      )}
    >
      <RegistrationTypeTabs />
      {form.formError && <Alert variant="error">{form.formError}</Alert>}

      <form className="form" onSubmit={onSubmit} noValidate>
        <FormField {...form.field('name')} label="Full name" autoComplete="name" />
        <FormField {...form.field('email')} label="Email" type="email" autoComplete="email" />
        <FormField {...form.field('phone')} label="Phone number" type="tel" autoComplete="tel"
          placeholder="077 123 4567" />
        <FormField {...form.field('address')} label="Address" autoComplete="street-address" />
        <FormField
          {...form.field('password')}
          label="Password"
          type="password"
          revealable
          autoComplete="new-password"
          hint="Use 8 to 72 characters."
        />
        <FormField
          {...form.field('confirmPassword')}
          label="Confirm password"
          type="password"
          revealable
          autoComplete="new-password"
        />
        <p className="form-note">
          Dispatcher and admin accounts are created by SwiftDrop administrators and cannot be
          selected here. Merchants and couriers apply using the tabs above.
        </p>
        <Button type="submit" fullWidth loading={form.submitting} loadingText="Creating account…">
          Create account
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
