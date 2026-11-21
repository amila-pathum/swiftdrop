import { useEffect, useRef, useState } from 'react'
import { courierService } from '../../services/courierService'

// Preserve the exact operation for uncertain responses: retries must be idempotent.
export function useDeliverySubmission(parcelId, onConfirmed) {
  const operation = useRef(null)
  const busy = useRef(false)
  const mounted = useRef(true)
  const confirmed = useRef(onConfirmed)
  const [state, setState] = useState({ phase: 'idle', message: '' })
  useEffect(() => { confirmed.current = onConfirmed }, [onConfirmed])
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  function receive(result) {
    if (!mounted.current) return
    if (result?.syncStatus === 'CONFIRMED') {
      operation.current = null
      setState({ phase: 'confirmed', message: result.message || 'Delivery update confirmed.' })
      confirmed.current()
    } else if (result?.syncStatus === 'REJECTED') {
      operation.current = null
      setState({ phase: 'error', message: result.message || 'The delivery update was rejected. Refresh the delivery before trying again.' })
    } else setState({ phase: 'pending', message: 'Your update is awaiting confirmation. Check its status before retrying.' })
  }
  async function send() {
    if (busy.current || !operation.current) return
    busy.current = true
    setState({ phase: 'submitting', message: 'Sending your delivery update...' })
    const op = operation.current
    try { receive(await courierService.submit(parcelId, op.data, op.photo)) }
    catch (error) {
      if (!mounted.current) return
      const definite = [400, 401, 403, 404, 409, 413, 415, 422].includes(error.status)
      if (definite) operation.current = null
      setState({ phase: definite ? 'error' : 'pending', message: definite ? error.message : 'Confirmation could not be received. Your update may have reached the server. Check its status or retry the same update.' })
    } finally { busy.current = false }
  }
  async function submit(data, photo) {
    if (operation.current || busy.current) return
    operation.current = { data: { ...data, submissionId: crypto.randomUUID() }, photo }
    await send()
  }
  async function check() {
    if (busy.current || !operation.current) return
    busy.current = true
    try { receive(await courierService.getSubmission(operation.current.data.submissionId)) }
    catch (error) { if (mounted.current) setState({ phase: 'pending', message: `${error.message} Your original update is retained for retry.` }) }
    finally { busy.current = false }
  }
  return { ...state, locked: ['submitting', 'pending'].includes(state.phase), submit, check, retry: send }
}
