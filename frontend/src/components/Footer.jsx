import { Link } from 'react-router-dom'
import Logo from './Logo'

const COLUMNS = [
  {
    title: 'Company',
    links: [
      { to: { pathname: '/', hash: '#about' }, label: 'About us' },
      { to: { pathname: '/', hash: '#services' }, label: 'Services' },
    ],
  },
  {
    title: 'Get started',
    links: [
      { to: '/register', label: 'Send parcels' },
      { to: '/register/merchant', label: 'Become a merchant' },
      { to: '/register/courier', label: 'Become a courier' },
    ],
  },
  {
    title: 'Support',
    links: [
      { to: '/track', label: 'Track a parcel' },
      { to: '/login', label: 'Log in' },
      { to: '/forgot-password', label: 'Reset password' },
    ],
  },
]

export default function Footer() {
  return (
    <footer className="site-footer">
      <div className="container site-footer-grid">
        <div className="site-footer-brand">
          <Logo inverted />
          <p>Fast, reliable and trackable deliveries for customers and businesses across Sri Lanka.</p>
        </div>
        {COLUMNS.map((column) => (
          <div key={column.title}>
            <h2 className="site-footer-title">{column.title}</h2>
            <ul>
              {column.links.map((link) => (
                <li key={link.label}><Link to={link.to}>{link.label}</Link></li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="container site-footer-bottom">
        <p>© {new Date().getFullYear()} SwiftDrop. All rights reserved.</p>
      </div>
    </footer>
  )
}
