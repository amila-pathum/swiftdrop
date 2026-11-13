import { useCallback, useEffect, useRef, useState } from 'react'
import { Navigate } from 'react-router-dom'
import Alert from '../../components/Alert'
import Button, { ButtonLink } from '../../components/Button'
import LoadingSpinner from '../../components/LoadingSpinner'
import { useAuth } from '../../context/AuthContext'
import { getMyApplication } from '../../services/applicationService'
import { describeError, isNotImplemented } from '../../services/errorMessages'
import { resolveApplicationView } from '../../utils/applicationStatus'
import { homePathFor, isActive, requiresApproval } from '../../utils/roles'
import {
  AccountInactiveView,
  ApplicationApprovedView,
  ApplicationPendingView,
  ApplicationRejectedView,
} from './ApplicationViews'

// /application/status: where merchants and couriers whose accounts are not yet
// active are sent. Also explains suspended or disabled accounts.
export default function ApplicationStatusPage() {
  const { user, refreshUser, logout } = useAuth()
  const [application, setApplication] = useState({ loaded: false, data: null, error: null })
  const [checking, setChecking] = useState(false)
  const [lastChecked, setLastChecked] = useState(null)
  const mountedRef = useRef(true)
  const role = user?.role
  const tracksApplication = requiresApproval(role)

  const loadApplication = useCallback(async () => {
    if (!tracksApplication) {
      return { loaded: true, data: null, error: null }
    }
    try {
      return { loaded: true, data: await getMyApplication(role), error: null }
    } catch (error) {
      // A planned endpoint is not an error: fall back to the account status.
      return { loaded: true, data: null, error: isNotImplemented(error) ? null : error }
    }
  }, [role, tracksApplication])

  useEffect(() => {
    mountedRef.current = true
    loadApplication().then((result) => {
      if (mountedRef.current) setApplication(result)
    })
    return () => {
      mountedRef.current = false
    }
  }, [loadApplication])

  async function checkLatest() {
    setChecking(true)
    const [, result] = await Promise.all([refreshUser(), loadApplication()])
    if (!mountedRef.current) return
    setApplication(result)
    setLastChecked(new Date())
    setChecking(false)
  }

  // Active users without an approval workflow have nothing to see here.
  if (isActive(user) && !tracksApplication) {
    return <Navigate to={homePathFor(user) ?? '/dashboard'} replace />
  }

  if (!application.loaded) {
    return (
      <div className="card status-card status-card-loading">
        <LoadingSpinner />
        <p role="status">Checking your application…</p>
      </div>
    )
  }

  const view = resolveApplicationView(user, application.data)
  const checkButton = (
    <Button variant="outline" fullWidth loading={checking} loadingText="Checking…" onClick={checkLatest}>
      Check latest status
    </Button>
  )
  const signOut = <Button variant="ghost" fullWidth onClick={logout}>Sign out</Button>

  return (
    <div className="status-stack">
      {application.error && (
        <Alert variant="warning">
          {`Application details could not be loaded. ${describeError(application.error)}`}
        </Alert>
      )}
      {view === 'approved' && (
        <ApplicationApprovedView
          applicationType={role}
          actions={<ButtonLink to="/dashboard" fullWidth>Go to your dashboard</ButtonLink>}
        />
      )}
      {view === 'pending' && (
        <ApplicationPendingView
          applicationType={role}
          submittedAt={application.data?.submittedAt}
          actions={<>{checkButton}{signOut}</>}
        />
      )}
      {view === 'rejected' && (
        <ApplicationRejectedView
          applicationType={role}
          reviewNote={application.data?.reviewNote}
          actions={<>{checkButton}{signOut}</>}
        />
      )}
      {view === 'inactive' && <AccountInactiveView actions={<>{checkButton}{signOut}</>} />}
      {lastChecked && (
        <p className="status-checked" role="status">
          Last checked at {lastChecked.toLocaleTimeString(undefined, { timeStyle: 'short' })}.
        </p>
      )}
    </div>
  )
}
