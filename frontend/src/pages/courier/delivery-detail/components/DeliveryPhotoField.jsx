// Optional photo for any status update; it is uploaded to private storage when the update is sent.
export default function DeliveryPhotoField({ photo, onPhoto, error, disabled }) {
  return <div className="form-field">
    <label htmlFor="delivery-photo">Photo (optional)</label>
    <input id="delivery-photo" type="file" accept=".jpg,.png,image/jpeg,image/png" disabled={disabled}
      onChange={(event) => onPhoto(event.target.files?.[0] || null)} aria-describedby="delivery-photo-help" aria-invalid={Boolean(error)} />
    <p id="delivery-photo-help" className="courier-help">JPG or PNG, up to 5 MB. Your dispatcher can see it. {photo?.name}</p>
    {error && <p className="form-error" role="alert">{error}</p>}
  </div>
}
