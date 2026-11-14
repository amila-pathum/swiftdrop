import { renderHook, act } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useParcelLiveStatus } from './useParcelLiveStatus'

class MockEventSource {
  constructor(url) {
    this.url = url
    this.listeners = {}
    MockEventSource.instances.push(this)
    setTimeout(() => {
      if (this.onopen) this.onopen()
    }, 0)
  }

  addEventListener(event, callback) {
    if (!this.listeners[event]) this.listeners[event] = []
    this.listeners[event].push(callback)
  }

  removeEventListener(event, callback) {
    if (this.listeners[event]) {
      this.listeners[event] = this.listeners[event].filter((cb) => cb !== callback)
    }
  }

  emit(event, data) {
    const eventObj = { data: JSON.stringify(data) }
    if (this.listeners[event]) {
      this.listeners[event].forEach((cb) => cb(eventObj))
    }
    if (event === 'message' && this.onmessage) {
      this.onmessage(eventObj)
    }
  }

  simulateError() {
    if (this.onerror) this.onerror(new Event('error'))
  }

  close() {
    this.closed = true
  }
}
MockEventSource.instances = []

describe('useParcelLiveStatus', () => {
  beforeEach(() => {
    MockEventSource.instances = []
    vi.stubGlobal('EventSource', MockEventSource)
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('starts with initial status and connects to EventSource', async () => {
    const { result } = renderHook(() =>
      useParcelLiveStatus('SD-TEST-123', 'CREATED')
    )

    expect(result.current.status).toBe('CREATED')
    expect(MockEventSource.instances.length).toBe(1)
    expect(MockEventSource.instances[0].url).toContain('/api/notifications/parcels/SD-TEST-123/stream')

    await act(async () => {
      vi.advanceTimersByTime(10)
    })

    expect(result.current.isConnected).toBe(true)
  })

  it('updates status and updatedAt when status event is received', async () => {
    const { result } = renderHook(() => useParcelLiveStatus('SD-TEST-123'))

    await act(async () => {
      vi.advanceTimersByTime(10)
    })

    const es = MockEventSource.instances[0]
    act(() => {
      es.emit('status', {
        trackingNumber: 'SD-TEST-123',
        status: 'OUT_FOR_DELIVERY',
        updatedAt: '2026-11-13T20:00:00Z',
      })
    })

    expect(result.current.status).toBe('OUT_FOR_DELIVERY')
    expect(result.current.updatedAt).toBe('2026-11-13T20:00:00Z')
  })

  it('preserves last known status and reconnects on error', async () => {
    const { result } = renderHook(() => useParcelLiveStatus('SD-TEST-123'))

    await act(async () => {
      vi.advanceTimersByTime(10)
    })

    const es = MockEventSource.instances[0]
    act(() => {
      es.emit('status', { status: 'DELIVERED', updatedAt: '2026-11-13T21:00:00Z' })
    })
    expect(result.current.status).toBe('DELIVERED')

    // Simulate connection drop
    act(() => {
      es.simulateError()
    })

    expect(result.current.isConnected).toBe(false)
    expect(result.current.status).toBe('DELIVERED') // Preserved!
    expect(result.current.error).toContain('Reconnecting')

    // Advance timers for backoff reconnect
    await act(async () => {
      vi.advanceTimersByTime(3000)
    })

    expect(MockEventSource.instances.length).toBe(2)
  })

  it('closes EventSource when unmounted', () => {
    const { unmount } = renderHook(() => useParcelLiveStatus('SD-TEST-123'))
    const es = MockEventSource.instances[0]
    expect(es.closed).toBeFalsy()

    unmount()
    expect(es.closed).toBe(true)
  })
})

