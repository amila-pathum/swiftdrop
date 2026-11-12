import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { AuthProvider } from './context/AuthContext'
import { ApiError, ApiErrorKind } from './services/apiClient'
import * as authService from './services/authService'
import { getSession, saveSession } from './services/tokenStorage'

// Real AuthProvider and router; only the network layer is mocked.
vi.mock('./services/authService', () => ({
  register: vi.fn(),
  login: vi.fn(),
  getCurrentUser: vi.fn(),
}))

const USER = {
  id: 1,
  name: 'Test User',
  email: 'user@example.com',
  role: 'CUSTOMER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
}

async function renderApp(path) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
    { reactStrictMode: true },
  )
  await act(async () => {
    for (let i = 0; i < 5; i += 1) {
      await Promise.resolve()
    }
  })
}

const heading = (name) => screen.getByRole('heading', { level: 1, name })

describe('App with the real AuthProvider', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    sessionStorage.clear()
    vi.resetAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('sends a visitor without a session to login', async () => {
    await renderApp('/dashboard')

    expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()
    expect(authService.getCurrentUser).not.toHaveBeenCalled()
  })

  it('restores a stored session onto the dashboard', async () => {
    saveSession('stored-token', 900)
    authService.getCurrentUser.mockResolvedValue(USER)

    await renderApp('/dashboard')

    expect(heading('Customer dashboard')).toBeInTheDocument()
    expect(authService.getCurrentUser).toHaveBeenCalledTimes(1)
  })

  it('returns to login with a notice when the session expires', async () => {
    saveSession('stored-token', 900)
    authService.getCurrentUser.mockResolvedValue(USER)
    await renderApp('/profile')
    expect(heading('Your profile')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(900_000)
    })

    expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Your session has expired')
    expect(getSession()).toBeNull()
  })

  it('logs out to the login page without an expiry notice', async () => {
    saveSession('stored-token', 900)
    authService.getCurrentUser.mockResolvedValue(USER)
    await renderApp('/dashboard')

    act(() => {
      screen.getByRole('button', { name: 'Log out' }).click()
    })

    expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(getSession()).toBeNull()
  })

  it('keeps the session and recovers via retry when the server was unavailable', async () => {
    saveSession('stored-token', 900)
    authService.getCurrentUser
      .mockRejectedValueOnce(new ApiError('down', {
        status: 502, kind: ApiErrorKind.SERVICE_UNAVAILABLE }))
      .mockResolvedValueOnce(USER)
    await renderApp('/dashboard')

    expect(heading('Unable to reach SwiftDrop')).toBeInTheDocument()
    expect(getSession()).not.toBeNull()

    await act(async () => {
      screen.getByRole('button', { name: 'Try again' }).click()
    })

    expect(heading('Customer dashboard')).toBeInTheDocument()
  })

  describe('login and registration forms', () => {
    function mockSuccessfulLogin() {
      authService.login.mockImplementation(async () => {
        const expiresAt = saveSession('new-token', 900)
        return { user: USER, expiresAt }
      })
    }

    async function signIn(email = USER.email, password = 'password123') {
      fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
      fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } })
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
      })
    }

    it('returns the user to the page they originally requested', async () => {
      mockSuccessfulLogin()
      await renderApp('/profile')
      expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()

      await signIn()

      expect(heading('Your profile')).toBeInTheDocument()
      expect(authService.login).toHaveBeenCalledWith(USER.email, 'password123', { remember: false })
    })

    it('sends a direct login to the role dashboard', async () => {
      mockSuccessfulLogin()
      await renderApp('/login')

      await signIn()

      expect(heading('Customer dashboard')).toBeInTheDocument()
      expect(vi.getTimerCount()).toBe(1)
    })

    it('stays on the login page with an error after invalid credentials', async () => {
      authService.login.mockRejectedValue(
        new ApiError('Invalid email or password', { status: 401 }))
      await renderApp('/login')

      await signIn(USER.email, 'wrong-password')

      expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()
      expect(screen.getByRole('alert')).toHaveTextContent('Invalid email or password.')
      expect(getSession()).toBeNull()
    })

    it('registers without signing in and prefills the login form', async () => {
      authService.register.mockResolvedValue({ ...USER, email: 'new@example.com' })
      await renderApp('/register')

      fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'New Customer' } })
      fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'new@example.com' } })
      fireEvent.change(screen.getByLabelText('Phone number'), { target: { value: '0771234567' } })
      fireEvent.change(screen.getByLabelText('Address'), { target: { value: '1 Main St' } })
      fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
      fireEvent.change(screen.getByLabelText('Confirm password'),
        { target: { value: 'password123' } })
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
      })

      expect(authService.register).toHaveBeenCalledWith({
        name: 'New Customer',
        email: 'new@example.com',
        phone: '0771234567',
        address: '1 Main St',
        password: 'password123',
      })
      expect(authService.login).not.toHaveBeenCalled()
      expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()
      expect(screen.getByRole('status')).toHaveTextContent('Your account has been created')
      expect(screen.getByLabelText('Email')).toHaveValue('new@example.com')
      expect(getSession()).toBeNull()
    })
  })

  describe('accounts awaiting approval', () => {
    it('signs a pending merchant in to the application status page, not the dashboard', async () => {
      const pending = { ...USER, role: 'MERCHANT', status: 'PENDING_APPROVAL' }
      authService.login.mockImplementation(async () => ({
        user: pending, expiresAt: saveSession('pending-token', 900),
      }))
      await renderApp('/merchant/dashboard')
      expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()

      fireEvent.change(screen.getByLabelText('Email'), { target: { value: USER.email } })
      fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
      })
      await act(async () => {})

      expect(heading('Application pending')).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Merchant dashboard' })).not.toBeInTheDocument()
    })

    it('restores a pending courier session onto the application status page', async () => {
      saveSession('stored-token', 900)
      authService.getCurrentUser.mockResolvedValue({
        ...USER, role: 'COURIER', status: 'PENDING_APPROVAL',
      })
      await renderApp('/dashboard')
      await act(async () => {})

      expect(heading('Application pending')).toBeInTheDocument()
    })
  })
})
