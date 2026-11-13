import NotAvailablePage from '../NotAvailablePage'
import { useAuth } from '../../context/AuthContext'

export default function CourierDashboardPage() {
  const { user } = useAuth()
  return <NotAvailablePage title="Courier dashboard" description={user?.name ? `Welcome, ${user.name}.` : undefined} />
}
