import { Link } from 'react-router-dom'
import AuthLayout from '../layouts/AuthLayout'

export default function NotFoundPage() {
  return (
    <AuthLayout>
      <section className="card status-card">
        <h1 className="card-title">Page not found</h1>
        <p className="card-subtitle">The page you are looking for does not exist.</p>
        <Link to="/dashboard" className="button button-primary button-full">
          Go to your dashboard
        </Link>
      </section>
    </AuthLayout>
  )
}
