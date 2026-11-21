import { useState } from 'react'
import Alert from '../Alert'
import { courierService } from '../../services/courierService'
import { courierError } from '../../utils/courier/deliveryStatus'

export default function AvailabilityControl({ profile, onChanged }) {
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)
  async function change(event) {
    const next = event.target.value
    setSaving(true); setError(null)
    try { await courierService.updateAvailability(next); onChanged() }
    catch (error) { setError(courierError(error, 'profile')) }
    finally { setSaving(false) }
  }
  return <div className="courier-availability"><label htmlFor="courier-availability">Availability</label><select id="courier-availability" className="input select" value={profile.availability} disabled={saving || !profile.active} onChange={change}>{['AVAILABLE', 'BUSY', 'OFFLINE'].map((value) => <option key={value} value={value}>{value.charAt(0) + value.slice(1).toLowerCase()}</option>)}</select>{saving && <span role="status">Saving availability...</span>}{!profile.active && <span>Your profile is inactive. Contact your administrator.</span>}{error && <Alert variant="error">{error}</Alert>}</div>
}
