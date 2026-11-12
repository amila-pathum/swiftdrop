import EmptyState from '../components/EmptyState'
import PageHeader from '../components/PageHeader'

// Placeholder for dashboards and pages that later phases will build.
export default function NotAvailablePage({ title = 'Not available yet', description }) {
  return (
    <section>
      <PageHeader title={title} description={description} />
      <EmptyState
        title="Coming soon"
        description="This functionality will be implemented in a future development phase."
      />
    </section>
  )
}
