import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MerchantRegisterPage from './MerchantRegisterPage'
import { ApiError, ApiErrorKind } from '../../services/apiClient'
import { submitMerchantApplication } from '../../services/applicationService'

vi.mock('../../services/applicationService', () => ({ submitMerchantApplication: vi.fn() }))

function SubmittedProbe() {
  const { state } = useLocation()
  return <p>submitted {state?.applicationType} for {state?.email}</p>
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/register/merchant']}>
      <Routes>
        <Route path="/register/merchant" element={<MerchantRegisterPage />} />
        <Route path="/application/submitted" element={<SubmittedProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

const VALID = {
  'Business name': 'Lanka Books (Pvt) Ltd',
  'Business registration number': 'PV 00123456',
  'Business email': 'orders@lankabooks.lk',
  'Business phone': '011 234 5678',
  'Business address': '45 Duplication Road, Colombo 04',
  "Contact person's name": 'Nimal Perera',
  "Contact person's email": 'nimal@lankabooks.lk',
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

describe('MerchantRegisterPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('groups business and account details and explains approval', () => {
    renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Apply for a merchant account' }))
      .toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Business details' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Account details' })).toBeInTheDocument()
    expect(screen.getByText(/reviewed before activation/)).toBeInTheDocument()
    expect(screen.queryByText(/google/i)).not.toBeInTheDocument()
  })

  describe('validation', () => {
    it('requires every field and focuses the first', async () => {
      renderPage()
      await submit()

      for (const message of [
        'Enter your business name.',
        'Enter your business registration number.',
        'Enter your business email address.',
        'Enter your business phone number.',
        'Enter your business address.',
        "Enter the contact person's name.",
        "Enter the contact person's email address.",
        'Enter a password.',
        'Confirm your password.',
      ]) {
        expect(screen.getByText(message)).toBeInTheDocument()
      }
      expect(screen.getByLabelText('Business name')).toHaveFocus()
      expect(submitMerchantApplication).not.toHaveBeenCalled()
    })

    it.each([
      ['Business email', 'not-an-email', 'Enter a valid email address.'],
      ["Contact person's email", 'nimal@', 'Enter a valid email address.'],
      ['Business phone', '12', 'Enter a valid phone number, for example 077 123 4567.'],
      ['Password', 'short', 'Password must be at least 8 characters.'],
      ['Confirm password', 'different123', 'Passwords do not match.'],
    ])('rejects an invalid %s', async (label, value, message) => {
      renderPage()
      fill({ [label]: value })
      await submit()

      expect(screen.getByLabelText(label)).toHaveAccessibleDescription(expect.stringContaining(message))
      expect(submitMerchantApplication).not.toHaveBeenCalled()
    })
  })

  describe('submission', () => {
    it('submits trimmed details and shows the application submitted page', async () => {
      submitMerchantApplication.mockResolvedValue({ id: 3, status: 'PENDING' })
      renderPage()
      fill({ 'Business name': '  Lanka Books (Pvt) Ltd  ' })
      await submit()

      expect(submitMerchantApplication).toHaveBeenCalledWith({
        businessName: 'Lanka Books (Pvt) Ltd',
        businessRegistrationNumber: 'PV 00123456',
        businessEmail: 'orders@lankabooks.lk',
        businessPhone: '011 234 5678',
        businessAddress: '45 Duplication Road, Colombo 04',
        contactName: 'Nimal Perera',
        contactEmail: 'nimal@lankabooks.lk',
        password: 'password123',
        confirmPassword: 'password123',
      })
      expect(screen.getByText('submitted MERCHANT for nimal@lankabooks.lk')).toBeInTheDocument()
    })

    it('does not pretend to succeed while the backend endpoint is planned', async () => {
      submitMerchantApplication.mockRejectedValue(new ApiError('Not available', {
        status: 501, kind: ApiErrorKind.NOT_IMPLEMENTED,
      }))
      renderPage()
      fill()
      await submit()

      expect(screen.getByRole('alert')).toHaveTextContent('Merchant applications are not open yet')
      expect(screen.getByRole('alert')).toHaveTextContent('Your details have not been submitted.')
      expect(screen.queryByText(/^submitted/)).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Submit application' })).toBeEnabled()
    })

    it.each([
      ['a duplicate', new ApiError('exists', { status: 409 }), /already exists/],
      ['invalid data', new ApiError('bad', { status: 400 }), /Some details are invalid/],
      ['a network failure', new ApiError('x', { kind: ApiErrorKind.NETWORK }), /temporarily unavailable/],
    ])('explains %s', async (_label, error, message) => {
      submitMerchantApplication.mockRejectedValue(error)
      renderPage()
      fill()
      await submit()

      expect(screen.getByRole('alert')).toHaveTextContent(message)
    })

    it('shows a loading state and ignores duplicate submissions', async () => {
      submitMerchantApplication.mockReturnValue(new Promise(() => {}))
      renderPage()
      fill()
      await submit()

      const button = screen.getByRole('button', { name: /submitting application/i })
      expect(button).toBeDisabled()
      await act(async () => {
        fireEvent.submit(button.closest('form'))
      })
      expect(submitMerchantApplication).toHaveBeenCalledTimes(1)
    })
  })
})
