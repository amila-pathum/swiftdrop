import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ButtonLink } from './Button'
import Logo from './Logo'

const SECTIONS = [
  { hash: '#home', label: 'Home' },
  { hash: '#about', label: 'About' },
  { hash: '#services', label: 'Services' },
  { hash: '#track', label: 'Track parcel' },
]

/** Navigation for public marketing pages; collapses to a menu on small screens. */
export default function PublicNavbar() {
  const { status } = useAuth()
  const [open, setOpen] = useState(false)
  const close = () => setOpen(false)
  const signedIn = status === 'authenticated'

  return (
    <header className="public-navbar">
      <div className="container public-navbar-inner">
        <Logo onClick={close} />
        <button
          type="button"
          className="icon-button public-navbar-toggle"
          aria-label={open ? 'Close menu' : 'Open menu'}
          aria-expanded={open}
          aria-controls="public-menu"
          onClick={() => setOpen((value) => !value)}
        >
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor"
            strokeWidth="2" strokeLinecap="round" aria-hidden="true">
            {open ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
          </svg>
        </button>
        <nav id="public-menu" className={`public-menu${open ? ' public-menu-open' : ''}`}
          aria-label="Site">
          <ul className="public-links">
            {SECTIONS.map(({ hash, label }) => (
              <li key={hash}>
                <Link to={{ pathname: '/', hash }} onClick={close}>{label}</Link>
              </li>
            ))}
          </ul>
          <div className="public-actions">
            {signedIn ? (
              <ButtonLink to="/dashboard" onClick={close}>Go to dashboard</ButtonLink>
            ) : (
              <>
                <ButtonLink to="/login" variant="ghost" onClick={close}>Log in</ButtonLink>
                <ButtonLink to={{ pathname: '/', hash: '#get-started' }} onClick={close}>
                  Get started
                </ButtonLink>
              </>
            )}
          </div>
        </nav>
      </div>
    </header>
  )
}
