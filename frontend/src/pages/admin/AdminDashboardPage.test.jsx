import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminDashboardPage from './AdminDashboardPage'
import { useAuth } from '../../context/AuthContext'
import { createDispatcher } from '../../services/adminUserService'

vi.mock('../../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../../services/adminUserService', () => ({ createDispatcher: vi.fn() }))

describe('AdminDashboardPage', () => {
  afterEach(() => vi.resetAllMocks())

  it('creates a dispatcher with a fixed role and clears the password', async () => {
    useAuth.mockReturnValue({ user: { name: 'Admin', role: 'ADMIN', status: 'ACTIVE' } })
    createDispatcher.mockResolvedValue({
      id: 7, name: 'Dispatcher One', email: 'dispatcher@example.test',
      role: 'DISPATCHER', status: 'ACTIVE',
    })
    render(<AdminDashboardPage />)

    fireEvent.change(screen.getByLabelText(/Full name/), { target: { value: 'Dispatcher One' } })
    fireEvent.change(screen.getByLabelText(/^Email/), { target: { value: 'dispatcher@example.test' } })
    fireEvent.change(screen.getByLabelText(/Temporary password/), {
      target: { value: 'temporary-pass-123' },
    })
    expect(screen.getByLabelText('Role')).toHaveValue('DISPATCHER')
    fireEvent.click(screen.getByRole('button', { name: 'Create dispatcher' }))

    await waitFor(() => expect(createDispatcher).toHaveBeenCalledWith({
      name: 'Dispatcher One', email: 'dispatcher@example.test', password: 'temporary-pass-123',
    }))
    expect(await screen.findByRole('status')).toHaveTextContent('was created successfully')
    expect(screen.getByLabelText(/Temporary password/)).toHaveValue('')
  })

  it('validates before calling the API', () => {
    useAuth.mockReturnValue({ user: { role: 'ADMIN', status: 'ACTIVE' } })
    render(<AdminDashboardPage />)
    fireEvent.click(screen.getByRole('button', { name: 'Create dispatcher' }))
    expect(screen.getByText('Enter the dispatcher’s full name.')).toBeInTheDocument()
    expect(createDispatcher).not.toHaveBeenCalled()
  })
})
