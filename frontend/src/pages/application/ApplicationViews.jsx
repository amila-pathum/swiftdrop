import { ButtonLink } from '../../components/Button'

const ICONS = {
  success: <path d="M5 12l5 5L20 7" />,
  pending: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
  error: <><circle cx="12" cy="12" r="9" /><path d="M15 9l-6 6M9 9l6 6" /></>,
  info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v5M12 8h.01" /></>,
}

const APPLICATION_NAMES = { MERCHANT: 'merchant', COURIER: 'courier' }

function applicationName(type) {
  return APPLICATION_NAMES[type] ?? 'partner'
}

function formatDate(value) {
  const date = new Date(value)
  return value && !Number.isNaN(date.getTime())
    ? date.toLocaleDateString(undefined, { dateStyle: 'medium' })
    : null
}

/** Shared full-card status layout: icon, heading, message, details and actions. */
export function StatusPanel({ tone = 'info', title, children, actions }) {
  return (
    <section className={`card status-panel status-panel-${tone}`} aria-labelledby="status-title">
      <span className="status-panel-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor"
          strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">{ICONS[tone]}</svg>
      </span>
      <h1 id="status-title" className="card-title">{title}</h1>
      <div className="status-panel-body">{children}</div>
      {actions && <div className="status-panel-actions">{actions}</div>}
    </section>
  )
}

function NextSteps({ steps }) {
  return (
    <ol className="next-steps">
      {steps.map((step) => <li key={step}>{step}</li>)}
    </ol>
  )
}

/** After a merchant or courier application has been sent (not signed in). */
export function ApplicationSubmittedView({ applicationType, email }) {
  // Reached without state (e.g. a reload): describe both application types.
  const name = APPLICATION_NAMES[applicationType] ?? 'merchant or courier'
  return (
    <StatusPanel
      tone="success"
      title="Application submitted"
      actions={(
        <>
          <ButtonLink to="/login" fullWidth>Sign in to check your status</ButtonLink>
          <ButtonLink to="/" variant="ghost" fullWidth>Back to home</ButtonLink>
        </>
      )}
    >
      <p>
        Thank you for applying for a SwiftDrop {name} account
        {email ? <> with <strong>{email}</strong></> : null}.
      </p>
      <p>Your {name} account requires approval by a SwiftDrop administrator before it can be used.</p>
      <NextSteps steps={[
        'An administrator reviews the details you submitted.',
        'You can sign in at any time to check the status of your application.',
        'Once approved, your dashboard and features are unlocked.',
      ]} />
    </StatusPanel>
  )
}

/** Signed in, waiting for an admin decision. */
export function ApplicationPendingView({ applicationType, submittedAt, actions }) {
  const name = applicationName(applicationType)
  const submitted = formatDate(submittedAt)
  return (
    <StatusPanel tone="pending" title="Application pending" actions={actions}>
      <p>
        Your {name} application is awaiting review by a SwiftDrop administrator.
        {submitted && <> It was submitted on {submitted}.</>}
      </p>
      <p>
        {name.charAt(0).toUpperCase() + name.slice(1)} features stay locked until your
        application is approved. Check back here for the latest status.
      </p>
    </StatusPanel>
  )
}

/** The account has been approved and is active. */
export function ApplicationApprovedView({ applicationType, actions }) {
  const name = applicationName(applicationType)
  return (
    <StatusPanel tone="success" title="Application approved" actions={actions}>
      <p>Good news: your {name} account has been approved and is now active.</p>
    </StatusPanel>
  )
}

/** The application was rejected. Shows the reviewer's note when the backend provides one. */
export function ApplicationRejectedView({ applicationType, reviewNote, actions }) {
  const name = applicationName(applicationType)
  return (
    <StatusPanel tone="error" title="Application not approved" actions={actions}>
      <p>Unfortunately your {name} application was not approved.</p>
      {reviewNote && (
        <blockquote className="review-note">
          <p className="review-note-label">Reviewer&apos;s note</p>
          <p>{reviewNote}</p>
        </blockquote>
      )}
      <p>If you believe this is a mistake, please contact SwiftDrop support.</p>
    </StatusPanel>
  )
}

/** Suspended or disabled accounts. */
export function AccountInactiveView({ actions }) {
  return (
    <StatusPanel tone="error" title="Account not active" actions={actions}>
      <p>Your account is not active, so SwiftDrop features are unavailable.</p>
      <p>Please contact SwiftDrop support for help.</p>
    </StatusPanel>
  )
}
