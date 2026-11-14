import FormField from '../../../../components/FormField'
import { FAILURE_REASONS } from '../../../../utils/courier/courierValidation'
export default function FailedDeliveryForm({ field, disabled }) {
  return <FormField label="Failure reason" as="select" options={FAILURE_REASONS} required {...field} disabled={disabled} />
}
