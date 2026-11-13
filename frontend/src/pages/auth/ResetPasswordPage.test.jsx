import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ResetPasswordPage from './ResetPasswordPage'
import { ApiError, ApiErrorKind } from '../../services/apiClient'
import { confirmPasswordReset } from '../../services/authService'

vi.mock('../../services/authService', () => ({ confirmPasswordReset: vi.fn() }))

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname}{location.hash}</div>
}

function renderAt(hash = '') {
  render(
    <MemoryRouter initialEntries={[`/reset-password${hash}`]}>
      <Routes>
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  )
}

async function submit(password, confirm = password) {
  fireEvent.change(screen.getByLabelText('New password'), { target: { value: password } })
  fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: confirm } })
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Change password' }))
  })
}

describe('ResetPasswordPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('removes the token from the address bar as soon as it has been read', () => {
    renderAt('#token=one-time-token')

    expect(screen.getByTestId('location')).toHaveTextContent(/^\/reset-password$/)
    expect(screen.getByLabelText('New password')).toBeInTheDocument()
  })

  it('refuses a link without a token', () => {
    renderAt()

    expect(screen.getByRole('alert')).toHaveTextContent('This password reset link is invalid')
    expect(screen.queryByLabelText('New password')).not.toBeInTheDocument()
  })

  it('validates the new password before calling the backend', async () => {
    renderAt('#token=one-time-token')

    await submit('short')
    expect(screen.getByText('Password must be at least 8 characters.')).toBeInTheDocument()
    await submit('new-password-456', 'different-456')
    expect(screen.getByText('Passwords do not match.')).toBeInTheDocument()
    expect(confirmPasswordReset).not.toHaveBeenCalled()
  })

  it('sets the new password with the token and points to sign-in', async () => {
    confirmPasswordReset.mockResolvedValue({ message: 'Password has been reset' })
    renderAt('#token=one-time-token')

    await submit('new-password-456')

    expect(confirmPasswordReset).toHaveBeenCalledWith('one-time-token', 'new-password-456')
    expect(screen.getByRole('heading', { level: 1, name: 'Password changed' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sign in with your new password' }))
      .toHaveAttribute('href', '/login')
  })

  it.each([
    ['an expired or used link', new ApiError('Invalid or expired password reset token', { status: 400 }),
      'This password reset link is invalid, has expired or was already used.'],
    ['a network failure', new ApiError('offline', { kind: ApiErrorKind.NETWORK }),
      'SwiftDrop service is temporarily unavailable.'],
  ])('explains %s', async (_label, error, message) => {
    confirmPasswordReset.mockRejectedValue(error)
    renderAt('#token=one-time-token')

    await submit('new-password-456')

    expect(screen.getByRole('alert')).toHaveTextContent(message)
    expect(screen.queryByText('Password changed')).not.toBeInTheDocument()
  })
})
