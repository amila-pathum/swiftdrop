import NotAvailablePage from '../NotAvailablePage'
import { useAuth } from '../../context/AuthContext'

export default function AdminDashboardPage() {
  const { user } = useAuth()
  return <NotAvailablePage title="Admin dashboard" description={user?.name ? `Welcome, ${user.name}.` : undefined} />
}
