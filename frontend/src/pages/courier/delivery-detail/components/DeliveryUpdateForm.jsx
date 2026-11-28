import { useState } from 'react'
import Button from '../../../../components/Button'
import Alert from '../../../../components/Alert'
import FormField from '../../../../components/FormField'
import { useForm } from '../../../../hooks/useForm'
import { useDeliverySubmission } from '../../../../hooks/courier/useDeliverySubmission'
import { STATUS } from '../../../../utils/courier/deliveryStatus'
import { validateDelivery } from '../../../../utils/courier/courierValidation'
import DeliveryPhotoField from './DeliveryPhotoField'
import DeliveryProofForm from './DeliveryProofForm'
import FailedDeliveryForm from './FailedDeliveryForm'

export default function DeliveryUpdateForm({ parcel, onConfirmed }) {
  const [status, setStatus] = useState(STATUS[parcel.status]?.next || 'DELIVERED')
  const [photo, setPhoto] = useState(null)
  const submission = useDeliverySubmission(parcel.id, onConfirmed)
  const form = useForm({ initialValues: { recipientName: '', failureReason: '', note: '' }, validate: (values) => validateDelivery(values, status, photo), idPrefix: 'delivery-update' })
  const actions = STATUS[parcel.status]?.next ? [STATUS[parcel.status].next] : parcel.status === 'OUT_FOR_DELIVERY' ? ['DELIVERED', 'FAILED'] : []
  if (!actions.length) return null
  return <section className="courier-panel"><header className="courier-panel-heading"><h2>Update delivery</h2></header><div className="courier-panel-body"><form className="courier-form" onSubmit={form.handleSubmit(async (values) => { await submission.submit({ status, recipientName: status === 'DELIVERED' ? values.recipientName.trim() || null : null, failureReason: status === 'FAILED' ? values.failureReason : null, note: values.note.trim() || null }, photo) })} noValidate>
    <FormField id="delivery-outcome" label="Update" as="select" value={status} onChange={(event) => { if (actions.includes(event.target.value)) setStatus(event.target.value) }} options={actions.map((value) => ({ value, label: STATUS[value].label }))} disabled={submission.locked} />
    {status === 'DELIVERED' && <DeliveryProofForm field={form.field('recipientName')} disabled={submission.locked} />}
    {status === 'FAILED' && <FailedDeliveryForm field={form.field('failureReason')} disabled={submission.locked} />}
    <FormField label="Note" as="textarea" {...form.field('note')} maxLength={1000} disabled={submission.locked} />
    <DeliveryPhotoField photo={photo} onPhoto={setPhoto} error={form.errors.photo} disabled={submission.locked} />
    {submission.message && <Alert variant={submission.phase === 'error' ? 'error' : submission.phase === 'confirmed' ? 'success' : 'info'}>{submission.message}</Alert>}
    {submission.phase === 'pending' ? <div className="courier-actions"><Button type="button" variant="outline" onClick={submission.check}>Check confirmation</Button><Button type="button" variant="outline" onClick={submission.retry}>Retry same update</Button></div> : <Button type="submit" loading={submission.phase === 'submitting'} disabled={submission.locked}>Confirm {STATUS[status].label.toLowerCase()}</Button>}
  </form></div></section>
}
