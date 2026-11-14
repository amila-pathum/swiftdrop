import { useRef, useState } from 'react'
import Button from '../../../components/Button'
import FormField from '../../../components/FormField'
import { merchantService } from '../../../services/merchantService'

const fields = [
  ['businessName', 'Business name'],
  ['registrationNumber', 'Business registration number'],
  ['contactName', 'Contact person'],
  ['email', 'Business email', 'email'],
  ['phone', 'Business phone', 'tel'],
  ['address', 'Business address'],
]

export default function MerchantProfileSetup({ onRegistered, onRefresh }) {
  const [values, setValues] = useState(() => Object.fromEntries(fields.map(([name]) => [name, ''])))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const lock = useRef(false)

  async function submit(event) {
    event.preventDefault()
    if (lock.current) return
    const details = Object.fromEntries(Object.entries(values).map(([name, value]) => [name, value.trim()]))
    if (Object.values(details).some((value) => !value)) {
      setError('Complete all business profile fields.'); return
    }
    if (!/^[^\s@]+@[^\s@]+$/.test(details.email)) {
      setError('Enter a valid business email address.'); return
    }
    lock.current = true; setBusy(true); setError('')
    try {
      const profile = await merchantService.registerMyMerchant(details)
      onRegistered(profile)
    } catch (err) {
      setError(err.status === 409
        ? 'A profile or registration number already exists. Check profile status before submitting again, or use your correct business registration number.'
        : err.message || 'Unable to submit your business profile. Check profile status before trying again.')
    } finally { lock.current = false; setBusy(false) }
  }

  return <section className="webhook-endpoint-card" aria-labelledby="merchant-setup-title">
    <div className="webhook-endpoint-heading"><h2 id="merchant-setup-title">Set up your business profile</h2></div>
    <form className="webhook-endpoint-form" onSubmit={submit} noValidate>
      <p>Your login is ready, but it has no linked business profile. Submit your business details for administrator approval to enable webhooks.</p>
      {error && <div className="alert alert-error" role="alert">{error}</div>}
      <fieldset className="webhook-registration-fields" disabled={busy}>
        <legend className="visually-hidden">Business details</legend>
        {fields.map(([name, label, type = 'text']) => <FormField key={name} id={`merchant-setup-${name}`}
          label={label} type={type} as={name === 'address' ? 'textarea' : 'input'} required
          value={values[name]} onChange={(event) => setValues({ ...values, [name]: event.target.value })} />)}
      </fieldset>
      <p className="webhook-help">This application is linked to your current signed-in account. It requires a separate business approval.</p>
      <div className="webhook-form-actions">
        <Button variant="secondary" disabled={busy} onClick={onRefresh}>Check profile status</Button>
        <Button type="submit" loading={busy} loadingText="Submitting…">Submit business profile</Button>
      </div>
    </form>
  </section>
}
