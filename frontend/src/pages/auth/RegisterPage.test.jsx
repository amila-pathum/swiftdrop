import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import RegisterPage from './RegisterPage'
import { useAuth } from '../../context/AuthContext'
import { ApiError, ApiErrorKind } from '../../services/apiClient'

vi.mock('../../context/AuthContext', () => ({ useAuth: vi.fn() }))

const CREATED_USER = {
  id: 7,
  name: 'Test Customer',
  email: 'customer@example.com',
  role: 'CUSTOMER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
}

function mockAuth(overrides = {}) {
  const auth = { register: vi.fn(), login: vi.fn(), ...overrides }
  useAuth.mockReturnValue(auth)
  return auth
}

function LoginProbe() {
  const location = useLocation()
  return <p>login route for {location.state?.registeredEmail}</p>
}

function renderRegister() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

const field = (label) => screen.getByLabelText(label)
const submitButton = () => screen.getByRole('button', { name: /create account|creating account/i })

function fill({
  name = 'Test Customer',
  email = 'customer@example.com',
  phone = '077 123 4567',
  address = '12 Galle Road, Colombo 03',
  password = 'password123',
  confirmPassword = password,
} = {}) {
  fireEvent.change(field('Full name'), { target: { value: name } })
  fireEvent.change(field('Email'), { target: { value: email } })
  fireEvent.change(field('Phone number'), { target: { value: phone } })
  fireEvent.change(field('Address'), { target: { value: address } })
  fireEvent.change(field('Password'), { target: { value: password } })
  fireEvent.change(field('Confirm password'), { target: { value: confirmPassword } })
}

