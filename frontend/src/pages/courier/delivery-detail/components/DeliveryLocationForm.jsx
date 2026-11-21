import { useState } from 'react'
import Button from '../../../../components/Button'
import FormField from '../../../../components/FormField'

export default function DeliveryLocationForm({ field, location, onLocation, disabled }) {
  const [error, setError] = useState('')
  const [capturing, setCapturing] = useState(false)
  function capture() {
    if (!navigator.geolocation) { setError('Location capture is unavailable. Enter the location below.'); return }
    setCapturing(true)
    setError('')
    navigator.geolocation.getCurrentPosition(({ coords, timestamp }) => {
      onLocation({ latitude: Number(coords.latitude.toFixed(6)), longitude: Number(coords.longitude.toFixed(6)), accuracyMeters: Number(coords.accuracy.toFixed(2)), capturedAt: new Date(Math.min(timestamp, Date.now())).toISOString() })
      setCapturing(false)
    }, () => { setError('Unable to capture your location. Describe the location below instead.'); setCapturing(false) }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 })
  }
  return <section className="courier-form"><h3>Collection / delivery location</h3><div className="courier-actions"><Button type="button" variant="outline" disabled={disabled || capturing} onClick={capture}>{capturing ? 'Capturing location...' : 'Use current location'}</Button>{location && <><span role="status">Location captured</span><Button type="button" variant="ghost" disabled={disabled} onClick={() => onLocation(null)}>Remove location</Button></>}</div>{error && <p role="status" className="courier-help">{error}</p>}<FormField label="Location description" as="textarea" {...field} disabled={disabled} hint="Describe the collecting or delivered place. Required for pickup, delivery or failure when GPS is unavailable." /></section>
}
