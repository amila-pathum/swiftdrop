import { useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ButtonLink } from '../../components/Button'
import TrackingSearch from '../../components/TrackingSearch'
import { useAuth } from '../../context/AuthContext'

const icon = (d) => (
  <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor"
    strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {d}
  </svg>
)

const ICONS = {
  door: icon(<><path d="M3 21h18" /><path d="M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16" /><circle cx="15" cy="12" r="1" /></>),
  store: icon(<><path d="M3 9l1.5-5h15L21 9" /><path d="M4 9v11h16V9" /><path d="M3 9h18" /><path d="M9 20v-6h6v6" /></>),
  pin: icon(<><path d="M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z" /><circle cx="12" cy="10" r="2.5" /></>),
  route: icon(<><circle cx="6" cy="19" r="2" /><circle cx="18" cy="5" r="2" /><path d="M8 19h7a3.5 3.5 0 0 0 0-7H9a3.5 3.5 0 0 1 0-7h7" /></>),
  tag: icon(<><path d="M20.6 13.4l-7.2 7.2a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8z" /><circle cx="7.5" cy="7.5" r="1.5" /></>),
  bell: icon(<><path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" /></>),
  shield: icon(<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />),
  bolt: icon(<path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />),
  users: icon(<><circle cx="9" cy="8" r="3.5" /><path d="M2 21a7 7 0 0 1 14 0" /><path d="M16 4.5a3.5 3.5 0 0 1 0 7" /><path d="M22 21a7 7 0 0 0-4-6.3" /></>),
  box: icon(<><path d="M21 8l-9-5-9 5v8l9 5 9-5z" /><path d="M3 8l9 5 9-5" /><path d="M12 13v8" /></>),
  bike: icon(<><circle cx="5.5" cy="17" r="3.5" /><circle cx="18.5" cy="17" r="3.5" /><path d="M5.5 17L9 9h6l3.5 8" /><path d="M9 9L7.5 5H5" /><path d="M15 9l-3 8" /></>),
  check: icon(<path d="M5 12l5 5L20 7" />),
}

const SERVICES = [
  { icon: 'door', title: 'Doorstep delivery', text: 'Book a pickup and have parcels delivered straight to the recipient’s door.' },
  { icon: 'store', title: 'Merchant shipping', text: 'Approved businesses create and manage bulk orders for their customers.' },
  { icon: 'pin', title: 'Live parcel tracking', text: 'Follow every parcel from pickup to delivery with a full status history.' },
  { icon: 'route', title: 'Smart dispatch', text: 'Dispatchers assign the right courier for each delivery zone.' },
  { icon: 'tag', title: 'Transparent pricing', text: 'Zone-based delivery costs calculated up front, with no surprises.' },
  { icon: 'bell', title: 'Delivery notifications', text: 'Status updates by email, and webhooks for merchant systems.' },
]

const STEPS = [
  { title: 'Create an account', text: 'Sign up as a customer in a minute, or apply as a merchant or courier.' },
  { title: 'Book a delivery', text: 'Enter pickup and drop-off details and see the cost before you confirm.' },
  { title: 'We dispatch a courier', text: 'A verified courier is assigned and collects your parcel.' },
  { title: 'Track to the door', text: 'Follow each status update until the parcel is delivered.' },
]

const ROLES = [
  {
    key: 'customer',
    icon: 'box',
    title: 'Customer',
    text: 'Send and receive parcels across the island.',
    points: ['Book deliveries online', 'Track every parcel', 'Instant account activation'],
    to: '/register',
    cta: 'Sign up as a customer',
  },
  {
    key: 'merchant',
    icon: 'store',
    title: 'Merchant',
    text: 'Ship orders for your online or retail business.',
    points: ['Bulk order management', 'Merchant API and webhooks', 'Reviewed by SwiftDrop admins'],
    to: '/register/merchant',
    cta: 'Apply as a merchant',
    approval: true,
  },
  {
    key: 'courier',
    icon: 'bike',
    title: 'Courier',
    text: 'Deliver with SwiftDrop in your preferred area.',
    points: ['Choose your delivery area', 'See assigned deliveries', 'Reviewed by SwiftDrop admins'],
    to: '/register/courier',
    cta: 'Apply as a courier',
    approval: true,
  },
]

// Illustrative only: clearly labelled as a sample, never real shipment data.
function HeroVisual() {
  const steps = [
    { label: 'Picked up', time: '09:15', done: true },
    { label: 'At sorting hub', time: '11:40', done: true },
    { label: 'Out for delivery', time: '14:05', current: true },
    { label: 'Delivered', time: '—' },
  ]
  return (
    <div className="hero-visual" aria-hidden="true">
      <div className="hero-card">
        <div className="hero-card-head">
          <span className="hero-card-icon">{ICONS.box}</span>
          <div>
            <p className="hero-card-eyebrow">Sample shipment</p>
            <p className="hero-card-title">SD-2026-000123</p>
          </div>
          <span className="pill pill-info">In transit</span>
        </div>
        <ol className="hero-timeline">
          {steps.map((step) => (
            <li key={step.label}
              className={step.done ? 'is-done' : step.current ? 'is-current' : undefined}>
              <span className="hero-timeline-dot" />
              <span className="hero-timeline-label">{step.label}</span>
              <span className="hero-timeline-time">{step.time}</span>
            </li>
          ))}
        </ol>
      </div>
      <div className="hero-float hero-float-a">
        <span className="hero-float-icon">{ICONS.shield}</span>
        Verified couriers
      </div>
      <div className="hero-float hero-float-b">
        <span className="hero-float-icon">{ICONS.bolt}</span>
        Same-day options
      </div>
    </div>
  )
}

export default function LandingPage() {
  const { hash } = useLocation()
  const { status } = useAuth()
  const signedIn = status === 'authenticated'

  // Scroll to #section links coming from other pages or the navbar.
  useEffect(() => {
    if (hash) {
      document.getElementById(hash.slice(1))?.scrollIntoView?.({ behavior: 'smooth' })
    }
  }, [hash])

  return (
    <>
      <section id="home" className="hero">
        <div className="container hero-grid">
          <div className="hero-copy">
            <p className="eyebrow">Courier management for Sri Lanka</p>
            <h1 className="hero-title">
              Deliveries that are <span className="text-accent">fast, simple</span> and always trackable.
            </h1>
            <p className="hero-lead">
              SwiftDrop connects customers, merchants, couriers and dispatchers on one platform,
              from booking and pricing to dispatch and doorstep delivery.
            </p>
            <TrackingSearch id="hero-tracking-number" />
            <div className="hero-actions">
              {signedIn ? (
                <ButtonLink to="/dashboard" size="lg">Go to dashboard</ButtonLink>
              ) : (
                <>
                  <ButtonLink to={{ pathname: '/', hash: '#get-started' }} size="lg">Get started</ButtonLink>
                  <ButtonLink to="/login" size="lg" variant="outline">Log in</ButtonLink>
                </>
              )}
            </div>
          </div>
          <HeroVisual />
        </div>
      </section>

      <section className="feature-strip" aria-label="Highlights">
        <div className="container feature-strip-grid">
          {[['pin', 'Real-time tracking'], ['shield', 'Verified couriers'], ['store', 'Merchant integrations'], ['users', 'Role-based accounts']]
            .map(([key, label]) => (
              <p key={label} className="feature-strip-item">
                <span className="feature-strip-icon">{ICONS[key]}</span>{label}
              </p>
            ))}
        </div>
      </section>

      <section id="about" className="section">
        <div className="container about-grid">
          <div>
            <p className="eyebrow">About SwiftDrop</p>
            <h2 className="section-title">One platform for every step of a delivery</h2>
            <p className="section-lead">
              SwiftDrop is a courier management system built for the way deliveries really work.
              Customers book and track parcels, merchants ship orders at scale, dispatchers plan
              routes by zone, and couriers see exactly what to deliver next.
            </p>
          </div>
          <ul className="about-points">
            {[
              ['shield', 'Secure by design', 'Every account has a role, and every action is checked by the service that owns it.'],
              ['users', 'Approved partners', 'Merchants and couriers are reviewed by SwiftDrop administrators before they start.'],
              ['bolt', 'Built to scale', 'Independent services for parcels, pricing, dispatch and notifications.'],
            ].map(([key, title, text]) => (
              <li key={title}>
                <span className="icon-tile">{ICONS[key]}</span>
                <div>
                  <h3>{title}</h3>
                  <p>{text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section id="services" className="section section-muted">
        <div className="container">
          <div className="section-head">
            <p className="eyebrow">Services</p>
            <h2 className="section-title">Everything you need to send and deliver</h2>
          </div>
          <ul className="card-grid">
            {SERVICES.map((service) => (
              <li key={service.title} className="feature-card">
                <span className="icon-tile">{ICONS[service.icon]}</span>
                <h3>{service.title}</h3>
                <p>{service.text}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <div className="section-head">
            <p className="eyebrow">How it works</p>
            <h2 className="section-title">From booking to doorstep in four steps</h2>
          </div>
          <ol className="steps">
            {STEPS.map((step, index) => (
              <li key={step.title}>
                <span className="step-number">{index + 1}</span>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section id="track" className="section track-band">
        <div className="container track-band-inner">
          <div>
            <h2 className="section-title section-title-inverted">Track your parcel</h2>
            <p className="section-lead section-lead-inverted">
              Enter the tracking number from your booking confirmation to see where your parcel is.
            </p>
          </div>
          <TrackingSearch id="band-tracking-number" />
        </div>
      </section>

      <section id="get-started" className="section">
        <div className="container">
          <div className="section-head">
            <p className="eyebrow">Get started</p>
            <h2 className="section-title">Join SwiftDrop your way</h2>
            <p className="section-lead">
              Customer accounts are ready straight away. Merchant and courier applications are
              reviewed by a SwiftDrop administrator before the account is activated.
            </p>
          </div>
          <ul className="role-grid">
            {ROLES.map((role) => (
              <li key={role.key} className={`role-card role-card-${role.key}`}>
                <span className="icon-tile icon-tile-lg">{ICONS[role.icon]}</span>
                <h3>{role.title}</h3>
                <p>{role.text}</p>
                <ul className="check-list">
                  {role.points.map((point) => (
                    <li key={point}><span>{ICONS.check}</span>{point}</li>
                  ))}
                </ul>
                {role.approval && <span className="pill pill-warning">Requires admin approval</span>}
                <ButtonLink to={role.to} fullWidth variant={role.key === 'customer' ? 'primary' : 'outline'}>
                  {role.cta}
                </ButtonLink>
              </li>
            ))}
          </ul>
          {!signedIn && (
            <p className="section-footnote">
              Already have an account? <Link to="/login">Log in</Link>
            </p>
          )}
        </div>
      </section>
    </>
  )
}
