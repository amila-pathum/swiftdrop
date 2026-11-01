import { useAuth } from '../../context/AuthContext'

export default function CustomerDashboardPage() {
  const { user } = useAuth()

  return (
    <section>
      <h1>Customer dashboard</h1>
      <p>Welcome, {user?.name}.</p>
      <p>Your deliveries and delivery requests will appear here.</p>
    </section>
  )
}
