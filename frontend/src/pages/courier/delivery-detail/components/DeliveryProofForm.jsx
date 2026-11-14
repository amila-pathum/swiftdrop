import FormField from '../../../../components/FormField'
export default function DeliveryProofForm({ field, photo, onPhoto, error, disabled }) {
  return <><FormField label="Recipient name" {...field} disabled={disabled} maxLength={150} hint="Required when no proof photo is uploaded." /><div className="form-field"><label htmlFor="delivery-photo">Proof photo</label><input id="delivery-photo" type="file" accept=".jpg,.png,image/jpeg,image/png" disabled={disabled} onChange={(event) => onPhoto(event.target.files?.[0] || null)} aria-describedby="delivery-photo-help" aria-invalid={Boolean(error)} /><p id="delivery-photo-help" className="courier-help">JPG or PNG, up to 5 MB. {photo?.name}</p>{error && <p className="form-error" role="alert">{error}</p>}</div></>
}
