import { useEffect, useRef, useState } from 'react'
import { getParcelLiveStatusStreamUrl } from '../services/notificationService'

/**
 * Reusable hook that connects to a parcel's public SSE live status stream.
 * Automatically updates status in real time and reconnects if connection drops,
 * retaining the last known status.
 *
 * @param {string} trackingNumber The tracking number to monitor.
 * @param {string} [initialStatus] Optional initial status before stream connects.
 * @returns {{
 *   status: string|null,
 *   updatedAt: string|null,
 *   isConnected: boolean,
 *   error: string|null,
 *   lastEvent: object|null
 * }}
 */
export function useParcelLiveStatus(trackingNumber, initialStatus = null) {
  const [status, setStatus] = useState(initialStatus)
  const [updatedAt, setUpdatedAt] = useState(null)
  const [isConnected, setIsConnected] = useState(false)
  const [error, setError] = useState(null)
  const [lastEvent, setLastEvent] = useState(null)

  const eventSourceRef = useRef(null)
  const reconnectTimeoutRef = useRef(null)
  const isMountedRef = useRef(true)

  useEffect(() => {
    isMountedRef.current = true

    if (!trackingNumber || typeof trackingNumber !== 'string' || !trackingNumber.trim()) {
      setIsConnected(false)
      setError(null)
      return undefined
    }

    const trimmedNumber = trackingNumber.trim()
    let backoffDelay = 3000

    function connect() {
      if (!isMountedRef.current) return

      try {
        const streamUrl = getParcelLiveStatusStreamUrl(trimmedNumber)
        const es = new EventSource(streamUrl)
        eventSourceRef.current = es

        es.onopen = () => {
          if (!isMountedRef.current) return
          setIsConnected(true)
          setError(null)
          backoffDelay = 3000 // reset backoff upon successful connection
        }

        // Handle named 'status' event
        es.addEventListener('status', (e) => {
          if (!isMountedRef.current) return
          try {
            const data = JSON.parse(e.data)
            if (data.status) {
              setStatus(data.status)
            }
            if (data.updatedAt) {
              setUpdatedAt(data.updatedAt)
            }
            setLastEvent(data)
          } catch {
            // Ignore malformed JSON
          }
        })

        // Also handle generic messages if status arrives on default event
        es.onmessage = (e) => {
          if (!isMountedRef.current) return
          try {
            const data = JSON.parse(e.data)
            if (data.status) {
              setStatus(data.status)
            }
            if (data.updatedAt) {
              setUpdatedAt(data.updatedAt)
            }
            setLastEvent(data)
          } catch {
            // Heartbeats or ping events
          }
        }

        es.onerror = () => {
          if (!isMountedRef.current) return
          setIsConnected(false)
          setError('Live tracking connection dropped. Reconnecting...')
          es.close()

          // Schedule automatic reconnect with exponential backoff (capped at 15s)
          reconnectTimeoutRef.current = setTimeout(() => {
            backoffDelay = Math.min(backoffDelay * 1.5, 15000)
            connect()
          }, backoffDelay)
        }
      } catch (err) {
        if (!isMountedRef.current) return
        setIsConnected(false)
        setError(err.message || 'Failed to establish live status connection')
      }
    }

    connect()

    return () => {
      isMountedRef.current = false
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current)
      }
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
        eventSourceRef.current = null
      }
    }
  }, [trackingNumber])

  return {
    status,
    updatedAt,
    isConnected,
    error,
    lastEvent,
  }
}

