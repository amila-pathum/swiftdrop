export const VEHICLES = [
  { value: 'BICYCLE', label: 'Bicycle' }, { value: 'MOTORCYCLE', label: 'Motorbike' },
  { value: 'THREE_WHEELER', label: 'Three-wheeler' }, { value: 'CAR', label: 'Car' },
  { value: 'VAN', label: 'Van' }, { value: 'TRUCK', label: 'Truck' },
]
export const FAILURE_REASONS = [
  { value: 'RECIPIENT_ABSENT', label: 'Recipient absent' }, { value: 'WRONG_ADDRESS', label: 'Wrong address' },
  { value: 'REFUSED', label: 'Delivery refused' }, { value: 'INACCESSIBLE_LOCATION', label: 'Location inaccessible' },
  { value: 'DAMAGED_PARCEL', label: 'Damaged parcel' }, { value: 'OTHER', label: 'Other' },
]
export function validateProfile(values) {
  const errors = {}
  for (const [key, limit] of [['name', 100], ['phone', 30], ['vehicleNumber', 30], ['area', 100]]) {
    if (!values[key]?.trim()) errors[key] = 'This field is required'
    else if (values[key].trim().length > limit) errors[key] = `Use at most ${limit} characters`
  }
  if (values.phone && !/^[+0-9 ()-]{7,30}$/.test(values.phone.trim())) errors.phone = 'Enter a valid phone number'
  if (!VEHICLES.some(({ value }) => value === values.vehicleType)) errors.vehicleType = 'Select a vehicle type'
  return errors
}
export function photoError(photo) {
  if (!photo) return null
  if (!/\.(jpg|png)$/i.test(photo.name) || !['image/jpeg', 'image/png'].includes(photo.type)) return 'Choose a JPG or PNG image'
  if (photo.size === 0) return 'Choose a non-empty image'
  if (photo.size > 5 * 1024 * 1024) return 'The photo must be 5 MB or smaller'
  return null
}
export function validateDelivery(values, status, photo, location) {
  const errors = {}
  if (status === 'DELIVERED' && !values.recipientName.trim() && !photo) errors.recipientName = 'Enter the recipient name or add a photo'
  if (values.recipientName.length > 150) errors.recipientName = 'Use at most 150 characters'
  if (status === 'FAILED' && !FAILURE_REASONS.some(({ value }) => value === values.failureReason)) errors.failureReason = 'Select a failure reason'
  if (status === 'FAILED' && values.failureReason === 'OTHER' && !values.note.trim()) errors.note = 'Please explain the failure'
  if (values.note.length > 1000) errors.note = 'Use at most 1,000 characters'
  if (values.locationDescription.length > 500) errors.locationDescription = 'Use at most 500 characters'
  if (['PICKED_UP', 'DELIVERED', 'FAILED'].includes(status) && !location && !values.locationDescription.trim()) errors.locationDescription = 'Capture your location or describe where this happened'
  if (photoError(photo)) errors.photo = photoError(photo)
  return errors
}