describe('RegisterPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('offers only customer registration fields', () => {
    mockAuth()
    renderRegister()

    expect(screen.getByRole('heading', { level: 1, name: 'Create a customer account' }))
      .toBeInTheDocument()
    expect(screen.getByText(/Dispatcher and admin accounts are created by SwiftDrop administrators/))
      .toBeInTheDocument()
    // Name, email, phone and address; no role selection of any kind.
    expect(screen.getAllByRole('textbox')).toHaveLength(4)
    expect(screen.getByLabelText('Phone number')).toBeInTheDocument()
    expect(screen.getByLabelText('Address')).toBeInTheDocument()
    expect(screen.queryByLabelText(/role/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    expect(screen.queryByText(/google/i)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login')
  })

  it('links to the merchant and courier applications', () => {
    mockAuth()
    renderRegister()

    const tabs = screen.getByRole('navigation', { name: 'Account type' })
    expect(tabs).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Merchant' })).toHaveAttribute('href', '/register/merchant')
    expect(screen.getByRole('link', { name: 'Courier' })).toHaveAttribute('href', '/register/courier')
    expect(screen.getByRole('link', { name: 'Customer' })).toHaveAttribute('aria-current', 'page')
  })

  describe('validation', () => {
    function submitWith(values) {
      const auth = mockAuth()
      renderRegister()
      fill(values)
      fireEvent.click(submitButton())
      return auth
    }

    it('requires every field', () => {
      const auth = mockAuth()
      renderRegister()

      fireEvent.click(submitButton())

      expect(screen.getByText('Enter your full name.')).toBeInTheDocument()
      expect(screen.getByText('Enter your email address.')).toBeInTheDocument()
      expect(screen.getByText('Enter your phone number.')).toBeInTheDocument()
      expect(screen.getByText('Enter your address.')).toBeInTheDocument()
      expect(screen.getByText('Enter a password.')).toBeInTheDocument()
      expect(screen.getByText('Confirm your password.')).toBeInTheDocument()
      expect(field('Full name')).toHaveFocus()
      expect(auth.register).not.toHaveBeenCalled()
    })

    it('rejects a whitespace-only name', () => {
      const auth = submitWith({ name: '   ' })
      expect(screen.getByText('Enter your full name.')).toBeInTheDocument()
      expect(auth.register).not.toHaveBeenCalled()
    })

    it('rejects an invalid email address', () => {
      const auth = submitWith({ email: 'not-an-email' })
      expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
      expect(field('Email')).toHaveFocus()
      expect(auth.register).not.toHaveBeenCalled()
    })

    it.each([
      ['letters', 'call me'],
      ['too few digits', '0771234'],
      ['too many digits', '0'.repeat(16)],
    ])('rejects a phone number with %s', (_label, phone) => {
      const auth = submitWith({ phone })
      expect(screen.getByText('Enter a valid phone number, for example 077 123 4567.'))
        .toBeInTheDocument()
      expect(field('Phone number')).toHaveFocus()
      expect(auth.register).not.toHaveBeenCalled()
    })

    it('rejects a whitespace-only address', () => {
      const auth = submitWith({ address: '   ' })
      expect(screen.getByText('Enter your address.')).toBeInTheDocument()
      expect(auth.register).not.toHaveBeenCalled()
    })

    it('re-validates as the user corrects the form', () => {
      submitWith({ email: 'not-an-email' })
      expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()

      fireEvent.change(field('Email'), { target: { value: 'fixed@example.com' } })
      expect(screen.queryByText('Enter a valid email address.')).not.toBeInTheDocument()
    })

    it('rejects a name over 100 characters', () => {
      const auth = submitWith({ name: 'a'.repeat(101) })
      expect(screen.getByText('Name must be 100 characters or fewer.')).toBeInTheDocument()
      expect(auth.register).not.toHaveBeenCalled()
    })

    it.each([
      ['too short', 'a'.repeat(7), 'Password must be at least 8 characters.'],
      ['too long', 'a'.repeat(73), 'Password must be 72 characters or fewer.'],
      ['over 72 bytes', 'é'.repeat(40), 'Password must be 72 characters or fewer.'],
    ])('rejects a password that is %s', (_label, password, message) => {
      const auth = submitWith({ password })
      expect(screen.getByText(message)).toBeInTheDocument()
      expect(auth.register).not.toHaveBeenCalled()
    })

    it('rejects mismatched passwords', () => {
      const auth = submitWith({ password: 'password123', confirmPassword: 'password124' })
      expect(screen.getByText('Passwords do not match.')).toBeInTheDocument()
      expect(auth.register).not.toHaveBeenCalled()
    })

    it('accepts values at the backend limits', async () => {
      const auth = mockAuth({ register: vi.fn().mockResolvedValue(CREATED_USER) })
      renderRegister()
      fill({ name: 'a'.repeat(100), password: 'a'.repeat(8) })

      await act(async () => {
        fireEvent.click(submitButton())
      })

      expect(auth.register).toHaveBeenCalledTimes(1)
    })
  })

  describe('submission', () => {
    it('registers with trimmed details, never a role, and goes to login', async () => {
      const auth = mockAuth({ register: vi.fn().mockResolvedValue(CREATED_USER) })
      renderRegister()
      fill({
        name: '  Test Customer ',
        email: ' Customer@Example.com ',
        phone: ' +94 77 123 4567 ',
        address: ' 12 Galle Road ',
      })

      await act(async () => {
        fireEvent.click(submitButton())
      })

      expect(auth.register).toHaveBeenCalledTimes(1)
      expect(auth.register.mock.calls[0]).toEqual([{
        name: 'Test Customer',
        email: 'Customer@Example.com',
        phone: '+94 77 123 4567',
        address: '12 Galle Road',
        password: 'password123',
      }])
      expect(auth.login).not.toHaveBeenCalled()
      expect(screen.getByText('login route for customer@example.com')).toBeInTheDocument()
    })

    it('marks the email field when it is already registered', async () => {
      mockAuth({
        register: vi.fn().mockRejectedValue(
          new ApiError('Email is already registered', { status: 409 })),
      })
      renderRegister()
      fill()

      await act(async () => {
        fireEvent.click(submitButton())
      })

      expect(field('Email')).toHaveAccessibleDescription('This email is already registered.')
      expect(field('Email')).toHaveFocus()
      expect(submitButton()).toBeEnabled()
    })

    it.each([
      ['invalid data', new ApiError('Invalid request data', { status: 400 }),
        'Some details are invalid. Check the form and try again.'],
      ['a network failure', new ApiError('offline', { kind: ApiErrorKind.NETWORK }),
        'SwiftDrop service is temporarily unavailable. Please try again shortly.'],
      ['an unavailable gateway', new ApiError('down', {
        status: 502, kind: ApiErrorKind.SERVICE_UNAVAILABLE }),
      'SwiftDrop service is temporarily unavailable. Please try again shortly.'],
    ])('shows a friendly message for %s', async (_label, error, message) => {
      mockAuth({ register: vi.fn().mockRejectedValue(error) })
      renderRegister()
      fill()

      await act(async () => {
        fireEvent.click(submitButton())
      })

      expect(screen.getByRole('alert')).toHaveTextContent(message)
      expect(screen.queryByText(/login route/)).not.toBeInTheDocument()
      expect(submitButton()).toBeEnabled()
    })

    it('shows a loading state and ignores duplicate submissions', async () => {
      const auth = mockAuth({ register: vi.fn(() => new Promise(() => {})) })
      renderRegister()
      fill()

      await act(async () => {
        fireEvent.click(submitButton())
      })
      expect(submitButton()).toBeDisabled()
      expect(submitButton()).toHaveTextContent('Creating account…')

      await act(async () => {
        fireEvent.submit(submitButton().closest('form'))
      })
      expect(auth.register).toHaveBeenCalledTimes(1)
    })
  })
})
