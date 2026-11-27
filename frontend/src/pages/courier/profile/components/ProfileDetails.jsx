import CourierIcon from '../../../../components/courier/CourierIcon'
import { vehicleLabel } from '../../../../utils/courier/courierValidation'

function Card({ title, icon, children }) {
  return <section className="courier-panel profile-card" aria-label={title}>
    <header className="profile-card-heading"><span className="profile-card-icon"><CourierIcon name={icon} size={16} /></span><h2>{title}</h2></header>
    <dl className="profile-facts">{children}</dl>
  </section>
}
const Fact = ({ label, children }) => <div><dt>{label}</dt><dd>{children || <span className="profile-muted">Not provided</span>}</dd></div>

export default function ProfileDetails({ profile, email }) {
  const phone = profile.phone?.replace(/[^+0-9]/g, '')
  return <div className="profile-cards">
    <Card title="Contact details" icon="person">
      <Fact label="Email">{email}</Fact>
      <Fact label="Phone">{phone ? <a href={`tel:${phone}`}>{profile.phone}</a> : null}</Fact>
      <Fact label="Home area">{profile.area}</Fact>
    </Card>
    <Card title="Vehicle" icon="box">
      <Fact label="Type">{vehicleLabel(profile.vehicleType)}</Fact>
      <Fact label="Registration">{profile.vehicleNumber ? <span className="profile-plate">{profile.vehicleNumber}</span> : null}</Fact>
    </Card>
  </div>
}
