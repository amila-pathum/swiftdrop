import PageHeader from '../components/PageHeader'
import { useAuth } from '../context/AuthContext'

function formatDate(value) {
  const date = new Date(value)
  return value && !Number.isNaN(date.getTime())
    ? date.toLocaleDateString(undefined, { dateStyle: 'medium' })
    : '—'
}

// Shared by all roles. Shows only the fields returned by GET /api/users/me.
export default function ProfilePage() {
  const { user } = useAuth()

  return (
    <section>
      <PageHeader title="Your profile" description="Your SwiftDrop account details." />
      <dl className="profile-details">
        <dt>Name</dt>
        <dd>{user.name}</dd>
        <dt>Email</dt>
        <dd>{user.email}</dd>
        <dt>Role</dt>
        <dd>{user.role}</dd>
        <dt>Account status</dt>
        <dd>{user.status}</dd>
        <dt>Member since</dt>
        <dd>{formatDate(user.createdAt)}</dd>
      </dl>
    </section>
  )
}
