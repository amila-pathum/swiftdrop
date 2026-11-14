import { useState } from 'react'
import Alert from '../../components/Alert'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import { useAuth } from '../../context/AuthContext'
import { createDispatcher } from '../../services/adminUserService'
import { describeError } from '../../services/errorMessages'

const EMPTY_FORM = { name: '', email: '', password: '' }

function validate(values) {
  const errors = {}
  if (!values.name.trim()) errors.name = 'Enter the dispatcher’s full name.'
  if (!/^\S+@\S+\.\S+$/.test(values.email.trim())) errors.email = 'Enter a valid email address.'
  if (values.password.length < 12 || values.password.length > 72) {
    errors.password = 'Temporary password must be 12 to 72 characters.'
  }
  return errors
}

export default function AdminDashboardPage() {
  const { user } = useAuth()
  const [values, setValues] = useState(EMPTY_FORM)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const change = ({ target }) => {
    setValues((current) => ({ ...current, [target.name]: target.value }))
    setErrors((current) => ({ ...current, [target.name]: undefined }))
    setResult(null)
    setError(null)
  }

  const submit = async (event) => {
    event.preventDefault()
    const found = validate(values)
    setErrors(found)
    if (Object.keys(found).length > 0 || submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const created = await createDispatcher(values)
      setResult(created)
      setValues(EMPTY_FORM)
    } catch (caught) {
      setError(describeError(caught, {
        byStatus: {
          400: 'Check the dispatcher details and temporary password.',
          403: 'Your account is not allowed to create staff users.',
          409: 'An account already uses that email address.',
        },
      }))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="section">
      <div className="container container-narrow">
        <h1 className="section-title">Admin dashboard</h1>
        <p className="section-lead">
          {user?.name ? `Welcome, ${user.name}. ` : ''}Create staff accounts for dispatch operations.
        </p>

        <section className="card" aria-labelledby="create-dispatcher-title">
          <h2 id="create-dispatcher-title" className="card-title">Create dispatcher account</h2>
          <p className="card-subtitle">
            The account is active immediately. Share the temporary password through a secure channel.
          </p>
          {result && (
            <Alert variant="success">
              Dispatcher {result.name} ({result.email}) was created successfully.
            </Alert>
          )}
          {error && <Alert variant="error">{error}</Alert>}
          <form className="form" onSubmit={submit} noValidate>
            <FormField id="dispatcher-name" name="name" label="Full name" required
              autoComplete="name" value={values.name} onChange={change} error={errors.name} />
            <FormField id="dispatcher-email" name="email" label="Email" type="email" required
              autoComplete="email" value={values.email} onChange={change} error={errors.email} />
            <FormField id="dispatcher-password" name="password" label="Temporary password"
              type="password" revealable required autoComplete="new-password"
              hint="12 to 72 characters. The password is sent only to Identity Service."
              value={values.password} onChange={change} error={errors.password} />
            <FormField id="dispatcher-role" name="role" label="Role" value="DISPATCHER"
              readOnly disabled />
            <Button type="submit" loading={submitting} loadingText="Creating dispatcher…">
              Create dispatcher
            </Button>
          </form>
        </section>

        <p className="form-hint">
          User listing is not shown because Identity Service does not provide an admin list endpoint.
        </p>
      </div>
    </div>
  )
}
