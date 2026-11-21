import { useState } from 'react'
import { Link } from 'react-router-dom'
import Alert from '../../components/Alert'
import Button from '../../components/Button'
import Checkbox from '../../components/Checkbox'
import FormField from '../../components/FormField'
import { useAuth } from '../../context/AuthContext'
import { createStaffAccount } from '../../services/adminUserService'
import { describeError } from '../../services/errorMessages'

const ROLE_OPTIONS = [
  { value: 'DISPATCHER', label: 'Dispatcher' },
  { value: 'ADMIN', label: 'Administrator' },
]

const ROLE_NAMES = { DISPATCHER: 'Dispatcher', ADMIN: 'Administrator' }

const EMPTY_FORM = { name: '', email: '', password: '', role: 'DISPATCHER', confirmAdmin: false }

function validate(values) {
  const errors = {}
  if (!values.name.trim()) errors.name = 'Enter the staff member’s full name.'
  if (!/^\S+@\S+\.\S+$/.test(values.email.trim())) errors.email = 'Enter a valid email address.'
  if (values.password.length < 12 || values.password.length > 72) {
    errors.password = 'Temporary password must be 12 to 72 characters.'
  }
  if (!ROLE_NAMES[values.role]) errors.role = 'Choose Dispatcher or Administrator.'
  if (values.role === 'ADMIN' && !values.confirmAdmin) {
    errors.confirmAdmin = 'Confirm that this person should have full administrator access.'
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

  const isAdmin = values.role === 'ADMIN'
  const roleName = ROLE_NAMES[values.role] ?? 'Staff'

  const change = ({ target }) => {
    const value = target.type === 'checkbox' ? target.checked : target.value
    setValues((current) => ({
      ...current,
      [target.name]: value,
      // A changed role must be confirmed again.
      ...(target.name === 'role' ? { confirmAdmin: false } : {}),
    }))
    setErrors((current) => ({ ...current, [target.name]: undefined, confirmAdmin: undefined }))
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
      const created = await createStaffAccount(values)
      setResult(created)
      setValues(EMPTY_FORM)
    } catch (caught) {
      setError(describeError(caught, {
        byStatus: {
          400: 'Check the account details and temporary password.',
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
          {user?.name ? `Welcome, ${user.name}. ` : ''}Create dispatcher and administrator accounts.
        </p>

        <section className="card" aria-labelledby="create-staff-title">
          <h2 id="create-staff-title" className="card-title">Create staff account</h2>
          <p className="card-subtitle">
            The account is active immediately. Share the temporary password through a secure channel.
            Couriers and merchants apply themselves and are approved on their approval pages.
          </p>
          {result && (
            <Alert variant="success">
              {ROLE_NAMES[result.role] ?? 'Staff'} {result.name} ({result.email}) was created successfully.
            </Alert>
          )}
          {error && <Alert variant="error">{error}</Alert>}
          <form className="form" onSubmit={submit} noValidate>
            <FormField id="staff-role" name="role" label="Role" as="select" required
              placeholder="Select a role" options={ROLE_OPTIONS}
              value={values.role} onChange={change} error={errors.role} />
            <FormField id="staff-name" name="name" label="Full name" required
              autoComplete="off" value={values.name} onChange={change} error={errors.name} />
            <FormField id="staff-email" name="email" label="Email" type="email" required
              autoComplete="off" value={values.email} onChange={change} error={errors.email} />
            <FormField id="staff-password" name="password" label="Temporary password"
              type="password" revealable required autoComplete="new-password"
              hint="12 to 72 characters. The password is sent only to Identity Service."
              value={values.password} onChange={change} error={errors.password} />

            {isAdmin && (
              <div className="form-field">
                <Alert variant="warning">
                  An administrator has full access: they can manage every user, change roles,
                  approve merchants and couriers, and create more administrators.
                </Alert>
                <Checkbox id="staff-confirm-admin" name="confirmAdmin"
                  label="I understand this person will have full administrator access."
                  checked={values.confirmAdmin} onChange={change}
                  aria-invalid={errors.confirmAdmin ? 'true' : undefined}
                  aria-describedby={errors.confirmAdmin ? 'staff-confirm-admin-error' : undefined} />
                {errors.confirmAdmin && (
                  <p id="staff-confirm-admin-error" className="form-error">{errors.confirmAdmin}</p>
                )}
              </div>
            )}

            <Button type="submit" loading={submitting} loadingText={`Creating ${roleName.toLowerCase()}…`}>
              Create {roleName.toLowerCase()}
            </Button>
          </form>
        </section>

        <p className="form-hint">
          Looking to view, search, or update accounts? Go to{' '}
          <Link to="/admin/users" style={{ color: 'var(--color-primary)', fontWeight: 500, textDecoration: 'underline' }}>
            User Management
          </Link>.
        </p>
      </div>
    </div>
  )
}
