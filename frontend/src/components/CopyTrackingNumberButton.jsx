import { useEffect, useRef, useState } from 'react'

export default function CopyTrackingNumberButton({ trackingNumber, className = '' }) {
  const [state, setState] = useState('idle')
  const resetTimer = useRef(null)

  useEffect(() => () => clearTimeout(resetTimer.current), [])

  const copy = async () => {
    if (!trackingNumber || state === 'copying') return
    setState('copying')
    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error('Clipboard unavailable')
      }
      await navigator.clipboard.writeText(trackingNumber)
      setState('copied')
    } catch {
      setState('error')
    }
    clearTimeout(resetTimer.current)
    resetTimer.current = setTimeout(() => setState('idle'), 2500)
  }

  const label = state === 'copied'
    ? 'Copied!'
    : state === 'error'
      ? 'Copy failed'
      : state === 'copying'
        ? 'Copying…'
        : 'Copy tracking number'

  return (
    <button
      type="button"
      className={`tracking-copy-button ${className}`.trim()}
      onClick={copy}
      disabled={!trackingNumber || state === 'copying'}
      aria-label={label}
    >
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
        <rect x="9" y="9" width="13" height="13" rx="2" />
        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
      </svg>
      <span aria-live="polite">{label}</span>
    </button>
  )
}
