import NotAvailablePage from '../NotAvailablePage'
import { useAuth } from '../../context/AuthContext'

export default function MerchantDashboardPage() {
  const { user } = useAuth()
  return <NotAvailablePage title="Merchant dashboard" description={user?.name ? `Welcome, ${user.name}.` : undefined} />
}
