import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminDashboardPage from './AdminDashboardPage'
import { useAuth } from '../../context/AuthContext'
import { createStaffAccount } from '../../services/adminUserService'
import { ApiError } from '../../services/apiClient'

vi.mock('../../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../../services/adminUserService', () => ({ createStaffAccount: vi.fn() }))

// The page links to User Management, so it needs a router.
const renderPage = () => render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>)

function fillAccount(name, email) {
  fireEvent.change(screen.getByLabelText(/Full name/), { target: { value: name } })
  fireEvent.change(screen.getByLabelText(/^Email/), { target: { value: email } })
  fireEvent.change(screen.getByLabelText(/Temporary password/), {
    target: { value: 'temporary-pass-123' },
  })
}

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ user: { name: 'Admin', role: 'ADMIN', status: 'ACTIVE' } })
  })
  afterEach(() => vi.resetAllMocks())

  it('creates a dispatcher by default and clears the password', async () => {
    createStaffAccount.mockResolvedValue({
      id: 7, name: 'Dispatcher One', email: 'dispatcher@example.test',
      role: 'DISPATCHER', status: 'ACTIVE',
    })
    renderPage()

    expect(screen.getByLabelText(/^Role/)).toHaveValue('DISPATCHER')
    fillAccount('Dispatcher One', 'dispatcher@example.test')
    fireEvent.click(screen.getByRole('button', { name: 'Create dispatcher' }))

    await waitFor(() => expect(createStaffAccount).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Dispatcher One', email: 'dispatcher@example.test',
      password: 'temporary-pass-123', role: 'DISPATCHER',
    })))
    expect(await screen.findByRole('status'))
      .toHaveTextContent('Dispatcher Dispatcher One (dispatcher@example.test) was created successfully.')
    expect(screen.getByLabelText(/Temporary password/)).toHaveValue('')
  })

  it('creates an administrator only after the admin confirms full access', async () => {
    createStaffAccount.mockResolvedValue({
      id: 8, name: 'Second Admin', email: 'admin2@example.test', role: 'ADMIN', status: 'ACTIVE',
    })
    renderPage()

    fireEvent.change(screen.getByLabelText(/^Role/), { target: { value: 'ADMIN' } })
    expect(screen.getByRole('alert')).toHaveTextContent('An administrator has full access')
    fillAccount('Second Admin', 'admin2@example.test')
    fireEvent.click(screen.getByRole('button', { name: 'Create administrator' }))

    expect(screen.getByText('Confirm that this person should have full administrator access.'))
      .toBeInTheDocument()
    expect(createStaffAccount).not.toHaveBeenCalled()

    fireEvent.click(screen.getByLabelText('I understand this person will have full administrator access.'))
    fireEvent.click(screen.getByRole('button', { name: 'Create administrator' }))

    await waitFor(() => expect(createStaffAccount).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Second Admin', email: 'admin2@example.test', role: 'ADMIN',
    })))
    expect(await screen.findByRole('status'))
      .toHaveTextContent('Administrator Second Admin (admin2@example.test) was created successfully.')
    // The form resets to a dispatcher, so the next account is not an admin by accident.
    expect(screen.getByLabelText(/^Role/)).toHaveValue('DISPATCHER')
    expect(screen.queryByLabelText(/full administrator access/)).not.toBeInTheDocument()
  })

  it('asks for confirmation again after switching back to administrator', () => {
    renderPage()

    fireEvent.change(screen.getByLabelText(/^Role/), { target: { value: 'ADMIN' } })
    fireEvent.click(screen.getByLabelText('I understand this person will have full administrator access.'))
    fireEvent.change(screen.getByLabelText(/^Role/), { target: { value: 'DISPATCHER' } })
    fireEvent.change(screen.getByLabelText(/^Role/), { target: { value: 'ADMIN' } })

    expect(screen.getByLabelText('I understand this person will have full administrator access.'))
      .not.toBeChecked()
  })

  it('validates before calling the API', () => {
    useAuth.mockReturnValue({ user: { role: 'ADMIN', status: 'ACTIVE' } })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Create dispatcher' }))
    expect(screen.getByText('Enter the staff member’s full name.')).toBeInTheDocument()
    expect(screen.getByText('Temporary password must be 12 to 72 characters.')).toBeInTheDocument()
    expect(createStaffAccount).not.toHaveBeenCalled()
  })

  it('explains a duplicate email', async () => {
    createStaffAccount.mockRejectedValue(new ApiError('exists', { status: 409 }))
    renderPage()
    fireEvent.change(screen.getByLabelText(/^Role/), { target: { value: 'ADMIN' } })
    fireEvent.click(screen.getByLabelText('I understand this person will have full administrator access.'))
    fillAccount('Second Admin', 'taken@example.test')
    fireEvent.click(screen.getByRole('button', { name: 'Create administrator' }))

    await waitFor(() => expect(createStaffAccount).toHaveBeenCalled())
    expect(await screen.findByText('An account already uses that email address.')).toBeInTheDocument()
  })
})
