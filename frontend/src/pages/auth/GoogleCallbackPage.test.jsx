import { act, fireEvent, render, screen } from '@testing-library/react'
import { BrowserRouter, MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { AuthProvider } from '../../context/AuthContext'
import { ApiError, ApiErrorKind } from '../../services/apiClient'
import * as authService from '../../services/authService'
import { getAccessToken, getSession, saveSession } from '../../services/tokenStorage'

// Real AuthProvider, router and pages under StrictMode; only the network is mocked.
vi.mock('../../services/authService', () => ({
  register: vi.fn(),
  login: vi.fn(),
  exchangeGoogleCode: vi.fn(),
  getCurrentUser: vi.fn(),
}))

const PENDING_KEY = 'swiftdrop.googleSignIn'
const VERIFIER = 'frontend-verifier-0123456789-abcdefghijklmnop'
const CALLBACK = '/auth/google/callback'

const CUSTOMER = {
  id: 1,
  name: 'Google Customer',
  email: 'customer@gmail.com',
  role: 'CUSTOMER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
}

function storePending(returnTo = '/dashboard') {
  sessionStorage.setItem(PENDING_KEY,
    JSON.stringify({ verifier: VERIFIER, returnTo, startedAt: Date.now() }))
}

// Emulates the real exchangeGoogleCode, which stores the session once the
// response arrives, just before resolving.
function mockSuccessfulExchange(user = CUSTOMER) {
  authService.exchangeGoogleCode.mockImplementation(async () => {
    await Promise.resolve()
    const expiresAt = saveSession('google-session-token', 900)
    return { user, expiresAt }
  })
}

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{`${location.pathname}${location.search}${location.hash}`}</div>
}

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i += 1) {
      await Promise.resolve()
    }
  })
}

async function renderCallback(fragment) {
  render(
    <MemoryRouter initialEntries={[`${CALLBACK}${fragment}`]}>
      <AuthProvider>
        <App />
        <LocationProbe />
      </AuthProvider>
    </MemoryRouter>,
    { reactStrictMode: true },
  )
  await flush()
}

const heading = (name) => screen.getByRole('heading', { level: 1, name })
const currentLocation = () => screen.getByTestId('location').textContent
const failure = () => screen.getByRole('alert')

