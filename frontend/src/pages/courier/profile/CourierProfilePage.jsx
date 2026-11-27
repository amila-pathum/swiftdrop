import { useState } from 'react'
import PageHeader from '../../../components/PageHeader'
import Alert from '../../../components/Alert'
import Button from '../../../components/Button'
import LoadingSpinner from '../../../components/LoadingSpinner'
import { useAuth } from '../../../hooks/useAuth'
import { useCourierProfile } from '../../../hooks/courier/useCourierProfile'
import { useCourierDeliveries } from '../../../hooks/courier/useCourierDeliveries'
import { courierError, dateLabel } from '../../../utils/courier/deliveryStatus'
import { summarise } from '../../../utils/courier/dashboard'
import { vehicleLabel } from '../../../utils/courier/courierValidation'
import AvailabilityPicker from './components/AvailabilityPicker'
import ProfileDetails from './components/ProfileDetails'
import ProfileEditForm from './components/ProfileEditForm'
import '../../../components/courier/courier.css'
import './courierProfile.css'

const initials = (name) => (name || '?').trim().split(/\s+/).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('') || '?'
const title = (value) => value ? value.charAt(0) + value.slice(1).toLowerCase() : ''

function Stats({ deliveries }) {
  const summary = deliveries.data ? summarise(deliveries.data) : null
  const items = [
    ['Completed deliveries', summary?.delivered],
    ['Success rate', summary ? (summary.successRate == null ? 'N/A' : `${summary.successRate}%`) : null],
    ['Active now', summary?.active],
  ]
  return <div className="profile-stats" aria-label="Delivery record">
    {items.map(([label, value]) => <div key={label}><strong>{value ?? '—'}</strong><span>{label}</span></div>)}
  </div>
}

export default function CourierProfilePage() {
  const { user } = useAuth()
  const resource = useCourierProfile()
  const deliveries = useCourierDeliveries('all')
  const [editing, setEditing] = useState(false)
  const [saved, setSaved] = useState(false)
  const profile = resource.data

  return <section className="courier-page courier-profile-page">
    <PageHeader title="Profile" description="Your courier details, vehicle and availability." />
    {saved && <Alert variant="success" onDismiss={() => setSaved(false)}>Your courier profile has been updated.</Alert>}
    {resource.loading && !profile && <div role="status" className="courier-loading"><LoadingSpinner /><span>Loading your profile...</span></div>}
    {resource.error && <Alert variant="error"><p>{courierError(resource.error, 'profile')}</p><Button variant="outline" size="sm" onClick={resource.reload}>Try again</Button></Alert>}
    {profile && <>
      <section className="courier-panel profile-hero" aria-label="Courier summary">
        <div className="profile-identity">
          <span className="profile-avatar" aria-hidden="true">{initials(profile.name)}</span>
          <div>
            <h2>{profile.name}</h2>
            <p>{[vehicleLabel(profile.vehicleType), profile.area].filter(Boolean).join(' courier · ')}</p>
            <div className="profile-tags">
              <span className={`profile-tag ${profile.active ? 'is-active' : 'is-inactive'}`}>{profile.active ? 'Active' : 'Inactive'}</span>
              <span className={`profile-tag is-${profile.availability?.toLowerCase()}`}><span className="profile-dot" aria-hidden="true" />{title(profile.availability)}</span>
              {profile.createdAt && <span className="profile-since">Courier since {dateLabel(profile.createdAt, { month: 'long', year: 'numeric' })}</span>}
            </div>
          </div>
        </div>
        {!editing && profile.active && <Button variant="outline" onClick={() => { setEditing(true); setSaved(false) }}>Edit profile</Button>}
      </section>

      <Stats deliveries={deliveries} />

      {editing
        ? <ProfileEditForm profile={profile} onCancel={() => setEditing(false)} onSaved={() => { setEditing(false); setSaved(true); resource.reload() }} />
        : <>
          <ProfileDetails profile={profile} email={user?.email} />
          <AvailabilityPicker profile={profile} onChanged={resource.reload} />
          {profile.updatedAt && <p className="courier-help profile-updated">Last updated {dateLabel(profile.updatedAt, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })}</p>}
        </>}
    </>}
  </section>
}
