import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminDashboardPage from './AdminDashboardPage'
import { useAuth } from '../../context/AuthContext'
import { dispatchService } from '../../services/dispatchService'
import { merchantService } from '../../services/merchantService'
import { parcelService } from '../../services/parcelService'

vi.mock('../../context/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../../services/dispatchService', () => ({
  dispatchService: { getSummary: vi.fn(), getCouriers: vi.fn() },
}))
vi.mock('../../services/merchantService', () => ({
  merchantService: { getAllMerchants: vi.fn() },
}))
vi.mock('../../services/parcelService', () => ({
  parcelService: { listForDispatch: vi.fn() },
}))

const renderPage = () => render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>)
const kpi = (label) => screen.getByText(label).closest('article').querySelector('.admin-kpi-value')

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    useAuth.mockReturnValue({ user: { name: 'Admin', role: 'ADMIN', status: 'ACTIVE' } })
    dispatchService.getSummary.mockResolvedValue({
      deliveriesPerDay: [{ date: '2026-11-20', total: 3, delivered: 2, failed: 1 }],
      successRate: 66.67,
    })
    dispatchService.getCouriers.mockResolvedValue([{ active: true }, { active: false }])
    merchantService.getAllMerchants.mockResolvedValue([{ businessName: 'Lanka Books' }])
    parcelService.listForDispatch.mockResolvedValue([
      { status: 'IN_TRANSIT', createdAt: new Date().toISOString() },
    ])
  })
  afterEach(() => vi.resetAllMocks())

  it('shows the operational overview', async () => {
    renderPage()

    expect(await screen.findByText('Active couriers')).toBeInTheDocument()
    expect(kpi('Active couriers')).toHaveTextContent('1')
    expect(kpi('Pending merchant approvals')).toHaveTextContent('1')
    expect(kpi('Deliveries today')).toHaveTextContent('1')
    expect(screen.getByText('66.7%')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Review' })).toHaveAttribute('href', '/admin/merchant-approvals')
  })

  it('no longer creates accounts; that lives in User Management', async () => {
    renderPage()
    await screen.findByText('Active couriers')

    expect(screen.queryByText(/create staff account/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/temporary password/i)).not.toBeInTheDocument()
  })
})
