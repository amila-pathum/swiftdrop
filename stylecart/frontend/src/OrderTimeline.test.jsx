import { render, screen, cleanup } from '@testing-library/react'
import { afterEach, expect, it } from 'vitest'
import OrderTimeline from './OrderTimeline'

afterEach(cleanup)
const order = { createdAt: '2026-11-15T10:00:00Z', trackingNumber: 'SD123', timeline: [] }
const statusName = status => ({ CREATED: 'Delivery created', DELIVERED: 'Delivered' })[status]

it('shows confirmed shipment creation without requiring a webhook or inventing its time', () => {
  render(<OrderTimeline order={order} statusName={statusName}/>)
  expect(screen.getByText('Delivery created')).toBeInTheDocument()
  expect(screen.getByText('Shipment confirmed by SwiftDrop')).toBeInTheDocument()
})

it('does not show shipment creation for an unbooked order', () => {
  render(<OrderTimeline order={{ ...order, trackingNumber: null }} statusName={statusName}/>)
  expect(screen.queryByText('Delivery created')).not.toBeInTheDocument()
})

it('replaces the confirmation with the dated creation event when its webhook arrives', () => {
  const { rerender } = render(<OrderTimeline order={order} statusName={statusName}/>)
  rerender(<OrderTimeline order={{ ...order, timeline: [
    { eventId: 'created', status: 'CREATED', occurredAt: '2026-11-15T10:01:00Z' },
    { eventId: 'delivered', status: 'DELIVERED', occurredAt: '2026-11-16T10:00:00Z' },
  ] }} statusName={statusName}/>)
  expect(screen.getAllByText('Delivery created')).toHaveLength(1)
  expect(screen.queryByText('Shipment confirmed by SwiftDrop')).not.toBeInTheDocument()
  expect(screen.getByText('Delivered')).toBeInTheDocument()
})
