import Button from '../../../../components/Button'
import FormField from '../../../../components/FormField'
import Alert from '../../../../components/Alert'
import { useForm } from '../../../../hooks/useForm'
import { courierService } from '../../../../services/courierService'
import { VEHICLES, validateProfile } from '../../../../utils/courier/courierValidation'
import { courierError } from '../../../../utils/courier/deliveryStatus'

export default function ProfileEditForm({ profile, onSaved, onCancel }) {
  const form = useForm({ initialValues: Object.fromEntries(['name', 'phone', 'vehicleType', 'vehicleNumber', 'area'].map((name) => [name, profile[name] || ''])), validate: validateProfile, idPrefix: 'courier-profile' })
  return <form onSubmit={form.handleSubmit(async (values) => {
    try { await courierService.updateProfile(Object.fromEntries(Object.entries(values).map(([key, value]) => [key, value.trim()]))); onSaved() }
    catch (error) { form.setFormError(courierError(error, 'profile')) }
  })} noValidate>
    {form.formError && <Alert variant="error">{form.formError}</Alert>}
    <fieldset disabled={form.submitting} className="courier-form-grid">
      <FormField label="Name" required maxLength={100} {...form.field('name')} />
      <FormField label="Phone" required type="tel" maxLength={30} {...form.field('phone')} />
      <FormField label="Home area" required maxLength={100} {...form.field('area')} />
      <FormField label="Vehicle type" required as="select" options={VEHICLES} {...form.field('vehicleType')} />
      <FormField label="Vehicle number" required maxLength={30} {...form.field('vehicleNumber')} />
    </fieldset>
    <div className="courier-form-actions"><Button type="submit" loading={form.submitting} loadingText="Saving...">Save changes</Button><Button variant="outline" disabled={form.submitting} onClick={onCancel}>Cancel</Button></div>
  </form>
}
