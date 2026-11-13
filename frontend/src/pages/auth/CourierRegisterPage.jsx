import { Link, useNavigate } from 'react-router-dom'
import Alert from '../../components/Alert'
import AuthCard from '../../components/AuthCard'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import FormSection from '../../components/FormSection'
import RegistrationTypeTabs from '../../components/RegistrationTypeTabs'
import { useForm } from '../../hooks/useForm'
import { applyAsCourier, VEHICLE_TYPES } from '../../services/applicationService'
import { describeError, isNotImplemented } from '../../services/errorMessages'
import { validateCourierRegistration } from './authForm'

const EMPTY_FORM = {
  name: '',
  email: '',
  phone: '',
  address: '',
  vehicleType: '',
  vehicleRegistrationNumber: '',
  preferredArea: '',
  password: '',
  confirmPassword: '',
}

const validate = (values) => validateCourierRegistration(values, VEHICLE_TYPES)

// Courier application. The account stays pending until an admin approves it.
export default function CourierRegisterPage() {
  const navigate = useNavigate()
  const form = useForm({ initialValues: EMPTY_FORM, validate, idPrefix: 'courier' })

  const onSubmit = form.handleSubmit(async (values) => {
    const details = {
      ...values,
      name: values.name.trim(),
      email: values.email.trim(),
      phone: values.phone.trim(),
      address: values.address.trim(),
      vehicleRegistrationNumber: values.vehicleRegistrationNumber.trim().toUpperCase(),
      preferredArea: values.preferredArea.trim(),
    }
    try {
      const result = await applyAsCourier(details)
      navigate('/application/submitted', {
        state: {
          applicationType: 'COURIER',
          email: result.user?.email ?? details.email,
          applicationSubmitted: result.applicationSubmitted,
          applicationUnavailable: isNotImplemented(result.applicationError),
        },
      })
    } catch (error) {
      // Only account creation rejects; nothing was created.
      if (error?.status === 409) {
        form.setFieldError('email', 'This email is already registered. Sign in instead.')
      } else {
        form.setFormError(describeError(error, {
          byStatus: { 400: 'Some details are invalid. Check the form and try again.' },
        }))
      }
    }
  })

  return (
    <AuthCard
      wide
      title="Apply to become a courier"
      subtitle="Deliver with SwiftDrop in your area. Applications are reviewed by a SwiftDrop administrator."
      footer={(
        <p className="card-footer-text">
          Already applied? <Link to="/login">Sign in to check your status</Link>
        </p>
      )}
    >
      <RegistrationTypeTabs />
      {form.formError && <Alert variant="error">{form.formError}</Alert>}

      <form className="form" onSubmit={onSubmit} noValidate>
        <FormSection title="Personal details">
          <FormField {...form.field('name')} label="Full name" autoComplete="name" />
          <FormField {...form.field('email')} label="Email" type="email" autoComplete="email" />
          <FormField {...form.field('phone')} label="Phone number" type="tel" autoComplete="tel"
            placeholder="077 123 4567" />
          <FormField {...form.field('address')} label="Address" autoComplete="street-address" />
        </FormSection>

        <FormSection title="Vehicle details">
          <FormField {...form.field('vehicleType')} label="Vehicle type" as="select"
            options={VEHICLE_TYPES} placeholder="Select vehicle type" />
          <FormField {...form.field('vehicleRegistrationNumber')} label="Vehicle registration number"
            placeholder="WP BAA-1234" />
          <FormField {...form.field('preferredArea')} label="Preferred delivery area"
            placeholder="e.g. Colombo 05, Nugegoda" hint="The city or area you would like to deliver in."
            className="form-field-full" />
        </FormSection>

        <FormSection title="Account details">
          <FormField {...form.field('password')} label="Password" type="password" revealable
            autoComplete="new-password" hint="Use 8 to 72 characters." />
          <FormField {...form.field('confirmPassword')} label="Confirm password" type="password"
            revealable autoComplete="new-password" />
        </FormSection>

        <Alert variant="info">
          Your courier account will be reviewed before activation. Until it is approved you can
          sign in to check its status, but delivery features stay locked.
        </Alert>
        <Button type="submit" fullWidth size="lg" loading={form.submitting}
          loadingText="Submitting application…">
          Submit application
        </Button>
      </form>
    </AuthCard>
  )
}
