import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ProtectedRoute from './ProtectedRoute'
import PublicOnlyRoute from './PublicOnlyRoute'
import { useAuth } from '../context/AuthContext'

vi.mock('../context/AuthContext', () => ({ useAuth: vi.fn() }))

function mockAuth(status, role) {
  useAuth.mockReturnValue({
    status,
    user: role ? { id: 1, name: 'Test User', role } : null,
    error: null,
    refreshUser: vi.fn(),
  })
}

function LoginProbe() {
  const location = useLocation()
  return <p>login page (from {location.state?.from?.pathname ?? 'nowhere'})</p>
}

function renderRoutes(path, state) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: path, state }]}>
      <Routes>
        <Route path="/login" element={<PublicOnlyRoute><LoginProbe /></PublicOnlyRoute>} />
        <Route path="/dashboard" element={<p>dashboard page</p>} />
        <Route
          path="/merchant/orders"
          element={(
            <ProtectedRoute allowedRoles={['MERCHANT']}>
              <p>merchant orders</p>
            </ProtectedRoute>
          )}
        />
        <Route
          path="/operations"
          element={(
            <ProtectedRoute allowedRoles={['DISPATCHER', 'ADMIN']}>
              <p>operations</p>
            </ProtectedRoute>
          )}
        />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('renders the route for a permitted role', () => {
    mockAuth('authenticated', 'MERCHANT')
    renderRoutes('/merchant/orders')
    expect(screen.getByText('merchant orders')).toBeInTheDocument()
  })

  it('redirects a customer away from a merchant-only route to the dashboard', () => {
    mockAuth('authenticated', 'CUSTOMER')
    renderRoutes('/merchant/orders')

    expect(screen.getByText('dashboard page')).toBeInTheDocument()
    expect(screen.queryByText('merchant orders')).not.toBeInTheDocument()
  })

  it.each([
    ['DISPATCHER', true],
    ['ADMIN', true],
    ['COURIER', false],
  ])('applies a multi-role restriction to %s', (role, allowed) => {
    mockAuth('authenticated', role)
    renderRoutes('/operations')

    expect(screen.queryByText('operations') !== null).toBe(allowed)
  })

  it('redirects anonymous users to login and remembers the requested page', () => {
    mockAuth('anonymous')
    renderRoutes('/merchant/orders')

    expect(screen.getByText('login page (from /merchant/orders)')).toBeInTheDocument()
  })

  describe('PublicOnlyRoute after sign-in', () => {
    it('continues to the page that was originally requested', () => {
      mockAuth('authenticated', 'MERCHANT')
      renderRoutes('/login', { from: { pathname: '/merchant/orders' } })
      expect(screen.getByText('merchant orders')).toBeInTheDocument()
    })

    it.each([
      ['no requested page', undefined],
      ['a protocol-relative URL', { from: { pathname: '//evil.example/x' } }],
      ['a public-only page', { from: { pathname: '/login' } }],
      ['a malformed value', { from: 'https://evil.example' }],
    ])('falls back to the dashboard for %s', (_label, state) => {
      mockAuth('authenticated', 'CUSTOMER')
      renderRoutes('/login', state)
      expect(screen.getByText('dashboard page')).toBeInTheDocument()
    })
  })

  it('does not evaluate roles while the session is loading', () => {
    mockAuth('loading')
    renderRoutes('/merchant/orders')

    expect(screen.getByRole('status')).toHaveTextContent('Checking your session')
    expect(screen.queryByText('dashboard page')).not.toBeInTheDocument()
  })
})
