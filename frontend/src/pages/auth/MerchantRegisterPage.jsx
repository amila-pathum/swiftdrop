import { Link, useNavigate } from 'react-router-dom'
import Alert from '../../components/Alert'
import AuthCard from '../../components/AuthCard'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import FormSection from '../../components/FormSection'
import RegistrationTypeTabs from '../../components/RegistrationTypeTabs'
import { useForm } from '../../hooks/useForm'
import { submitMerchantApplication } from '../../services/applicationService'
import { describeError } from '../../services/errorMessages'
import { validateMerchantRegistration } from './authForm'

const EMPTY_FORM = {
  businessName: '',
  businessRegistrationNumber: '',
  businessEmail: '',
  businessPhone: '',
  businessAddress: '',
  contactName: '',
  contactEmail: '',
  password: '',
  confirmPassword: '',
}

const NOT_OPEN_MESSAGE = 'Merchant applications are not open yet: the Merchant Service has not '
  + 'published its application endpoint. Your details have not been submitted.'

const trimmed = (values) => Object.fromEntries(Object.entries(values).map(([key, value]) => [
  key, key === 'password' || key === 'confirmPassword' ? value : value.trim(),
]))

// Merchant business application. The account stays pending until an admin approves it.
export default function MerchantRegisterPage() {
  const navigate = useNavigate()
  const form = useForm({
    initialValues: EMPTY_FORM,
    validate: validateMerchantRegistration,
    idPrefix: 'merchant',
  })

  const onSubmit = form.handleSubmit(async (values) => {
    const details = trimmed(values)
    try {
      await submitMerchantApplication(details)
      navigate('/application/submitted', {
        state: { applicationType: 'MERCHANT', email: details.contactEmail },
      })
    } catch (error) {
      if (error?.status === 409) {
        form.setFormError('An account or application already exists for this contact email or '
          + 'business registration number. Sign in to check its status.')
      } else {
        form.setFormError(describeError(error, {
          notImplemented: NOT_OPEN_MESSAGE,
          byStatus: { 400: 'Some details are invalid. Check the form and try again.' },
        }))
      }
    }
  })

  return (
    <AuthCard
      wide
      title="Apply for a merchant account"
      subtitle="Ship orders for your business with SwiftDrop. Applications are reviewed by a SwiftDrop administrator."
      footer={(
        <p className="card-footer-text">
          Already applied? <Link to="/login">Sign in to check your status</Link>
        </p>
      )}
    >
      <RegistrationTypeTabs />
      {form.formError && <Alert variant="error">{form.formError}</Alert>}

      <form className="form" onSubmit={onSubmit} noValidate>
        <FormSection title="Business details" description="As shown on your business registration.">
          <FormField {...form.field('businessName')} label="Business name" autoComplete="organization"
            className="form-field-full" />
          <FormField {...form.field('businessRegistrationNumber')} label="Business registration number"
            placeholder="PV 00123456" />
          <FormField {...form.field('businessEmail')} label="Business email" type="email"
            autoComplete="email" />
          <FormField {...form.field('businessPhone')} label="Business phone" type="tel"
            autoComplete="tel" placeholder="011 234 5678" />
          <FormField {...form.field('businessAddress')} label="Business address" as="textarea"
            autoComplete="street-address" className="form-field-full" />
        </FormSection>

        <FormSection title="Account details"
          description="The contact person signs in with this email once the application is approved.">
          <FormField {...form.field('contactName')} label="Contact person's name" autoComplete="name" />
          <FormField {...form.field('contactEmail')} label="Contact person's email" type="email"
            autoComplete="username" />
          <FormField {...form.field('password')} label="Password" type="password" revealable
            autoComplete="new-password" hint="Use 8 to 72 characters." />
          <FormField {...form.field('confirmPassword')} label="Confirm password" type="password"
            revealable autoComplete="new-password" />
        </FormSection>

        <Alert variant="info">
          Your merchant account will be reviewed before activation. Until it is approved you can
          sign in to check its status, but merchant features stay locked.
        </Alert>
        <Button type="submit" fullWidth size="lg" loading={form.submitting}
          loadingText="Submitting application…">
          Submit application
        </Button>
      </form>
    </AuthCard>
  )
}
