import { beforeEach, describe, expect, it, vi } from 'vitest'
import { request } from './apiClient'
import { courierService } from './courierService'
vi.mock('./apiClient', async (original) => ({ ...await original(), request: vi.fn() }))
describe('courier API', () => {
  beforeEach(() => vi.clearAllMocks())
  it('loads every assignment page and deduplicates overlaps by assignment', async () => {
    const item = { assignment: { id: 'a' }, parcel: { id: 'p' } }
    request.mockResolvedValue({ content: [item], totalPages: 2 })
    expect(await courierService.getDeliveries('all')).toEqual([item])
    expect(request).toHaveBeenCalledTimes(4)
  })
  it('sends status updates as JSON', async () => {
    await courierService.submit('parcel-1', { submissionId: 'id', status: 'DELIVERED', photoKey: 'proofs/p/a.jpg' })
    const [url, options] = request.mock.calls[0]
    expect(url).toBe('/api/couriers/me/deliveries/parcel-1/status')
    expect(options).toMatchObject({ method: 'POST', auth: true, body: { submissionId: 'id', status: 'DELIVERED', photoKey: 'proofs/p/a.jpg' } })
  })
  it('puts the photo straight to the signed URL without the API token and returns its key', async () => {
    const photo = new File(['bytes'], 'pod.jpg', { type: 'image/jpeg' })
    request.mockResolvedValue({ photoKey: 'proofs/p/a.jpg', uploadUrl: 'https://bucket.s3/signed', headers: { 'Content-Type': 'image/jpeg' } })
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true, status: 200 })
    expect(await courierService.uploadPhoto('parcel-1', photo)).toBe('proofs/p/a.jpg')
    expect(request).toHaveBeenCalledWith('/api/couriers/me/deliveries/parcel-1/photo-uploads',
      { method: 'POST', auth: true, body: { contentType: 'image/jpeg', sizeBytes: 5 } })
    expect(fetchMock).toHaveBeenCalledWith('https://bucket.s3/signed', { method: 'PUT', headers: { 'Content-Type': 'image/jpeg' }, body: photo })
    fetchMock.mockResolvedValue({ ok: false, status: 403 })
    await expect(courierService.uploadPhoto('parcel-1', photo)).rejects.toThrow('could not be uploaded')
    fetchMock.mockRestore()
  })
})
