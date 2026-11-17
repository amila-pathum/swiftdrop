import { useState } from 'react'
import PageHeader from '../../../components/PageHeader'
import Alert from '../../../components/Alert'
import Button from '../../../components/Button'
import LoadingSpinner from '../../../components/LoadingSpinner'
import AvailabilityControl from '../../../components/courier/AvailabilityControl'
import { useAuth } from '../../../hooks/useAuth'
import { useCourierProfile } from '../../../hooks/courier/useCourierProfile'
import { courierError } from '../../../utils/courier/deliveryStatus'
import ProfileDetails from './components/ProfileDetails'
import ProfileEditForm from './components/ProfileEditForm'
import '../../../components/courier/courier.css'
import './courierProfile.css'

export default function CourierProfilePage() {
  const { user } = useAuth()
  const resource = useCourierProfile()
  const [editing, setEditing] = useState(false)
  const [saved, setSaved] = useState(false)
  return <section className="courier-page courier-profile-page">
    <PageHeader title="Profile" description="Your courier contact details, vehicle and availability." />
    {saved && <Alert variant="success" onDismiss={() => setSaved(false)}>Your courier profile has been updated.</Alert>}
    {resource.loading && <div role="status" className="courier-loading"><LoadingSpinner /><span>Loading your profile...</span></div>}
    {resource.error && <Alert variant="error"><p>{courierError(resource.error, 'profile')}</p><Button variant="outline" size="sm" onClick={resource.reload}>Try again</Button></Alert>}
    {resource.data && <>
      <section className="courier-panel"><header className="courier-panel-heading"><h2>{resource.data.name}</h2>{!editing && resource.data.active && <Button variant="outline" size="sm" onClick={() => { setEditing(true); setSaved(false) }}>Edit profile</Button>}</header><div className="courier-panel-body">
        {editing ? <ProfileEditForm profile={resource.data} onCancel={() => setEditing(false)} onSaved={() => { setEditing(false); setSaved(true); resource.reload() }} /> : <ProfileDetails profile={resource.data} email={user?.email} />}
      </div></section>
      {!editing && <div className="courier-profile-availability"><AvailabilityControl profile={resource.data} onChanged={resource.reload} /><p className="courier-help">Set Offline when you are not taking new deliveries. Your dispatcher manages assignments.</p></div>}
    </>}
  </section>
}
