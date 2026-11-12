import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ApplicationStatusPage from './ApplicationStatusPage'
import ApplicationSubmittedPage from './ApplicationSubmittedPage'
import { useAuth } from '../../context/AuthContext'
import { ApiError, ApiErrorKind } from '../../services/apiClient'
import { getMyApplication } from '../../services/applicationService'

vi.mock('../../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../../services/applicationService', async (importOriginal) => ({
  ...(await importOriginal()),
  getMyApplication: vi.fn(),
}))

const NOT_IMPLEMENTED = new ApiError('n/a', { status: 501, kind: ApiErrorKind.NOT_IMPLEMENTED })

function mockAuth(role, status, overrides = {}) {
  const auth = {
    status: 'authenticated',
    user: { id: 1, name: 'Applicant', role, status },
    refreshUser: vi.fn().mockResolvedValue(null),
    logout: vi.fn(),
    ...overrides,
  }
  useAuth.mockReturnValue(auth)
  return auth
}

async function renderStatus() {
  render(
    <MemoryRouter initialEntries={['/application/status']}>
      <Routes>
        <Route path="/application/status" element={<ApplicationStatusPage />} />
        <Route path="/customer/dashboard" element={<p>customer dashboard</p>} />
      </Routes>
    </MemoryRouter>,
  )
  await act(async () => {})
}

const title = () => screen.getByRole('heading', { level: 1 })

describe('ApplicationStatusPage', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('falls back to the account status while the application endpoint is planned', async () => {
    mockAuth('MERCHANT', 'PENDING_APPROVAL')
    getMyApplication.mockRejectedValue(NOT_IMPLEMENTED)
    await renderStatus()

    expect(title()).toHaveTextContent('Application pending')
    expect(screen.getByText(/merchant application is awaiting review/)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows the submission date from the application record', async () => {
    mockAuth('COURIER', 'PENDING_APPROVAL')
    getMyApplication.mockResolvedValue({ status: 'PENDING', submittedAt: '2026-03-04T10:00:00Z' })
    await renderStatus()

    expect(title()).toHaveTextContent('Application pending')
    expect(screen.getByText(/It was submitted on/)).toBeInTheDocument()
    expect(getMyApplication).toHaveBeenCalledWith('COURIER')
  })

  it('shows a rejection only when the backend reports one, with its note', async () => {
    mockAuth('COURIER', 'DISABLED')
    getMyApplication.mockResolvedValue({ status: 'REJECTED', reviewNote: 'Licence expired.' })
    await renderStatus()

    expect(title()).toHaveTextContent('Application not approved')
    expect(screen.getByText('Licence expired.')).toBeInTheDocument()
  })

  it('shows an approved account with a link to the dashboard', async () => {
    mockAuth('MERCHANT', 'ACTIVE')
    getMyApplication.mockResolvedValue({ status: 'APPROVED' })
    await renderStatus()

    expect(title()).toHaveTextContent('Application approved')
    expect(screen.getByRole('link', { name: 'Go to your dashboard' }))
      .toHaveAttribute('href', '/dashboard')
  })

  it('explains a suspended account without calling it rejected', async () => {
    mockAuth('MERCHANT', 'SUSPENDED')
    getMyApplication.mockRejectedValue(NOT_IMPLEMENTED)
    await renderStatus()

    expect(title()).toHaveTextContent('Account not active')
    expect(screen.queryByText(/not approved/)).not.toBeInTheDocument()
  })

  it('warns when application details fail to load but still shows the account status', async () => {
    mockAuth('MERCHANT', 'PENDING_APPROVAL')
    getMyApplication.mockRejectedValue(new ApiError('down', {
      status: 503, kind: ApiErrorKind.SERVICE_UNAVAILABLE,
    }))
    await renderStatus()

    expect(screen.getByRole('alert')).toHaveTextContent('Application details could not be loaded.')
    expect(title()).toHaveTextContent('Application pending')
  })

  it('checks the latest status from the backend on request', async () => {
    const auth = mockAuth('COURIER', 'PENDING_APPROVAL')
    getMyApplication.mockRejectedValue(NOT_IMPLEMENTED)
    await renderStatus()

    await act(async () => {
      screen.getByRole('button', { name: 'Check latest status' }).click()
    })

    expect(auth.refreshUser).toHaveBeenCalledTimes(1)
    expect(getMyApplication).toHaveBeenCalledTimes(2)
    expect(screen.getByText(/Last checked at/)).toBeInTheDocument()
  })

  it('lets a pending applicant sign out', async () => {
    const auth = mockAuth('COURIER', 'PENDING_APPROVAL')
    getMyApplication.mockRejectedValue(NOT_IMPLEMENTED)
    await renderStatus()

    screen.getByRole('button', { name: 'Sign out' }).click()
    expect(auth.logout).toHaveBeenCalledTimes(1)
  })

  it('sends an active customer to their dashboard', async () => {
    mockAuth('CUSTOMER', 'ACTIVE')
    await renderStatus()

    expect(screen.getByText('customer dashboard')).toBeInTheDocument()
    expect(getMyApplication).not.toHaveBeenCalled()
  })
})

describe('ApplicationSubmittedPage', () => {
  it.each([
    ['MERCHANT', 'merchant'],
    ['COURIER', 'courier'],
  ])('explains that a %s account needs admin approval', (applicationType, name) => {
    render(
      <MemoryRouter initialEntries={[{
        pathname: '/application/submitted',
        state: { applicationType, email: 'a@example.com' },
      }]}>
        <ApplicationSubmittedPage />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { level: 1, name: 'Application submitted' }))
      .toBeInTheDocument()
    expect(screen.getByText(`Your ${name} account requires approval by a SwiftDrop administrator before it can be used.`))
      .toBeInTheDocument()
    expect(screen.getByText('a@example.com')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sign in to check your status' }))
      .toHaveAttribute('href', '/login')
  })
})
