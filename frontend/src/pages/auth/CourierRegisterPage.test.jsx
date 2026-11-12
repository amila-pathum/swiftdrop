import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import CourierRegisterPage from './CourierRegisterPage'
import { ApiError, ApiErrorKind } from '../../services/apiClient'
import { submitCourierApplication } from '../../services/applicationService'

vi.mock('../../services/applicationService', async (importOriginal) => ({
  ...(await importOriginal()),
  submitCourierApplication: vi.fn(),
}))

function SubmittedProbe() {
  const { state } = useLocation()
  return <p>submitted {state?.applicationType} for {state?.email}</p>
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
      expect(submitCourierApplication).not.toHaveBeenCalled()
    })

    it('rejects mismatched passwords', async () => {
      renderPage()
      fill({ 'Confirm password': 'password124' })
      await submit()

      expect(screen.getByText('Passwords do not match.')).toBeInTheDocument()
      expect(screen.getByLabelText('Confirm password')).toHaveFocus()
      expect(submitCourierApplication).not.toHaveBeenCalled()
    })

    it('rejects an over-long registration number', async () => {
      renderPage()
      fill({ 'Vehicle registration number': 'X'.repeat(21) })
      await submit()

      expect(screen.getByText('Registration number must be 20 characters or fewer.')).toBeInTheDocument()
    })
  })

  describe('submission', () => {
    it('submits the application and shows the submitted page', async () => {
      submitCourierApplication.mockResolvedValue({ id: 9, status: 'PENDING' })
      renderPage()
      fill()
      await submit()

      expect(submitCourierApplication).toHaveBeenCalledWith(expect.objectContaining({
        name: 'Kasun Silva',
        email: 'kasun@example.com',
        vehicleType: 'MOTORCYCLE',
        vehicleRegistrationNumber: 'WP BAA-1234',
        preferredArea: 'Kandy',
        password: 'password123',
      }))
      expect(screen.getByText('submitted COURIER for kasun@example.com')).toBeInTheDocument()
    })

    it('does not pretend to succeed while the backend endpoint is planned', async () => {
      submitCourierApplication.mockRejectedValue(new ApiError('n/a', {
        status: 501, kind: ApiErrorKind.NOT_IMPLEMENTED,
      }))
      renderPage()
      fill()
      await submit()

      expect(screen.getByRole('alert')).toHaveTextContent('Courier applications are not open yet')
      expect(screen.queryByText(/^submitted/)).not.toBeInTheDocument()
    })

    it('marks the email when an account already exists', async () => {
      submitCourierApplication.mockRejectedValue(new ApiError('exists', { status: 409 }))
      renderPage()
      fill()
      await submit()

      expect(screen.getByLabelText('Email'))
        .toHaveAccessibleDescription('An account or application already exists for this email.')
      expect(screen.getByLabelText('Email')).toHaveFocus()
    })
  })
})
