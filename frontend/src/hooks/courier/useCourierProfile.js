import { useAuth } from '../useAuth'
import { courierService } from '../../services/courierService'
import { useCourierResource } from './useCourierResource'

export function useCourierProfile() {
  const { user } = useAuth()
  return useCourierResource(courierService.getProfile, `profile:${user?.id}`)
}
