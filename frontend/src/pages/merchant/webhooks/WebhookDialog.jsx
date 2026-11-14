import { useEffect, useRef } from 'react'
import Button from '../../../components/Button'

export default function WebhookDialog({ title, onClose, busy = false, children, footer }) {
  const dialogRef = useRef(null)
  useEffect(() => {
    const dialog = dialogRef.current
    dialog.showModal()
    return () => dialog.close()
  }, [])

  return <dialog ref={dialogRef} className="webhook-dialog" aria-labelledby="webhook-dialog-title"
    onCancel={(event) => { event.preventDefault(); if (!busy) onClose() }}>
    <div className="webhook-dialog-header">
      <h2 id="webhook-dialog-title">{title}</h2>
      <Button variant="ghost" onClick={onClose} disabled={busy} aria-label="Close dialog">×</Button>
    </div>
    <div className="webhook-dialog-body">{children}</div>
    {footer && <div className="webhook-dialog-footer">{footer}</div>}
  </dialog>
}
