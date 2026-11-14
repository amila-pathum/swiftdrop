import { ButtonLink } from '../../components/Button'
import EmptyState from '../../components/EmptyState'
import PageHeader from '../../components/PageHeader'
import { useAuth } from '../../context/AuthContext'

export default function CustomerDashboardPage() {
  const { user } = useAuth()

  return (
    <section>
      <PageHeader
        title="Customer dashboard"
        description={<>Welcome, {user?.name}.</>}
        actions={<><ButtonLink to="/customer/create-delivery">Create Delivery</ButtonLink>{' '}<ButtonLink to="/track" variant="outline">Track a parcel</ButtonLink></>}
      />
      <EmptyState
        title="No deliveries yet"
        description="Your deliveries and delivery requests will appear here."
      />
    </section>
  )
}
