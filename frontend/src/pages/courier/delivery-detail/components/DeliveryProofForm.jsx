import FormField from '../../../../components/FormField'
export default function DeliveryProofForm({ field, disabled }) {
  return <FormField label="Recipient name" {...field} disabled={disabled} maxLength={150} required hint="Who received the parcel." />
}
