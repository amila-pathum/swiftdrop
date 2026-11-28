import { useEffect, useRef, useState } from 'react'

/** A compact "View photo" button that opens a delivery photo in an overlay. */
export default function PhotoViewerButton({ url, title = 'Delivery photo', label = 'View photo' }) {
  const [open, setOpen] = useState(false)
  const [failed, setFailed] = useState(false)
  const opener = useRef(null)
  const closer = useRef(null)

  useEffect(() => {
    if (!open) return undefined
    const button = opener.current
    closer.current?.focus()
    const onKey = (event) => { if (event.key === 'Escape') setOpen(false) }
    window.addEventListener('keydown', onKey)
    return () => { window.removeEventListener('keydown', onKey); button?.focus() }
  }, [open])

  if (!url) return null
  return <>
    <button ref={opener} type="button" className="photo-view-button" onClick={() => { setFailed(false); setOpen(true) }}>
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="3" y="4" width="18" height="16" rx="2" /><circle cx="9" cy="10" r="2" /><path d="m21 17-5-5-8 8" />
      </svg>
      {label}
    </button>
    {open && <div className="photo-viewer-backdrop" onClick={(event) => { if (event.target === event.currentTarget) setOpen(false) }}>
      <figure className="photo-viewer" role="dialog" aria-modal="true" aria-label={title}>
        <figcaption>
          <span>{title}</span>
          <span className="photo-viewer-actions">
            {!failed && <a href={url} target="_blank" rel="noopener noreferrer">Open full size</a>}
            <button ref={closer} type="button" aria-label="Close photo" onClick={() => setOpen(false)}>×</button>
          </span>
        </figcaption>
        {failed
          ? <p className="photo-viewer-error">The photo link has expired. Refresh the page to load it again.</p>
          : <img src={url} alt={title} onError={() => setFailed(true)} />}
      </figure>
    </div>}
  </>
}
