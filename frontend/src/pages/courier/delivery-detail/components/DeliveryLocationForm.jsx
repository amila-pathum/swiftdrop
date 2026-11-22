import FormField from '../../../../components/FormField'

// GPS capture was removed for now; location will come from the Google Maps integration later.
export default function DeliveryLocationForm({ field, disabled }) {
  return <section className="courier-form"><h3>Collection / delivery location (optional)</h3><FormField label="Location description" as="textarea" {...field} disabled={disabled} hint="Optional. Describe where the parcel was collected or delivered." /></section>
}
