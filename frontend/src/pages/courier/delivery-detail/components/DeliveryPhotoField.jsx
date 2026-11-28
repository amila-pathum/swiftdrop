import { useEffect, useMemo, useRef, useState } from 'react'
import CourierIcon from '../../../../components/courier/CourierIcon'

const sizeLabel = (bytes) => bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`

// Optional photo for any status update; it is uploaded to private storage when the update is sent.
export default function DeliveryPhotoField({ photo, onPhoto, error, disabled }) {
  const input = useRef(null)
  const [dragging, setDragging] = useState(false)
  const preview = useMemo(() => (photo && typeof URL.createObjectURL === 'function' ? URL.createObjectURL(photo) : null), [photo])
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

  const choose = () => input.current?.click()
  const remove = () => { onPhoto(null); if (input.current) input.current.value = '' }
  const drop = (event) => {
    event.preventDefault()
    setDragging(false)
    if (!disabled && event.dataTransfer.files?.[0]) onPhoto(event.dataTransfer.files[0])
  }

  return <div className="form-field photo-field">
    <label htmlFor="delivery-photo" className="photo-field-label">Photo <span>(optional)</span></label>
    <input ref={input} id="delivery-photo" className="visually-hidden" type="file" tabIndex={-1}
      accept=".jpg,.png,image/jpeg,image/png" disabled={disabled}
      onChange={(event) => onPhoto(event.target.files?.[0] || null)} aria-describedby="delivery-photo-help" aria-invalid={Boolean(error)} />
    {photo ? (
      <div className={`photo-field-selected${error ? ' has-error' : ''}`}>
        {preview ? <img src={preview} alt="" className="photo-field-thumb" /> : <span className="photo-field-thumb photo-field-thumb-empty"><CourierIcon name="camera" size={20} /></span>}
        <div className="photo-field-meta">
          <strong title={photo.name}>{photo.name}</strong>
          <span>{sizeLabel(photo.size)}</span>
        </div>
        <div className="photo-field-actions">
          <button type="button" onClick={choose} disabled={disabled}>Change</button>
          <button type="button" onClick={remove} disabled={disabled} className="is-danger">Remove</button>
        </div>
      </div>
    ) : (
      <div className={`photo-field-drop${dragging ? ' is-dragging' : ''}${error ? ' has-error' : ''}`}
        onDragOver={(event) => { event.preventDefault(); if (!disabled) setDragging(true) }}
        onDragLeave={() => setDragging(false)} onDrop={drop}>
        <span className="photo-field-icon"><CourierIcon name="camera" size={20} /></span>
        <div className="photo-field-meta">
          <strong>Add a photo</strong>
          <span>Drag an image here or choose one</span>
        </div>
        <button type="button" className="photo-field-choose" onClick={choose} disabled={disabled}>Choose photo</button>
      </div>
    )}
    <p id="delivery-photo-help" className="courier-help">JPG or PNG, up to 5 MB. Your dispatcher can see it.</p>
    {error && <p className="form-error" role="alert">{error}</p>}
  </div>
}
