import { beforeEach, describe, expect, it, vi } from 'vitest'
import { request } from './apiClient'
import { parcelService } from './parcelService'

vi.mock('./apiClient', () => ({
  request: vi.fn(),
  callEndpoint: vi.fn(),
}))

describe('parcelService parcel queries', () => {
  beforeEach(() => vi.resetAllMocks())

  it('requests the authenticated paginated merchant endpoint', async () => {
    const signal = new AbortController().signal
    request.mockResolvedValue({ content: [] })

    await parcelService.getMerchantParcels(2, 25, signal)

    expect(request).toHaveBeenCalledWith('/api/parcels/merchant?page=2&size=25', {
      auth: true,
      signal,
    })
  })

  it('requests authenticated customer listing, details and history endpoints', async () => {
    const signal = new AbortController().signal
    request.mockResolvedValue({ content: [] })

    await parcelService.getMyParcels(1, 50, signal)
    await parcelService.getParcelById('parcel/id', signal)
    await parcelService.getParcelHistory('parcel/id', signal)

    expect(request).toHaveBeenNthCalledWith(1, '/api/parcels/my?page=1&size=50', {
      auth: true,
      signal,
    })
    expect(request).toHaveBeenNthCalledWith(2, '/api/parcels/parcel%2Fid', {
      auth: true,
      signal,
    })
    expect(request).toHaveBeenNthCalledWith(3, '/api/parcels/parcel%2Fid/history', {
      auth: true,
      signal,
    })
  })
})
