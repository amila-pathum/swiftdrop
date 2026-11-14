import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { courierService } from '../../../../services/courierService'
import DeliveryUpdateForm from './DeliveryUpdateForm'
vi.mock('../../../../services/courierService', () => ({ courierService: { submit: vi.fn(), getSubmission: vi.fn() } }))
describe('delivery proof form', () => {
  beforeEach(() => vi.clearAllMocks())
  it('blocks incomplete proof and sends the entered delivery place', async () => {
    courierService.submit.mockResolvedValue({ syncStatus: 'CONFIRMED' })
    const confirmed = vi.fn()
    render(<DeliveryUpdateForm parcel={{ id: 'p', status: 'OUT_FOR_DELIVERY' }} onConfirmed={confirmed} />)
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivered' }))
    expect(courierService.submit).not.toHaveBeenCalled()
    expect(screen.getByText('Enter the recipient name or add a photo')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Recipient name'), { target: { value: 'Amal' } })
    fireEvent.change(screen.getByLabelText('Location description'), { target: { value: 'Reception, ground floor' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivered' }))
    await waitFor(() => expect(confirmed).toHaveBeenCalledOnce())
    expect(courierService.submit).toHaveBeenCalledWith('p', expect.objectContaining({ status: 'DELIVERED', recipientName: 'Amal', locationDescription: 'Reception, ground floor', submissionId: expect.any(String) }), null)
  })
  it('keeps pending proof locked and does not announce successful delivery', async () => {
    courierService.submit.mockResolvedValue({ syncStatus: 'PENDING' })
    const confirmed = vi.fn()
    render(<DeliveryUpdateForm parcel={{ id: 'p', status: 'OUT_FOR_DELIVERY' }} onConfirmed={confirmed} />)
    fireEvent.change(screen.getByLabelText('Recipient name'), { target: { value: 'Amal' } })
    fireEvent.change(screen.getByLabelText('Location description'), { target: { value: 'Front gate' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivered' }))
    await screen.findByRole('button', { name: 'Check confirmation' })
    expect(screen.getByLabelText('Recipient name')).toBeDisabled()
    expect(confirmed).not.toHaveBeenCalled()
  })
})
