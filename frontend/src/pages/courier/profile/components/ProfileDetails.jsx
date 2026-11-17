import { VEHICLES } from '../../../../utils/courier/courierValidation'

export default function ProfileDetails({ profile, email }) {
  const rows = [
    ['Email', email || 'Not available'], ['Phone', profile.phone], ['Home area', profile.area],
    ['Vehicle', VEHICLES.find(({ value }) => value === profile.vehicleType)?.label || profile.vehicleType],
    ['Vehicle number', profile.vehicleNumber], ['Availability', profile.availability.charAt(0) + profile.availability.slice(1).toLowerCase()],
    ['Profile status', profile.active ? 'Active' : 'Inactive'],
  ]
  return <dl className="courier-profile-details">{rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || 'Not provided'}</dd></div>)}</dl>
}
