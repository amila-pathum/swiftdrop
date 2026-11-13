import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Navbar from '../components/Navbar'
import Sidebar from '../components/Sidebar'

/**
 * Dashboard layout shared by every role: top bar, role-aware sidebar (a
 * drawer on small screens) and the page content. Use as a layout route, or
 * wrap content directly: <AppLayout>…</AppLayout>.
 */
export default function AppLayout({ children }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const closeMenu = () => setMenuOpen(false)

  return (
    <div className="app-layout">
      <Navbar onMenuClick={() => setMenuOpen((open) => !open)} menuOpen={menuOpen} />
      <div className="app-body">
        <Sidebar open={menuOpen} onNavigate={closeMenu} />
        {menuOpen && <div className="sidebar-backdrop" onClick={closeMenu} aria-hidden="true" />}
        <main className="app-content">
          {children ?? <Outlet />}
        </main>
      </div>
    </div>
  )
}
