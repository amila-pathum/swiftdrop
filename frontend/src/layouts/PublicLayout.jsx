import { Outlet } from 'react-router-dom'
import Footer from '../components/Footer'
import PublicNavbar from '../components/PublicNavbar'

/** Marketing layout: public navigation, page content and footer. */
export default function PublicLayout({ children }) {
  return (
    <div className="public-shell">
      <PublicNavbar />
      <main id="main">{children ?? <Outlet />}</main>
      <Footer />
    </div>
  )
}
