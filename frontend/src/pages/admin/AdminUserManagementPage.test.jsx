import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminUserManagementPage from './AdminUserManagementPage'
import { useAuth } from '../../context/AuthContext'
import { createStaffAccount, listUsers } from '../../services/adminUserService'
import { ApiError } from '../../services/apiClient'

vi.mock('../../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../../services/adminUserService', () => ({
  changeUserRole: vi.fn(),
  changeUserStatus: vi.fn(),
  createStaffAccount: vi.fn(),
  listUsers: vi.fn(),
}))

const CONFIRM = 'I understand this person will have full administrator access.'

async function openDialog() {
  render(<AdminUserManagementPage />)
  await waitFor(() => expect(listUsers).toHaveBeenCalled())
  fireEvent.click(screen.getByRole('button', { name: 'Create Staff Account' }))
  return screen.getByRole('dialog', { name: 'Create Staff Account' })
}

function fillAccount(dialog, name, email) {
  fireEvent.change(within(dialog).getByLabelText('Full name'), { target: { value: name } })
  fireEvent.change(within(dialog).getByLabelText('Email'), { target: { value: email } })
  fireEvent.change(within(dialog).getByLabelText('Temporary password'), {
    target: { value: 'temporary-pass-123' },
  })
}

describe('AdminUserManagementPage – create staff account', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ user: { id: 1, name: 'Admin', role: 'ADMIN', status: 'ACTIVE' } })
    listUsers.mockResolvedValue({ content: [], totalPages: 1, totalElements: 0 })
  })
  afterEach(() => vi.resetAllMocks())

  it('creates a dispatcher by default, closes the dialog and refreshes the list', async () => {
    createStaffAccount.mockResolvedValue({
      id: 7, name: 'Dispatcher One', email: 'dispatcher@example.test', role: 'DISPATCHER', status: 'ACTIVE',
    })
    const dialog = await openDialog()
    const callsBefore = listUsers.mock.calls.length

    expect(within(dialog).getByLabelText(/^Dispatcher/)).toBeChecked()
    fillAccount(dialog, 'Dispatcher One', 'dispatcher@example.test')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create Dispatcher' }))

    await waitFor(() => expect(createStaffAccount).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Dispatcher One', email: 'dispatcher@example.test',
      password: 'temporary-pass-123', role: 'DISPATCHER',
    })))
    expect(await screen.findByRole('status'))
      .toHaveTextContent('Dispatcher account created for Dispatcher One (dispatcher@example.test).')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(listUsers.mock.calls.length).toBeGreaterThan(callsBefore))
  })

  it('creates an administrator only after the admin confirms full access', async () => {
    createStaffAccount.mockResolvedValue({
      id: 8, name: 'Second Admin', email: 'admin2@example.test', role: 'ADMIN', status: 'ACTIVE',
    })
    const dialog = await openDialog()

    fireEvent.click(within(dialog).getByLabelText(/^Administrator/))
    expect(within(dialog).getByText('Administrators have full access')).toBeInTheDocument()
    fillAccount(dialog, 'Second Admin', 'admin2@example.test')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create Administrator' }))

    expect(within(dialog).getByText('Confirm that this person should have full administrator access.'))
      .toBeInTheDocument()
    expect(createStaffAccount).not.toHaveBeenCalled()

    fireEvent.click(within(dialog).getByLabelText(CONFIRM))
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create Administrator' }))

    await waitFor(() => expect(createStaffAccount).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Second Admin', email: 'admin2@example.test', role: 'ADMIN',
    })))
    expect(await screen.findByRole('status'))
      .toHaveTextContent('Admin account created for Second Admin (admin2@example.test).')
  })

  it('asks for the confirmation again after switching roles', async () => {
    const dialog = await openDialog()

    fireEvent.click(within(dialog).getByLabelText(/^Administrator/))
    fireEvent.click(within(dialog).getByLabelText(CONFIRM))
    fireEvent.click(within(dialog).getByLabelText(/^Dispatcher/))
    expect(within(dialog).queryByLabelText(CONFIRM)).not.toBeInTheDocument()
    fireEvent.click(within(dialog).getByLabelText(/^Administrator/))

    expect(within(dialog).getByLabelText(CONFIRM)).not.toBeChecked()
  })

  it('offers only dispatcher and administrator roles', async () => {
    const dialog = await openDialog()
    const roles = within(dialog).getAllByRole('radio').map((radio) => radio.value)
    expect(roles).toEqual(['DISPATCHER', 'ADMIN'])
  })

  it('validates before calling the API', async () => {
    const dialog = await openDialog()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create Dispatcher' }))

    expect(within(dialog).getByText('Enter the full name.')).toBeInTheDocument()
    expect(within(dialog).getByText('Enter a valid email address.')).toBeInTheDocument()
    expect(within(dialog).getByText('Temporary password must be 12 to 72 characters.')).toBeInTheDocument()
    expect(createStaffAccount).not.toHaveBeenCalled()
  })

  it('keeps the dialog open and explains a duplicate email', async () => {
    createStaffAccount.mockRejectedValue(new ApiError('exists', { status: 409 }))
    const dialog = await openDialog()
    fillAccount(dialog, 'Dispatcher One', 'taken@example.test')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create Dispatcher' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('An account already uses that email address.')
    expect(screen.getByRole('dialog', { name: 'Create Staff Account' })).toBeInTheDocument()
  })

  it('closes without creating anything on Cancel', async () => {
    const dialog = await openDialog()
    fillAccount(dialog, 'Dispatcher One', 'dispatcher@example.test')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(createStaffAccount).not.toHaveBeenCalled()
  })
})
