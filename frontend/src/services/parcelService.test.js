import { beforeEach, describe, expect, it, vi } from 'vitest'
import { request } from './apiClient'
import { parcelService } from './parcelService'

vi.mock('./apiClient', () => ({
  request: vi.fn(),
  callEndpoint: vi.fn(),
}))

describe('parcelService merchant orders', () => {
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
})
