import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { courierService } from '../../services/courierService'
import { useDeliverySubmission } from './useDeliverySubmission'
vi.mock('../../services/courierService', () => ({ courierService: { submit: vi.fn(), getSubmission: vi.fn() } }))
describe('delivery confirmation', () => {
  beforeEach(() => vi.clearAllMocks())
  it('keeps pending updates locked and retries with the same submission and photo', async () => {
    courierService.submit.mockResolvedValue({ syncStatus: 'UNKNOWN' })
    const done = vi.fn()
    const { result } = renderHook(() => useDeliverySubmission('parcel', done))
    const photo = new File(['photo'], 'pod.jpg', { type: 'image/jpeg' })
    await act(() => result.current.submit({ status: 'DELIVERED' }, photo))
    expect(result.current.locked).toBe(true)
    expect(done).not.toHaveBeenCalled()
    await act(() => result.current.retry())
    expect(courierService.submit.mock.calls[1]).toEqual(courierService.submit.mock.calls[0])
    courierService.getSubmission.mockResolvedValue({ syncStatus: 'CONFIRMED' })
    await act(() => result.current.check())
    expect(done).toHaveBeenCalledOnce()
    expect(result.current.locked).toBe(false)
  })
  it('retains uncertain network submissions but unlocks definite validation failures', async () => {
    courierService.submit.mockRejectedValue(new Error('Network unavailable'))
    const { result } = renderHook(() => useDeliverySubmission('parcel', vi.fn()))
    await act(() => result.current.submit({ status: 'PICKED_UP' }))
    expect(result.current.phase).toBe('pending')
    courierService.submit.mockRejectedValue(Object.assign(new Error('Invalid location'), { status: 400 }))
    await act(() => result.current.retry())
    expect(result.current.phase).toBe('error')
    expect(result.current.locked).toBe(false)
  })
})
