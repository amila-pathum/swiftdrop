import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CourierRegisterPage from './CourierRegisterPage'
import { ApiError, ApiErrorKind } from '../../services/apiClient'
import { applyAsCourier } from '../../services/applicationService'

vi.mock('../../services/applicationService', async (importOriginal) => ({
  ...(await importOriginal()),
  applyAsCourier: vi.fn(),
}))

function SubmittedProbe() {
  const { state } = useLocation()
  return (
    <p>
      {state?.applicationType} {state?.email} submitted={String(state?.applicationSubmitted)}
      {' '}unavailable={String(state?.applicationUnavailable)}
    </p>
  )
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/register/courier']}>
      <Routes>
        <Route path="/register/courier" element={<CourierRegisterPage />} />
        <Route path="/application/submitted" element={<SubmittedProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

const VALID = {
  'Full name': 'Kasun Silva',
  Email: 'kasun@example.com',
  'Phone number': '+94 71 555 1234',
  Address: '8 Temple Road, Kandy',
  'Vehicle type': 'MOTORCYCLE',
  'Vehicle registration number': 'wp baa-1234',
  'Preferred delivery area': 'Kandy',
  Password: 'password123',
  'Confirm password': 'password123',
}

function fill(overrides = {}) {
  for (const [label, value] of Object.entries({ ...VALID, ...overrides })) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
  }
}

const submit = () => act(async () => {
  fireEvent.click(screen.getByRole('button', { name: /submit application/i }))
})

describe('CourierRegisterPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('groups personal, vehicle and account details', () => {
    renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Apply to become a courier' }))
      .toBeInTheDocument()
    for (const group of ['Personal details', 'Vehicle details', 'Account details']) {
      expect(screen.getByRole('group', { name: group })).toBeInTheDocument()
    }
    const vehicle = screen.getByLabelText('Vehicle type')
    expect(vehicle.tagName).toBe('SELECT')
    expect(screen.getByRole('option', { name: 'Motorcycle' })).toBeInTheDocument()
  })

  describe('validation', () => {
    it('requires every field', async () => {
      renderPage()
      await submit()

      for (const message of [
        'Enter your full name.',
        'Enter your email address.',
        'Enter your phone number.',
        'Enter your address.',
        'Select your vehicle type.',
        'Enter your vehicle registration number.',
        'Enter your preferred delivery area.',
        'Enter a password.',
        'Confirm your password.',
      ]) {
        expect(screen.getByText(message)).toBeInTheDocument()
      }
      expect(screen.getByLabelText('Full name')).toHaveFocus()
      expect(applyAsCourier).not.toHaveBeenCalled()
    })

    it('rejects mismatched passwords', async () => {
      renderPage()
      fill({ 'Confirm password': 'password124' })
      await submit()

      expect(screen.getByText('Passwords do not match.')).toBeInTheDocument()
      expect(screen.getByLabelText('Confirm password')).toHaveFocus()
      expect(applyAsCourier).not.toHaveBeenCalled()
    })

    it('rejects an over-long registration number', async () => {
      renderPage()
      fill({ 'Vehicle registration number': 'X'.repeat(21) })
      await submit()

      expect(screen.getByText('Registration number must be 20 characters or fewer.')).toBeInTheDocument()
    })
  })

  describe('submission', () => {
    it('creates the account, submits the application and shows the submitted page', async () => {
      applyAsCourier.mockResolvedValue({
        user: { email: 'kasun@example.com' }, applicationSubmitted: true, applicationError: null,
      })
      renderPage()
      fill()
      await submit()

      expect(applyAsCourier).toHaveBeenCalledWith(expect.objectContaining({
        name: 'Kasun Silva',
        email: 'kasun@example.com',
        phone: '+94 71 555 1234',
        address: '8 Temple Road, Kandy',
        vehicleType: 'MOTORCYCLE',
        vehicleRegistrationNumber: 'WP BAA-1234',
        preferredArea: 'Kandy',
        password: 'password123',
      }))
      expect(screen.getByText('COURIER kasun@example.com submitted=true unavailable=false'))
        .toBeInTheDocument()
    })

    it('reports an account created without a submitted application honestly', async () => {
      applyAsCourier.mockResolvedValue({
        user: { email: 'kasun@example.com' },
        applicationSubmitted: false,
        applicationError: new ApiError('n/a', { status: 501, kind: ApiErrorKind.NOT_IMPLEMENTED }),
      })
      renderPage()
      fill()
      await submit()

      expect(screen.getByText('COURIER kasun@example.com submitted=false unavailable=true'))
        .toBeInTheDocument()
    })

    it('marks the email when an account already exists', async () => {
      applyAsCourier.mockRejectedValue(new ApiError('exists', { status: 409 }))
      renderPage()
      fill()
      await submit()

      expect(screen.getByLabelText('Email'))
        .toHaveAccessibleDescription('This email is already registered. Sign in instead.')
      expect(screen.getByLabelText('Email')).toHaveFocus()
      expect(screen.queryByText(/submitted=/)).not.toBeInTheDocument()
    })
  })
})
