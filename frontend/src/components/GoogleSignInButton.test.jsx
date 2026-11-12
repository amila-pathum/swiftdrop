import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from '../pages/auth/LoginPage'
import RegisterPage from '../pages/auth/RegisterPage'
import { useAuth } from '../context/AuthContext'
import { startGoogleSignIn } from '../services/googleAuth'

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../services/googleAuth', () => ({ startGoogleSignIn: vi.fn() }))

const PAGES = [
  ['LoginPage', '/login', <LoginPage key="login" />],
  ['RegisterPage', '/register', <RegisterPage key="register" />],
]

function mockAuth() {
  const auth = {
    login: vi.fn(),
    register: vi.fn(),
    sessionExpired: false,
    dismissSessionExpired: vi.fn(),
  }
  useAuth.mockReturnValue(auth)
  return auth
}

function renderPage(path, element, state) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: path, state }]}>
      <Routes>
        <Route path={path} element={element} />
      </Routes>
    </MemoryRouter>,
  )
}

const googleButton = () => screen.queryByRole('button', { name: /google/i })

describe('Continue with Google', () => {
  beforeEach(() => {
    mockAuth()
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetAllMocks()
  })

  describe.each(PAGES)('on %s', (_name, path, element) => {
    it('is hidden unless VITE_GOOGLE_LOGIN_ENABLED is "true"', () => {
      vi.stubEnv('VITE_GOOGLE_LOGIN_ENABLED', 'false')
      renderPage(path, element)

      expect(googleButton()).not.toBeInTheDocument()
      expect(screen.queryByText(/google/i)).not.toBeInTheDocument()
    })

    it('is shown below the "or" divider when enabled', () => {
      vi.stubEnv('VITE_GOOGLE_LOGIN_ENABLED', 'true')
      renderPage(path, element)

      const button = googleButton()
      expect(button).toHaveAccessibleName('Continue with Google')
      expect(button).toHaveAttribute('type', 'button')
      expect(screen.getByRole('separator').compareDocumentPosition(button)
        & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    })

    it('starts the backend flow and shows a loading state', async () => {
      vi.stubEnv('VITE_GOOGLE_LOGIN_ENABLED', 'true')
      startGoogleSignIn.mockReturnValue(new Promise(() => {}))
      renderPage(path, element)

      fireEvent.click(googleButton())

      expect(startGoogleSignIn).toHaveBeenCalledTimes(1)
      expect(startGoogleSignIn).toHaveBeenCalledWith('/dashboard')
      expect(googleButton()).toBeDisabled()
      expect(googleButton()).toHaveTextContent('Redirecting to Google…')
    })

    it('explains when the browser cannot start Google sign-in', async () => {
      vi.stubEnv('VITE_GOOGLE_LOGIN_ENABLED', 'true')
      startGoogleSignIn.mockRejectedValue(new Error('crypto.subtle is unavailable'))
      renderPage(path, element)

      await act(async () => {
        fireEvent.click(googleButton())
      })

      expect(screen.getByRole('alert')).toHaveTextContent(/Google sign-in is not available in this browser/)
      expect(googleButton()).toBeEnabled()
    })
  })

  it('remembers the page the user was sent to sign in from', () => {
    vi.stubEnv('VITE_GOOGLE_LOGIN_ENABLED', 'true')
    startGoogleSignIn.mockReturnValue(new Promise(() => {}))
    renderPage('/login', <LoginPage />, { from: { pathname: '/profile', search: '?tab=1' } })

    fireEvent.click(googleButton())

    expect(startGoogleSignIn).toHaveBeenCalledWith('/profile?tab=1')
  })

  it('leaves the email and password sign-in working', async () => {
    vi.stubEnv('VITE_GOOGLE_LOGIN_ENABLED', 'true')
    const auth = mockAuth()
    auth.login.mockResolvedValue({})
    renderPage('/login', <LoginPage />)

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    })

    expect(auth.login).toHaveBeenCalledWith('user@example.com', 'password123', { remember: false })
    expect(startGoogleSignIn).not.toHaveBeenCalled()
  })

  it('leaves the registration form working', async () => {
    vi.stubEnv('VITE_GOOGLE_LOGIN_ENABLED', 'true')
    const auth = mockAuth()
    auth.register.mockReturnValue(new Promise(() => {}))
    renderPage('/register', <RegisterPage />)

    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'New Customer' } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'new@example.com' } })
    fireEvent.change(screen.getByLabelText('Phone number'), { target: { value: '0771234567' } })
    fireEvent.change(screen.getByLabelText('Address'), { target: { value: '1 Main St, Colombo' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
    fireEvent.change(screen.getByLabelText('Confirm password'), { target: { value: 'password123' } })
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
    })

    expect(auth.register).toHaveBeenCalledWith({
      name: 'New Customer',
      email: 'new@example.com',
      phone: '0771234567',
      address: '1 Main St, Colombo',
      password: 'password123',
    })
    expect(startGoogleSignIn).not.toHaveBeenCalled()
  })
})
