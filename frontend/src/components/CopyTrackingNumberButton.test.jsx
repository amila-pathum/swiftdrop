import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CopyTrackingNumberButton from './CopyTrackingNumberButton'

describe('CopyTrackingNumberButton', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    })
  })

  it('copies the exact tracking number and confirms success', async () => {
    render(<CopyTrackingNumberButton trackingNumber="SD-2026-001" />)
    fireEvent.click(screen.getByRole('button', { name: 'Copy tracking number' }))

    await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledWith('SD-2026-001'))
    expect(screen.getByRole('button', { name: 'Copied!' })).toBeInTheDocument()
  })

  it('reports a clipboard failure without crashing the page', async () => {
    navigator.clipboard.writeText.mockRejectedValue(new Error('Not allowed'))
    render(<CopyTrackingNumberButton trackingNumber="SD-2026-002" />)
    fireEvent.click(screen.getByRole('button', { name: 'Copy tracking number' }))

    expect(await screen.findByRole('button', { name: 'Copy failed' })).toBeInTheDocument()
  })
})
