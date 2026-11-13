import { Link } from 'react-router-dom'

export function LogoMark({ size = 26 }) {
  return (
    <span className="logo-mark" style={{ width: size, height: size }} aria-hidden="true">
      <svg viewBox="0 0 24 24" width={size * 0.62} height={size * 0.62} fill="none"
        stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 8l-9-5-9 5v8l9 5 9-5z" />
        <path d="M3 8l9 5 9-5" />
        <path d="M12 13v8" />
      </svg>
    </span>
  )
}

/** SwiftDrop mark + wordmark, linking home. `inverted` for dark backgrounds. */
export default function Logo({ to = '/', inverted = false, onClick }) {
  return (
    <Link to={to} className={`brand${inverted ? ' brand-inverted' : ''}`} onClick={onClick}>
      <LogoMark />
      SwiftDrop
    </Link>
  )
}
