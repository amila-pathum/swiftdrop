import { useState } from 'react'

/**
 * A delivery photo from a short-lived signed URL. Clicking opens it full size in
 * a new tab. If the link has expired, reloading the page gets a fresh one.
 */
export default function DeliveryPhoto({ url, alt = 'Delivery photo', className = 'delivery-photo' }) {
  const [failed, setFailed] = useState(false)
  if (!url) return null
  if (failed) return <p className="delivery-photo-error">Photo unavailable. Refresh the page to load it again.</p>
  return <a className={className} href={url} target="_blank" rel="noopener noreferrer" title="Open full size">
    <img src={url} alt={alt} loading="lazy" onError={() => setFailed(true)} />
  </a>
}
