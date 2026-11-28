import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import AssistantChat from '../components/AssistantChat'
import Navbar from '../components/Navbar'
import Sidebar from '../components/Sidebar'
import { useAuth } from '../context/AuthContext'
import { isActive, isKnownRole } from '../utils/roles'

/**
 * Dashboard layout shared by every role: top bar, role-aware sidebar (a
 * drawer on small screens) and the page content. Use as a layout route, or
 * wrap content directly: <AppLayout>…</AppLayout>.
 */
export default function AppLayout({ children }) {
  const { user } = useAuth()
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
      {isActive(user) && isKnownRole(user.role) && <AssistantChat key={`${user.id}:${user.role}`} />}
    </div>
  )
}
