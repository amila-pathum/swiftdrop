import { useEffect, useRef, useState } from 'react'

export default function CopyTrackingNumberButton({ trackingNumber, className = '' }) {
  const [state, setState] = useState('idle')
  const resetTimer = useRef(null)

  useEffect(() => () => clearTimeout(resetTimer.current), [])

  const copy = async () => {
    if (!trackingNumber || state === 'copying') return
    setState('copying')
    let success = false

    // Primary path: synchronous execCommand — reliable across all browsers and
    // origins including http://localhost (no permission prompt, no silent failure).
    try {
      const textarea = document.createElement('textarea')
      textarea.value = trackingNumber
      textarea.setAttribute('readonly', '')
      textarea.style.cssText = 'position:fixed;left:-9999px;top:-9999px;opacity:0'
      document.body.appendChild(textarea)
      textarea.focus()
      textarea.select()
      success = document.execCommand('copy')
      document.body.removeChild(textarea)
    } catch {
      success = false
    }

    // Secondary path: modern Clipboard API (future-proof; also works in
    // secure contexts where execCommand may eventually be removed).
    if (!success && typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      try {
        await navigator.clipboard.writeText(trackingNumber)
        success = true
      } catch {
        // Both paths failed — show error state below
      }
    }

    if (success) {
      setState('copied')
    } else {
      setState('error')
    }
    clearTimeout(resetTimer.current)
    resetTimer.current = setTimeout(() => setState('idle'), 2500)
  }

  const tooltipLabel = state === 'copied'
    ? 'Copied!'
    : state === 'error'
      ? 'Copy failed'
      : state === 'copying'
        ? 'Copying…'
        : 'Copy tracking number'

  // Icon changes to a checkmark when copied successfully
  const icon = state === 'copied'
    ? (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <polyline points="20 6 9 17 4 12" />
      </svg>
    )
    : (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
        <rect x="9" y="9" width="13" height="13" rx="2" />
        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
      </svg>
    )

  return (
    <button
      type="button"
      className={`tracking-copy-button tracking-copy-button--icon-only ${state === 'copied' ? 'tracking-copy-button--copied' : ''} ${className}`.trim()}
      onClick={copy}
      disabled={!trackingNumber || state === 'copying'}
      aria-label={tooltipLabel}
      data-tooltip={tooltipLabel}
    >
      {icon}
    </button>
  )
}
