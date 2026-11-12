import { render, screen, within } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { useAuth } from './context/AuthContext'
import { ApiError, ApiErrorKind } from './services/apiClient'

vi.mock('./context/AuthContext', () => ({ useAuth: vi.fn() }))

const USER = {
  id: 1,
  name: 'Test User',
  email: 'user@example.com',
  role: 'CUSTOMER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
}

function mockAuth(overrides = {}) {
  const auth = {
    user: null,
    status: 'anonymous',
    error: null,
    sessionExpired: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshUser: vi.fn(),
    dismissSessionExpired: vi.fn(),
    ...overrides,
  }
  useAuth.mockReturnValue(auth)
  return auth
}

const authenticatedAs = (role = 'CUSTOMER', status = 'ACTIVE') =>
  mockAuth({ status: 'authenticated', user: { ...USER, role, status } })

function LocationProbe() {
  return <div data-testid="location">{useLocation().pathname}</div>
}

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <LocationProbe />
    </MemoryRouter>,
  )
}

const currentPath = () => screen.getByTestId('location').textContent

const heading = (name) => screen.getByRole('heading', { level: 1, name })

describe('App routing', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('anonymous users', () => {
    it.each([
      '/dashboard',
      '/profile',
      '/customer/dashboard',
      '/merchant/dashboard',
      '/courier/dashboard',
      '/dispatcher/dashboard',
      '/admin/dashboard',
      '/application/status',
    ])('are redirected from %s to login', (path) => {
      mockAuth()
      renderAt(path)

      expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()
      expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    })

    it.each([
      ['/register', 'Create a customer account'],
      ['/register/merchant', 'Apply for a merchant account'],
      ['/register/courier', 'Apply to become a courier'],
      ['/forgot-password', 'Reset your password'],
    ])('can open %s', (path, title) => {
      mockAuth()
      renderAt(path)
      expect(heading(title)).toBeInTheDocument()
    })

    it('see the landing page at /', () => {
      mockAuth()
      renderAt('/')

      expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/Deliveries that are/)
      for (const id of ['home', 'about', 'services', 'track', 'get-started']) {
        expect(document.getElementById(id)).not.toBeNull()
      }
      expect(screen.getAllByRole('link', { name: 'Log in' })[0]).toHaveAttribute('href', '/login')
      expect(screen.getAllByRole('link', { name: 'Get started' })[0])
        .toHaveAttribute('href', '/#get-started')
      expect(screen.getByRole('link', { name: 'Sign up as a customer' }))
        .toHaveAttribute('href', '/register')
      expect(screen.getByRole('link', { name: 'Apply as a merchant' }))
        .toHaveAttribute('href', '/register/merchant')
      expect(screen.getByRole('link', { name: 'Apply as a courier' }))
        .toHaveAttribute('href', '/register/courier')
      expect(screen.getAllByRole('search', { name: 'Track a parcel' })).toHaveLength(2)
      expect(screen.getByRole('contentinfo')).toBeInTheDocument()
    })

    it('can open the application submitted page', () => {
      mockAuth()
      renderAt('/application/submitted')
      expect(heading('Application submitted')).toBeInTheDocument()
    })

    it('see the session-expired notice and can dismiss it', () => {
      const auth = mockAuth({ sessionExpired: true })
      renderAt('/dashboard')

      expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()
      expect(screen.getByRole('alert')).toHaveTextContent('Your session has expired')
      screen.getByRole('button', { name: 'Dismiss' }).click()
      expect(auth.dismissSessionExpired).toHaveBeenCalledTimes(1)
    })

    it('do not see a notice without an expired session', () => {
      mockAuth()
      renderAt('/login')
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })

  describe('authenticated users', () => {
    it('see the landing page at / with a link to their dashboard', () => {
      authenticatedAs('CUSTOMER')
      renderAt('/')

      expect(screen.getAllByRole('link', { name: 'Go to dashboard' })[0])
        .toHaveAttribute('href', '/dashboard')
      expect(within(screen.getByRole('banner')).queryByRole('link', { name: 'Log in' }))
        .not.toBeInTheDocument()
      expect(screen.queryByText(/Already have an account/)).not.toBeInTheDocument()
    })

    it('reach their dashboard inside the shared layout', () => {
      authenticatedAs('CUSTOMER')
      renderAt('/dashboard')

      expect(heading('Customer dashboard')).toBeInTheDocument()
      expect(screen.getByText('Welcome, Test User.')).toBeInTheDocument()
      expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument()
      expect(screen.getByText('Customer')).toBeInTheDocument()
    })

    it('can view their profile with only the public profile fields', () => {
      authenticatedAs('CUSTOMER')
      renderAt('/profile')

      expect(heading('Your profile')).toBeInTheDocument()
      expect(screen.getByText('user@example.com')).toBeInTheDocument()
      expect(screen.getByText('CUSTOMER')).toBeInTheDocument()
      expect(screen.getByText('ACTIVE')).toBeInTheDocument()
      expect(screen.queryByText(/password/i)).not.toBeInTheDocument()
    })

    it.each(['/login', '/register', '/register/merchant', '/register/courier', '/forgot-password'])(
      'are redirected from %s to the dashboard',
      (path) => {
        authenticatedAs('CUSTOMER')
        renderAt(path)

        expect(heading('Customer dashboard')).toBeInTheDocument()
        expect(currentPath()).toBe('/customer/dashboard')
      },
    )

    it('can log out from the navbar', () => {
      const auth = authenticatedAs('CUSTOMER')
      renderAt('/dashboard')

      screen.getByRole('button', { name: 'Log out' }).click()
      expect(auth.logout).toHaveBeenCalledTimes(1)
    })
  })

  describe('role-based dashboard', () => {
    it('shows the customer dashboard placeholder', () => {
      authenticatedAs('CUSTOMER')
      renderAt('/dashboard')

      expect(heading('Customer dashboard')).toBeInTheDocument()
      expect(screen.queryByText(/future development phase/)).not.toBeInTheDocument()
    })

    it.each([
      ['MERCHANT', 'Merchant dashboard'],
      ['COURIER', 'Courier dashboard'],
      ['DISPATCHER', 'Dispatcher dashboard'],
      ['ADMIN', 'Admin dashboard'],
    ])('shows a not-available page for %s', (role, title) => {
      authenticatedAs(role)
      renderAt('/dashboard')

      expect(heading(title)).toBeInTheDocument()
      expect(screen.getByText(/future development phase/)).toBeInTheDocument()
      expect(screen.queryByText('Customer dashboard')).not.toBeInTheDocument()
    })

    it.each([
      ['CUSTOMER', '/customer/dashboard'],
      ['MERCHANT', '/merchant/dashboard'],
      ['COURIER', '/courier/dashboard'],
      ['DISPATCHER', '/dispatcher/dashboard'],
      ['ADMIN', '/admin/dashboard'],
    ])('redirects %s from /dashboard to %s', (role, path) => {
      authenticatedAs(role)
      renderAt('/dashboard')
      expect(currentPath()).toBe(path)
    })

    it.each([
      ['CUSTOMER', '/admin/dashboard', '/customer/dashboard'],
      ['MERCHANT', '/courier/dashboard', '/merchant/dashboard'],
      ['COURIER', '/dispatcher/dashboard', '/courier/dashboard'],
      ['DISPATCHER', '/admin/dashboard', '/dispatcher/dashboard'],
      ['ADMIN', '/customer/dashboard', '/admin/dashboard'],
    ])('sends %s away from %s to their own dashboard', (role, path, expected) => {
      authenticatedAs(role)
      renderAt(path)
      expect(currentPath()).toBe(expected)
    })

    it('handles an unrecognised role safely', () => {
      authenticatedAs('UNKNOWN')
      renderAt('/dashboard')

      expect(heading('Dashboard not available')).toBeInTheDocument()
    })
  })

  describe('accounts awaiting approval', () => {
    it.each([
      ['MERCHANT', '/dashboard'],
      ['MERCHANT', '/merchant/dashboard'],
      ['MERCHANT', '/login'],
      ['COURIER', '/dashboard'],
      ['COURIER', '/courier/dashboard'],
      ['COURIER', '/register/courier'],
    ])('send a pending %s from %s to the application status page', async (role, path) => {
      authenticatedAs(role, 'PENDING_APPROVAL')
      renderAt(path)

      expect(await screen.findByRole('heading', { level: 1, name: 'Application pending' }))
        .toBeInTheDocument()
      expect(currentPath()).toBe('/application/status')
      expect(screen.queryByText(/dashboard/i, { selector: 'h1' })).not.toBeInTheDocument()
    })

    it('let a pending account view its profile', () => {
      authenticatedAs('MERCHANT', 'PENDING_APPROVAL')
      renderAt('/profile')

      expect(heading('Your profile')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Application status' }))
        .toHaveAttribute('href', '/application/status')
      expect(screen.queryByRole('link', { name: 'Dashboard' })).not.toBeInTheDocument()
    })

    it('send an active customer away from the application status page', () => {
      authenticatedAs('CUSTOMER')
      renderAt('/application/status')
      expect(currentPath()).toBe('/customer/dashboard')
    })
  })

  describe('parcel tracking', () => {
    it('explains that tracking is not available yet instead of faking a result', async () => {
      mockAuth()
      renderAt('/track/SD-2026-000123')

      expect(await screen.findByText('Tracking coming soon')).toBeInTheDocument()
      expect(screen.getByText(/Online parcel tracking is not available yet/)).toBeInTheDocument()
    })

    it('rejects a malformed tracking number in the URL', () => {
      mockAuth()
      renderAt('/track/x')
      expect(screen.getByRole('alert'))
        .toHaveTextContent('That does not look like a SwiftDrop tracking number.')
    })
  })

  describe('session verification', () => {
    it.each(['/dashboard', '/login'])('shows a loading screen on %s', (path) => {
      mockAuth({ status: 'loading' })
      renderAt(path)

      expect(screen.getByRole('status')).toHaveTextContent('Checking your session')
      expect(screen.queryByRole('heading', { name: 'Sign in to SwiftDrop' })).not.toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: 'Customer dashboard' }))
        .not.toBeInTheDocument()
    })

    it('offers a retry instead of redirecting to login when the server is unavailable', () => {
      const auth = mockAuth({
        status: 'loading',
        error: new ApiError('The service is temporarily unavailable. Please try again shortly.', {
          status: 502,
          kind: ApiErrorKind.SERVICE_UNAVAILABLE,
        }),
      })
      renderAt('/dashboard')

      expect(heading('Unable to reach SwiftDrop')).toBeInTheDocument()
      expect(screen.getByRole('alert')).toHaveTextContent('temporarily unavailable')
      expect(screen.queryByRole('heading', { name: 'Sign in to SwiftDrop' })).not.toBeInTheDocument()

      screen.getByRole('button', { name: 'Try again' }).click()
      expect(auth.refreshUser).toHaveBeenCalledTimes(1)
    })
  })

  describe('unknown routes', () => {
    it.each([
      ['anonymous', () => mockAuth()],
      ['authenticated', () => authenticatedAs('CUSTOMER')],
    ])('show the not-found page to %s users', (_label, setUp) => {
      setUp()
      renderAt('/no-such-page')

      expect(heading('Page not found')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Go to your dashboard' }))
        .toHaveAttribute('href', '/dashboard')
    })
  })
})
