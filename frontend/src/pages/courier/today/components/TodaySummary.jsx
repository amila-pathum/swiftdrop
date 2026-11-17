export default function TodaySummary({ deliveries }) {
  const active = deliveries?.filter(({ assignment, parcel }) => assignment.active && !['DELIVERED', 'FAILED', 'CANCELLED'].includes(parcel.status)).length
  const delivered = deliveries?.filter(({ parcel }) => parcel.status === 'DELIVERED').length
  const failed = deliveries?.filter(({ parcel }) => parcel.status === 'FAILED').length
  return <>
    <p className="courier-assignment-notice">{deliveries ? <>You have <strong>{deliveries.length}</strong> assigned {deliveries.length === 1 ? 'delivery' : 'deliveries'} today.</> : 'Your delivery summary will appear when assignments are available.'}</p>
    <div className="courier-summary-grid" aria-label="Today's delivery summary">{[['Active', active, ''], ['Delivered', delivered, 'success'], ['Failed', failed, 'error']].map(([label, count, tone]) => <div className={`courier-summary-card ${tone ? `courier-text-${tone}` : ''}`} key={label}><span>{label}</span><strong aria-label={count == null ? `${label} count unavailable` : undefined}>{count ?? '—'}</strong></div>)}</div>
  </>
}
