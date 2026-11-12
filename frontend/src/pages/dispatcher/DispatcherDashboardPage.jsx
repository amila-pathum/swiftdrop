import NotAvailablePage from '../NotAvailablePage'
import { useAuth } from '../../context/AuthContext'

export default function DispatcherDashboardPage() {
  const { user } = useAuth()
  return <NotAvailablePage title="Dispatcher dashboard" description={user?.name ? `Welcome, ${user.name}.` : undefined} />
}
