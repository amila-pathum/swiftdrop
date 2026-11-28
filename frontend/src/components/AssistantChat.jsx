import { Fragment, useEffect, useId, useRef, useState } from 'react'
import { apiClient } from '../services/apiClient'
import './AssistantChat.css'

const estimateFee = { label: 'Estimate delivery fee', message: 'Help me estimate a delivery fee.' }
const weeklyReport = { label: 'Weekly delivery report', message: 'How many deliveries succeeded or failed in the last 7 days?' }
const roleSuggestions = {
  CUSTOMER: [
    { label: 'Track a parcel', message: 'Help me track a parcel.' },
    estimateFee,
    { label: 'Book a delivery', message: 'How do I book a new delivery on the site?' },
  ],
  MERCHANT: [
    { label: 'Find an order', message: 'How do I find an order in my merchant dashboard?' },
    { label: 'Generate an API key', message: 'How do I generate a merchant API key?' },
    estimateFee,
  ],
  COURIER: [
    { label: 'Today’s deliveries', message: 'Where can I see my assigned deliveries for today?' },
    { label: 'Update delivery status', message: 'How do I update the status of an assigned delivery?' },
  ],
  DISPATCHER: [
    { label: 'Check courier workload', message: 'How many parcels are waiting and how many active deliveries does each courier have?' },
    { label: 'Assign a courier', message: 'How do I assign a waiting parcel to a courier?' },
    weeklyReport,
  ],
  ADMIN: [
    { label: 'Check pending approvals', message: 'How many merchant and courier applications are pending approval?' },
    weeklyReport,
    estimateFee,
  ],
}

function BotMark() {
  return (
    <svg className="assistant-mark" width="32" height="32" viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="10" fill="currentColor" />
      <g stroke="white" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" fill="none">
        <path d="M16 6v4m-9 6H5m22 0h-2" />
        <path d="M11 10h10a4 4 0 0 1 4 4v5a4 4 0 0 1-4 4h-6l-4 3v-3a4 4 0 0 1-4-4v-5a4 4 0 0 1 4-4Z" />
        <path d="M12 15v2m8-2v2m-6 3h4" />
      </g>
    </svg>
  )
}

export default function AssistantChat({ role = 'CUSTOMER', suggestions = roleSuggestions[role] }) {
  const [open, setOpen] = useState(false)
  const [turns, setTurns] = useState([])
  const [draft, setDraft] = useState('')
  const [pending, setPending] = useState(null)
  const [error, setError] = useState('')
  const controller = useRef(null)
  const launcher = useRef(null)
  const restoreFocus = useRef(false)
  const panel = useRef(null)
  const log = useRef(null)
  const id = useId()

  useEffect(() => () => controller.current?.abort(), [])
  useEffect(() => {
    if (open) panel.current?.focus()
    else if (restoreFocus.current) {
      launcher.current?.focus()
      restoreFocus.current = false
    }
  }, [open])
  useEffect(() => {
    if (open && log.current) log.current.scrollTop = log.current.scrollHeight
  }, [open, turns, pending, error])

  async function send(text) {
    const message = text.trim()
    if (!message || controller.current) return
    const active = new AbortController()
    controller.current = active
    setError('')
    setPending(message)
    if (message !== pending) setDraft('')
    try {
      const history = turns.slice(-8).map(({ message, reply }) => ({
        message, reply: reply.slice(0, 8000),
      }))
      const data = await apiClient.post('/api/ai/chat', { message, history }, {
        auth: true, signal: active.signal,
      })
      if (typeof data?.reply !== 'string' || !data.reply.trim()) throw new Error('Empty reply')
      if (!active.signal.aborted) {
        setTurns(previous => [...previous, { message, reply: data.reply }])
        setPending(null)
      }
    } catch {
      if (!active.signal.aborted) setError('Couldn’t get a reply.')
    } finally {
      controller.current = null
    }
  }

  const busy = pending !== null && !error
  return (
    <div className="assistant-chat">
      {open && (
        <section className="assistant-panel" id={id} ref={panel} tabIndex={-1}
          role="dialog" aria-label="SwiftDrop Assistant"
          onKeyDown={event => { if (event.key === 'Escape') setOpen(false) }}>
          <header className="assistant-header">
            <BotMark />
            <strong>SwiftDrop Assistant</strong>
            <button type="button" aria-label="Minimize assistant" onClick={() => setOpen(false)}>
              <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
                <path d="M4 10h10" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </button>
          </header>
          <div className="assistant-log" role="log" aria-label="Conversation" ref={log}>
            <p className="assistant-bubble">How can I help?</p>
            {turns.length === 0 && pending === null && (
              <div className="assistant-suggestions">
                {suggestions.map(({ label, message }) => (
                  <button key={label} type="button" onClick={() => send(message)}>{label}</button>
                ))}
              </div>
            )}
            {turns.map((turn, index) => (
              <Fragment key={index}>
                <p className="assistant-bubble outgoing">{turn.message}</p>
                <p className="assistant-bubble">{turn.reply}</p>
              </Fragment>
            ))}
            {pending && <p className="assistant-bubble outgoing">{pending}</p>}
            {busy && <p className="assistant-bubble" role="status">Thinking…</p>}
            {error && <p className="assistant-error">{error} <button type="button" onClick={() => send(pending)}>Try again</button></p>}
          </div>
          <form className="assistant-form" onSubmit={event => { event.preventDefault(); send(draft) }}>
            <input aria-label="Message SwiftDrop Assistant" placeholder="Ask SwiftDrop Assistant…"
              maxLength={2000} value={draft} onChange={event => setDraft(event.target.value)} />
            <button type="submit" disabled={busy || !draft.trim()} aria-label="Send message">
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M12 19V5m-6 6 6-6 6 6" />
              </svg>
            </button>
          </form>
        </section>
      )}
      <button className="assistant-launcher" type="button" hidden={open} ref={launcher}
        aria-expanded={open} aria-controls={open ? id : undefined}
        onClick={() => { restoreFocus.current = true; setOpen(true) }}>
        <BotMark /> Ask assistant
      </button>
    </div>
  )
}
