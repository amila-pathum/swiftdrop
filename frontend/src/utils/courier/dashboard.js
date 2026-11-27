import { activeDelivery, terminal } from './deliveryStatus'

const ZONE = 'Asia/Colombo'
/** YYYY-MM-DD in Sri Lanka time, comparable as a string with assignment.deliveryDate. */
export const dayKey = (value = new Date()) => {
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : new Intl.DateTimeFormat('en-CA', { timeZone: ZONE }).format(date)
}

/** 'overdue' | 'today' | 'upcoming' | 'unscheduled' for an active delivery. */
export function schedule(delivery, today = dayKey()) {
  const date = delivery.assignment?.deliveryDate
  if (!date) return 'unscheduled'
  if (date < today) return 'overdue'
  return date === today ? 'today' : 'upcoming'
}

const SCHEDULE_ORDER = { overdue: 0, today: 1, unscheduled: 2, upcoming: 3 }
// Parcels further along the route come first: they are closest to done.
const STAGE_ORDER = { OUT_FOR_DELIVERY: 0, IN_TRANSIT: 1, PICKED_UP: 2, CREATED: 3 }

/** Active deliveries, most urgent first. */
export function prioritise(deliveries, today = dayKey()) {
  return deliveries.filter(activeDelivery).sort((a, b) =>
    SCHEDULE_ORDER[schedule(a, today)] - SCHEDULE_ORDER[schedule(b, today)]
    || String(a.assignment.deliveryDate || '').localeCompare(String(b.assignment.deliveryDate || ''))
    || (STAGE_ORDER[a.parcel.status] ?? 9) - (STAGE_ORDER[b.parcel.status] ?? 9)
    || String(a.assignment.assignedAt || '').localeCompare(String(b.assignment.assignedAt || '')))
}

/** When the parcel reached its final status, from its history; null when unknown. */
export function finishedAt({ parcel }) {
  if (!terminal(parcel.status)) return null
  const entries = (parcel.history || []).filter((entry) => entry.status === parcel.status && entry.recordedAt)
  return entries.length ? entries.map((entry) => entry.recordedAt).sort().at(-1) : null
}

export function summarise(deliveries, today = dayKey()) {
  const active = deliveries.filter(activeDelivery)
  const finished = deliveries.filter(({ parcel }) => ['DELIVERED', 'FAILED'].includes(parcel.status))
  const delivered = finished.filter(({ parcel }) => parcel.status === 'DELIVERED')
  const finishedToday = finished.filter((delivery) => dayKey(finishedAt(delivery) || 0) === today)
  const stages = Object.fromEntries(Object.keys(STAGE_ORDER).map((status) => [status, active.filter(({ parcel }) => parcel.status === status).length]))
  return {
    active: active.length,
    overdue: active.filter((delivery) => schedule(delivery, today) === 'overdue').length,
    dueToday: active.filter((delivery) => schedule(delivery, today) === 'today').length,
    deliveredToday: finishedToday.filter(({ parcel }) => parcel.status === 'DELIVERED').length,
    failedToday: finishedToday.filter(({ parcel }) => parcel.status === 'FAILED').length,
    delivered: delivered.length,
    successRate: finished.length ? Math.round((delivered.length / finished.length) * 100) : null,
    stages,
  }
}

/** Most recently finished deliveries first. */
export function recentlyFinished(deliveries, limit = 5) {
  return deliveries.filter(({ parcel }) => ['DELIVERED', 'FAILED'].includes(parcel.status))
    .sort((a, b) => String(finishedAt(b) || '').localeCompare(String(finishedAt(a) || '')))
    .slice(0, limit)
}
