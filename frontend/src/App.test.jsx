import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { useAuth } from './context/AuthContext'
import { ApiError, ApiErrorKind } from './services/apiClient'
import { dispatchService } from './services/dispatchService'

vi.mock('./context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('./services/dispatchService', () => ({
  dispatchService: {
    getUnassigned: vi.fn().mockResolvedValue({ content: [], page: 0, totalPages: 0, totalElements: 0 }),
    getParcels: vi.fn().mockResolvedValue({ content: [], page: 0, totalPages: 0, totalElements: 0 }),
    getCouriers: vi.fn().mockResolvedValue([]),
    getParcel: vi.fn().mockResolvedValue({ parcel: {}, history: [] }),
    assignCourier: vi.fn(),
    getReport: vi.fn().mockResolvedValue([]),
    exportReport: vi.fn().mockResolvedValue(''),
  },
}))

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

const authenticatedAs = (role = 'CUSTOMER') =>
  mockAuth({ status: 'authenticated', user: { ...USER, role } })

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

const heading = (name) => screen.getByRole('heading', { level: 1, name })

describe('App routing', () => {
  beforeEach(() => {
    dispatchService.getUnassigned.mockResolvedValue({
      content: [], page: 0, totalPages: 0, totalElements: 0,
    })
    dispatchService.getParcels.mockResolvedValue({
      content: [], page: 0, totalPages: 0, totalElements: 0,
    })
    dispatchService.getCouriers.mockResolvedValue([])
    dispatchService.getParcel.mockResolvedValue({ parcel: {}, history: [] })
    dispatchService.getReport.mockResolvedValue([])
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('anonymous users', () => {
    it.each(['/', '/dashboard', '/profile'])('are redirected from %s to login', (path) => {
      mockAuth()
      renderAt(path)

      expect(heading('Sign in to SwiftDrop')).toBeInTheDocument()
      expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    })

    it('can open the login and registration pages', () => {
      mockAuth()
      renderAt('/register')
      expect(heading('Create a customer account')).toBeInTheDocument()
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
    it('are redirected from / to their dashboard inside the shared layout', () => {
      authenticatedAs('CUSTOMER')
      renderAt('/')

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

    it.each(['/login', '/register'])('are redirected from %s to the dashboard', (path) => {
      authenticatedAs('CUSTOMER')
      renderAt(path)

      expect(heading('Customer dashboard')).toBeInTheDocument()
    })

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
      ['ADMIN', 'Admin dashboard'],
    ])('shows a not-available page for %s', (role, title) => {
      authenticatedAs(role)
      renderAt('/dashboard')

      expect(heading(title)).toBeInTheDocument()
      expect(screen.getByText(/future development phase/)).toBeInTheDocument()
      expect(screen.queryByText('Customer dashboard')).not.toBeInTheDocument()
    })

    it('shows dispatch management to dispatcher users', async () => {
      authenticatedAs('DISPATCHER')
      renderAt('/dashboard')

      expect(await screen.findByRole('heading', { level: 1, name: 'Dispatch dashboard' }))
        .toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Reports' })).toHaveAttribute('href', '/reports')
    })

    it('allows admins to open reports and redirects other roles away', async () => {
      authenticatedAs('ADMIN')
      renderAt('/reports')
      expect(await screen.findByRole('heading', { level: 1, name: 'Dispatch reports' }))
        .toBeInTheDocument()

      authenticatedAs('CUSTOMER')
      renderAt('/reports')
      expect(await screen.findByRole('heading', { level: 1, name: 'Customer dashboard' }))
        .toBeInTheDocument()
    })

    it('handles an unrecognised role safely', () => {
      authenticatedAs('UNKNOWN')
      renderAt('/dashboard')

      expect(heading('Dashboard not available')).toBeInTheDocument()
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
