import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ForgotPasswordPage from './ForgotPasswordPage'
import { ApiError, ApiErrorKind } from '../../services/apiClient'
import { requestPasswordReset } from '../../services/authService'

vi.mock('../../services/authService', () => ({ requestPasswordReset: vi.fn() }))

function renderPage() {
  render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>)
}

async function submit(email) {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }))
  })
}

describe('ForgotPasswordPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('validates the email first', async () => {
    renderPage()
    await submit('nope')

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
    expect(requestPasswordReset).not.toHaveBeenCalled()
  })

  it('says honestly that reset is not available while the endpoint is planned', async () => {
    requestPasswordReset.mockRejectedValue(new ApiError('n/a', {
      status: 501, kind: ApiErrorKind.NOT_IMPLEMENTED,
    }))
    renderPage()
    await submit('user@example.com')

    expect(screen.getByRole('alert')).toHaveTextContent('Password reset by email is not available yet')
    expect(screen.queryByText('Check your email')).not.toBeInTheDocument()
  })

  it('confirms without revealing whether the account exists once available', async () => {
    requestPasswordReset.mockResolvedValue(null)
    renderPage()
    await submit(' user@example.com ')

    expect(requestPasswordReset).toHaveBeenCalledWith('user@example.com')
    expect(screen.getByRole('status')).toHaveTextContent('If an account exists for user@example.com')
  })
})