describe('GoogleCallbackPage', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.resetAllMocks()
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  describe('success', () => {
    it('exchanges the code once under StrictMode and signs the user in', async () => {
      storePending()
      mockSuccessfulExchange()

      await renderCallback('#code=one-time-code')

      expect(authService.exchangeGoogleCode).toHaveBeenCalledTimes(1)
      expect(authService.exchangeGoogleCode).toHaveBeenCalledWith('one-time-code', VERIFIER)
      expect(heading('Customer dashboard')).toBeInTheDocument()
      expect(currentLocation()).toBe('/dashboard')
      expect(getAccessToken()).toBe('google-session-token')
      // The session is restored from storage, not by a second profile request.
      expect(authService.getCurrentUser).not.toHaveBeenCalled()
    })

    it('removes the code from the URL before the exchange finishes', async () => {
      storePending()
      authService.exchangeGoogleCode.mockReturnValue(new Promise(() => {}))

      await renderCallback('#code=one-time-code')

      expect(currentLocation()).toBe(CALLBACK)
      expect(screen.getByRole('status')).toHaveTextContent('Signing you in with Google…')
    })

    it('removes the verifier from storage', async () => {
      storePending()
      mockSuccessfulExchange()

      await renderCallback('#code=one-time-code')

      expect(sessionStorage.getItem(PENDING_KEY)).toBeNull()
      expect(JSON.stringify({ ...sessionStorage })).not.toContain(VERIFIER)
    })

    it('returns to the page the user originally requested', async () => {
      storePending('/profile')
      mockSuccessfulExchange()

      await renderCallback('#code=one-time-code')

      expect(heading('Your profile')).toBeInTheDocument()
      expect(currentLocation()).toBe('/profile')
    })

    it.each([
      '//evil.example/profile',
      'https://evil.example/profile',
      '/login',
      CALLBACK,
    ])('ignores an unsafe stored return path (%s)', async (returnTo) => {
      storePending(returnTo)
      mockSuccessfulExchange()

      await renderCallback('#code=one-time-code')

      expect(currentLocation()).toBe('/dashboard')
    })

    it("shows the dashboard for the signed-in user's role", async () => {
      storePending()
      mockSuccessfulExchange({ ...CUSTOMER, role: 'MERCHANT' })

      await renderCallback('#code=one-time-code')

      expect(heading('Merchant dashboard')).toBeInTheDocument()
    })
  })

  describe('a code that cannot be used', () => {
    it.each([
      ['expired', 'Invalid or expired sign-in code'],
      ['invalid', 'Invalid or expired sign-in code'],
      ['already used', 'Invalid or expired sign-in code'],
    ])('explains an %s code without signing in', async (_label, backendMessage) => {
      storePending()
      authService.exchangeGoogleCode.mockRejectedValue(
        new ApiError(backendMessage, { status: 401 }))

      await renderCallback('#code=one-time-code')

      expect(heading('Google sign-in failed')).toBeInTheDocument()
      expect(failure()).toHaveTextContent('This Google sign-in has expired or was already used.')
      expect(getSession()).toBeNull()
      expect(sessionStorage.getItem(PENDING_KEY)).toBeNull()
    })

    it('explains an account that became inactive', async () => {
      storePending()
      authService.exchangeGoogleCode.mockRejectedValue(
        new ApiError('Account is not active', { status: 403 }))

      await renderCallback('#code=one-time-code')

      expect(failure()).toHaveTextContent('This account is not permitted to sign in.')
    })

    it('explains when SwiftDrop cannot be reached', async () => {
      storePending()
      authService.exchangeGoogleCode.mockRejectedValue(
        new ApiError('offline', { kind: ApiErrorKind.NETWORK }))

      await renderCallback('#code=one-time-code')

      expect(failure()).toHaveTextContent('SwiftDrop service is temporarily unavailable.')
    })

    it('refuses a code without the verifier from this tab', async () => {
      await renderCallback('#code=one-time-code')

      expect(failure()).toHaveTextContent('This Google sign-in was not started in this browser tab')
      expect(authService.exchangeGoogleCode).not.toHaveBeenCalled()
      expect(currentLocation()).toBe(CALLBACK)
    })

    it('refuses a callback without a code', async () => {
      storePending()

      await renderCallback('')

      expect(failure()).toHaveTextContent('Google sign-in could not be completed.')
      expect(authService.exchangeGoogleCode).not.toHaveBeenCalled()
      expect(sessionStorage.getItem(PENDING_KEY)).toBeNull()
    })
  })

  describe('reasons reported by the backend', () => {
    it.each([
      ['google_unavailable', 'Google sign-in is not available right now.'],
      ['account_exists', 'An account with this email already exists. Sign in with your email and password instead.'],
      ['account_inactive', 'This account is not permitted to sign in.'],
      ['email_unverified', 'Your Google account email is not verified.'],
      ['google_failed', 'Google sign-in could not be completed.'],
      ['something_new', 'Google sign-in could not be completed.'],
    ])('explains #error=%s', async (reason, message) => {
      storePending()

      await renderCallback(`#error=${reason}`)

      expect(failure()).toHaveTextContent(message)
      expect(authService.exchangeGoogleCode).not.toHaveBeenCalled()
      expect(sessionStorage.getItem(PENDING_KEY)).toBeNull()
      expect(currentLocation()).toBe(CALLBACK)
    })

    it('offers a way back to the sign-in page', async () => {
      await renderCallback('#error=account_exists')

      fireEvent.click(screen.getByRole('button', { name: 'Back to sign in' }))

      expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()
    })
  })

  it('removes the code from the real browser history', async () => {
    storePending()
    authService.exchangeGoogleCode.mockReturnValue(new Promise(() => {}))
    window.history.replaceState(null, '', `${CALLBACK}#code=secret-one-time-code`)
    const historyLength = window.history.length

    render(
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>,
      { reactStrictMode: true },
    )
    await flush()

    expect(window.location.pathname).toBe(CALLBACK)
    expect(window.location.hash).toBe('')
    expect(window.location.href).not.toContain('secret-one-time-code')
    expect(window.history.length).toBe(historyLength)
    expect(authService.exchangeGoogleCode).toHaveBeenCalledTimes(1)
    window.history.replaceState(null, '', '/')
  })
})
