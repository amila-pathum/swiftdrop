import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import LoginPage from './LoginPage'
import { useAuth } from '../../context/AuthContext'
import { ApiError, ApiErrorKind } from '../../services/apiClient'

vi.mock('../../context/AuthContext', () => ({ useAuth: vi.fn() }))

function mockAuth(overrides = {}) {
  const auth = {
    login: vi.fn(),
    sessionExpired: false,
    dismissSessionExpired: vi.fn(),
    ...overrides,
  }
  useAuth.mockReturnValue(auth)
  return auth
}

function renderLogin(state) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<h1>Register route</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

const emailInput = () => screen.getByLabelText('Email')
const passwordInput = () => screen.getByLabelText('Password')
const submitButton = () => screen.getByRole('button', { name: /sign in|signing in/i })

function fillAndSubmit(email, password) {
  fireEvent.change(emailInput(), { target: { value: email } })
  fireEvent.change(passwordInput(), { target: { value: password } })
  fireEvent.click(submitButton())
}

describe('LoginPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('renders the Figma content without demo, Google or tracking features', () => {
    mockAuth()
    renderLogin()

    expect(screen.getByRole('heading', { level: 1, name: 'Sign in to SwiftDrop' }))
      .toBeInTheDocument()
    expect(screen.getByText('Book, manage and track deliveries across Sri Lanka.'))
      .toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Create account' })).toHaveAttribute('href', '/register')
    expect(screen.queryByText(/google/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/demo/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/track a parcel/i)).not.toBeInTheDocument()
  })

  describe('validation', () => {
    it('requires email and password and focuses the first invalid field', () => {
      const auth = mockAuth()
      renderLogin()

      fireEvent.click(submitButton())

      expect(screen.getByText('Enter your email address.')).toBeInTheDocument()
      expect(screen.getByText('Enter your password.')).toBeInTheDocument()
      expect(emailInput()).toHaveAttribute('aria-invalid', 'true')
      expect(emailInput()).toHaveAccessibleDescription('Enter your email address.')
      expect(emailInput()).toHaveFocus()
      expect(auth.login).not.toHaveBeenCalled()
    })

    it('rejects an invalid email address', () => {
      const auth = mockAuth()
      renderLogin()

      fillAndSubmit('not-an-email', 'password123')

      expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
      expect(auth.login).not.toHaveBeenCalled()
    })

    it('rejects a password longer than the backend accepts', () => {
      const auth = mockAuth()
      renderLogin()

      fillAndSubmit('user@example.com', 'a'.repeat(73))

      expect(screen.getByText('Password must be 72 characters or fewer.')).toBeInTheDocument()
      expect(auth.login).not.toHaveBeenCalled()
    })

    it('clears errors as the user corrects the form', () => {
      mockAuth()
      renderLogin()
      fireEvent.click(submitButton())

      fireEvent.change(emailInput(), { target: { value: 'user@example.com' } })

      expect(screen.queryByText('Enter your email address.')).not.toBeInTheDocument()
      expect(screen.getByText('Enter your password.')).toBeInTheDocument()
    })
  })

  describe('submission', () => {
    it('signs in with the trimmed email and the exact password', async () => {
      const auth = mockAuth({ login: vi.fn().mockResolvedValue({}) })
      renderLogin()

      await act(async () => {
        fillAndSubmit('  user@example.com ', ' pass word1 ')
      })

      expect(auth.login).toHaveBeenCalledWith('user@example.com', ' pass word1 ')
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it.each([
      ['invalid credentials', new ApiError('Invalid email or password', { status: 401 }),
        'Invalid email or password.'],
      ['an inactive account', new ApiError('This account is not permitted to sign in', {
        status: 403 }), 'This account is not permitted to sign in.'],
      ['a network failure', new ApiError('offline', { kind: ApiErrorKind.NETWORK }),
        'SwiftDrop service is temporarily unavailable. Please try again shortly.'],
      ['an unavailable gateway', new ApiError('down', {
        status: 502, kind: ApiErrorKind.SERVICE_UNAVAILABLE }),
      'SwiftDrop service is temporarily unavailable. Please try again shortly.'],
      ['blocked browser storage', new Error('Unable to store the session.'),
        'Unable to sign in. Check that your browser allows this site to store data.'],
    ])('shows a friendly message for %s and allows another attempt', async (_label, error, message) => {
      mockAuth({ login: vi.fn().mockRejectedValue(error) })
      renderLogin()

      await act(async () => {
        fillAndSubmit('user@example.com', 'password123')
      })

      expect(screen.getByRole('alert')).toHaveTextContent(message)
      expect(submitButton()).toBeEnabled()
      expect(submitButton()).toHaveTextContent('Sign in')
    })

    it('shows a loading state and ignores duplicate submissions', async () => {
      let finishLogin
      const auth = mockAuth({
        login: vi.fn(() => new Promise((resolve, reject) => { finishLogin = reject })),
      })
      renderLogin()

      await act(async () => {
        fillAndSubmit('user@example.com', 'password123')
      })
      expect(submitButton()).toBeDisabled()
      expect(submitButton()).toHaveTextContent('Signing in…')
      expect(submitButton()).toHaveAttribute('aria-busy', 'true')

      await act(async () => {
        fireEvent.submit(submitButton().closest('form'))
        fireEvent.click(submitButton())
      })
      expect(auth.login).toHaveBeenCalledTimes(1)

      await act(async () => {
        finishLogin(new ApiError('Invalid email or password', { status: 401 }))
      })
      expect(submitButton()).toBeEnabled()
    })
  })

  it('toggles password visibility with an accessible button', () => {
    mockAuth()
    renderLogin()
    const toggle = screen.getByRole('button', { name: 'Show password' })

    expect(passwordInput()).toHaveAttribute('type', 'password')
    expect(toggle).toHaveAttribute('aria-pressed', 'false')

    fireEvent.click(toggle)
    expect(passwordInput()).toHaveAttribute('type', 'text')
    expect(toggle).toHaveAttribute('aria-pressed', 'true')

    fireEvent.click(toggle)
    expect(passwordInput()).toHaveAttribute('type', 'password')
  })

  describe('notifications', () => {
    it('shows the session-expired notice', () => {
      const auth = mockAuth({ sessionExpired: true })
      renderLogin()

      expect(screen.getByRole('alert')).toHaveTextContent('Your session has expired')
      fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
      expect(auth.dismissSessionExpired).toHaveBeenCalledTimes(1)
    })

    it('confirms a new registration and prefills the email', () => {
      mockAuth()
      renderLogin({ registeredEmail: 'new@example.com' })

      expect(screen.getByRole('status')).toHaveTextContent('Your account has been created')
      expect(emailInput()).toHaveValue('new@example.com')
      expect(passwordInput()).toHaveValue('')
    })
  })
})
