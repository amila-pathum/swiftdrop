import { useCallback } from 'react'
import { useAuth } from '../useAuth'
import { courierService } from '../../services/courierService'
import { useCourierResource } from './useCourierResource'

export function useCourierDeliveries(view) {
  const { user } = useAuth()
  const load = useCallback((options) => courierService.getDeliveries(view, options), [view])
  return useCourierResource(load, `deliveries:${user?.id}:${view}`)
}
export function useCourierDelivery(parcelId) {
  const { user } = useAuth()
  const load = useCallback(async (options) => {
    const detail = await courierService.getDelivery(parcelId, options)
    if (!detail?.parcel?.id || !detail?.assignment?.id) throw new Error('The delivery response is incomplete.')
    const [tracking, proofs] = await Promise.allSettled([courierService.getTracking(parcelId, options), courierService.getProofs(parcelId, options)])
    return { ...detail, tracking: tracking.status === 'fulfilled' ? tracking.value : [], proofs: proofs.status === 'fulfilled' ? proofs.value : [], evidenceUnavailable: tracking.status === 'rejected' || proofs.status === 'rejected' }
  }, [parcelId])
  return useCourierResource(load, `delivery:${user?.id}:${parcelId}`)
}
