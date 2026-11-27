import { useState } from 'react'
import Alert from '../../../../components/Alert'
import { courierService } from '../../../../services/courierService'
import { courierError } from '../../../../utils/courier/deliveryStatus'

const OPTIONS = [
  ['AVAILABLE', 'Available', 'Ready for new deliveries'],
  ['BUSY', 'Busy', 'Finishing current work'],
  ['OFFLINE', 'Offline', 'Not taking deliveries'],
]

/** Availability as three clear choices; the dispatcher sees the change immediately. */
export default function AvailabilityPicker({ profile, onChanged }) {
  const [saving, setSaving] = useState(null)
  const [error, setError] = useState(null)
  async function choose(value) {
    if (value === profile.availability || saving) return
    setSaving(value); setError(null)
    try { await courierService.updateAvailability(value); onChanged() }
    catch (failure) { setError(courierError(failure, 'profile')) }
    finally { setSaving(null) }
  }
  return <section className="courier-panel profile-availability" aria-labelledby="profile-availability-title">
    <div className="profile-availability-intro">
      <h2 id="profile-availability-title">Availability</h2>
      <p>Your dispatcher uses this when assigning new parcels. Deliveries already assigned to you stay with you.</p>
    </div>
    <div className="profile-availability-options" role="radiogroup" aria-labelledby="profile-availability-title">
      {OPTIONS.map(([value, label, hint]) => {
        const selected = profile.availability === value
        return <button key={value} type="button" role="radio" aria-checked={selected} disabled={!profile.active || Boolean(saving)}
          className={`profile-availability-option is-${value.toLowerCase()}${selected ? ' is-selected' : ''}`} onClick={() => choose(value)}>
          <span className="profile-dot" aria-hidden="true" />
          <span><strong>{label}</strong><small>{saving === value ? 'Saving...' : hint}</small></span>
        </button>
      })}
    </div>
    {!profile.active && <p className="courier-help">Your profile is inactive, so availability cannot be changed. Contact your administrator.</p>}
    {error && <Alert variant="error">{error}</Alert>}
  </section>
}
