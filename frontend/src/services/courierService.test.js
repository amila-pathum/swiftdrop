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
  it('uploads JSON metadata and image as multipart without serializing the file', async () => {
    const photo = new File(['bytes'], 'pod.jpg', { type: 'image/jpeg' })
    await courierService.submit('parcel-1', { submissionId: 'id', status: 'DELIVERED' }, photo)
    const [url, options] = request.mock.calls[0]
    expect(url).toBe('/api/couriers/me/deliveries/parcel-1/status')
    expect(options.body).toBeInstanceOf(FormData)
    expect(options.body.get('photo')).toBe(photo)
    expect(options.body.get('data').type).toBe('application/json')
  })
})
