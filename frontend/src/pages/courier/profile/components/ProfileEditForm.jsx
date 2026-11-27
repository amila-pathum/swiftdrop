import Button from '../../../../components/Button'
import FormField from '../../../../components/FormField'
import Alert from '../../../../components/Alert'
import { useForm } from '../../../../hooks/useForm'
import { courierService } from '../../../../services/courierService'
import { VEHICLES, validateProfile } from '../../../../utils/courier/courierValidation'
import { courierError } from '../../../../utils/courier/deliveryStatus'

export default function ProfileEditForm({ profile, onSaved, onCancel }) {
  const form = useForm({ initialValues: Object.fromEntries(['name', 'phone', 'vehicleType', 'vehicleNumber', 'area'].map((name) => [name, profile[name] || ''])), validate: validateProfile, idPrefix: 'courier-profile' })
  return <section className="courier-panel profile-edit" aria-labelledby="profile-edit-title">
    <header className="courier-panel-heading"><h2 id="profile-edit-title">Edit profile</h2></header>
    <form className="courier-panel-body" onSubmit={form.handleSubmit(async (values) => {
      try { await courierService.updateProfile(Object.fromEntries(Object.entries(values).map(([key, value]) => [key, value.trim()]))); onSaved() }
      catch (error) { form.setFormError(courierError(error, 'profile')) }
    })} noValidate>
      {form.formError && <Alert variant="error">{form.formError}</Alert>}
      <fieldset disabled={form.submitting} className="profile-edit-section">
        <legend>Contact</legend>
        <p className="courier-help">Your email is managed by your account and cannot be changed here.</p>
        <div className="courier-form-grid">
          <FormField label="Name" required maxLength={100} {...form.field('name')} />
          <FormField label="Phone" required type="tel" maxLength={30} {...form.field('phone')} />
          <FormField label="Home area" required maxLength={100} hint="The area you usually deliver in." {...form.field('area')} />
        </div>
      </fieldset>
      <fieldset disabled={form.submitting} className="profile-edit-section">
        <legend>Vehicle</legend>
        <div className="courier-form-grid">
          <FormField label="Vehicle type" required as="select" options={VEHICLES} {...form.field('vehicleType')} />
          <FormField label="Vehicle number" required maxLength={30} hint="As shown on the number plate." {...form.field('vehicleNumber')} />
        </div>
      </fieldset>
      <div className="profile-edit-actions">
        <Button variant="outline" disabled={form.submitting} onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={form.submitting} loadingText="Saving...">Save changes</Button>
      </div>
    </form>
  </section>
}
