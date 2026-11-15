import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MerchantApiKeysPage from './MerchantApiKeysPage'
import { merchantService } from '../../services/merchantService'

vi.mock('../../services/merchantService', () => ({
  merchantService: {
    getMyMerchant: vi.fn(),
    getApiKeys: vi.fn(),
    createApiKey: vi.fn(),
    revokeApiKey: vi.fn(),
  },
}))

const MOCK_MERCHANT = {
  id: 1,
  businessName: 'UrbanNest Commerce',
  status: 'APPROVED',
}

const MOCK_KEYS = [
  {
    id: 101,
    name: 'Storefront integration',
    prefix: 'sd_live_7xK9',
    createdAt: '2026-10-24T10:00:00Z',
    lastUsedAt: '2026-11-12T10:00:00Z',
    revokedAt: null,
  },
  {
    id: 102,
    name: 'Legacy connector',
    prefix: 'sd_live_8nT1',
    createdAt: '2026-09-15T10:00:00Z',
    lastUsedAt: null,
    revokedAt: '2026-10-20T10:00:00Z',
  },
]

describe('MerchantApiKeysPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function renderPage() {
    return render(
      <MemoryRouter>
        <MerchantApiKeysPage />
      </MemoryRouter>,
    )
  }

  it('renders header, approval banner and keys list', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getApiKeys.mockResolvedValue(MOCK_KEYS)

    renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'API key management' })).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByText('Storefront integration')).toBeInTheDocument()
    })

    expect(screen.getByText(/Your business is approved for API access/)).toBeInTheDocument()
    expect(screen.getByText('sd_live_7xK9••••')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByText('Revoked')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Revoke' })).toBeInTheDocument()
  })

  it('disables key generation and shows warning when merchant is not approved', async () => {
    merchantService.getMyMerchant.mockResolvedValue({
      ...MOCK_MERCHANT,
      status: 'PENDING',
    })
    merchantService.getApiKeys.mockResolvedValue([])

    renderPage()

    await waitFor(() => {
      expect(screen.getByText(/API key actions require business approval/)).toBeInTheDocument()
    })

    const generateBtn = screen.getByRole('button', { name: '+ Generate New API Key' })
    expect(generateBtn).toBeDisabled()
  })

  it('generates a new key and displays the ready modal matching Figma design', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getApiKeys.mockResolvedValue(MOCK_KEYS)
    merchantService.createApiKey.mockResolvedValue({
      apiKey: { id: 103, name: 'Warehouse sync', prefix: 'sd_live_2mP4' },
      secretKey: 'sd_live_2mP4_test_secret_key_12345',
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Storefront integration')).toBeInTheDocument()
    })

    // Click generate button
    fireEvent.click(screen.getByRole('button', { name: '+ Generate New API Key' }))

    // Fill form
    const input = screen.getByLabelText(/Key Name/)
    fireEvent.change(input, { target: { value: 'Warehouse sync' } })
    fireEvent.click(screen.getByRole('button', { name: 'Generate Key' }))

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Your API key is ready' })).toBeInTheDocument()
    })

    expect(screen.getByText(/Copy this key now. You won't see it again./)).toBeInTheDocument()
    expect(screen.getByDisplayValue('sd_live_2mP4_test_secret_key_12345')).toBeInTheDocument()

    // Test Copy button
    const copyBtn = screen.getByRole('button', { name: 'Copy Key' })
    fireEvent.click(copyBtn)
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('sd_live_2mP4_test_secret_key_12345')

    // Close ready modal
    fireEvent.click(screen.getByRole('button', { name: "I've saved my key" }))
    expect(screen.queryByRole('heading', { name: 'Your API key is ready' })).not.toBeInTheDocument()
  })

  it('revokes an active API key after confirmation', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getApiKeys.mockResolvedValue(MOCK_KEYS)
    merchantService.revokeApiKey.mockResolvedValue(null)

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('Storefront integration')).toBeInTheDocument()
    })

    // Click Revoke button
    fireEvent.click(screen.getByRole('button', { name: 'Revoke' }))

    expect(screen.getByRole('heading', { name: 'Revoke API Key' })).toBeInTheDocument()
    expect(screen.getByText(/Are you sure you want to revoke key/)).toBeInTheDocument()

    // Confirm revoke
    const confirmBtn = screen.getByRole('button', { name: 'Revoke Key' })
    fireEvent.click(confirmBtn)

    await waitFor(() => {
      expect(merchantService.revokeApiKey).toHaveBeenCalledWith(101)
    })
  })

  it('renders empty state when there are no keys', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getApiKeys.mockResolvedValue([])

    renderPage()

    await waitFor(() => {
      expect(screen.getByText('No API keys yet')).toBeInTheDocument()
    })
  })

  it('renders example order request card and copies curl command', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getApiKeys.mockResolvedValue(MOCK_KEYS)

    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Example Order Request' })).toBeInTheDocument()
    })

    expect(screen.getByText(/curl -X POST https:\/\/api\.swiftdrop\.com\/api\/merchant\/orders/)).toBeInTheDocument()
    expect(screen.getByText(/X-API-Key: sd_live_YOUR_KEY_HERE/)).toBeInTheDocument()

    const copyBtn = screen.getByRole('button', { name: 'Copy cURL snippet' })
    fireEvent.click(copyBtn)

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(expect.stringContaining('POST https://api.swiftdrop.com/api/merchant/orders'))
    expect(screen.getByText('✓ Copied')).toBeInTheDocument()
  })
})

