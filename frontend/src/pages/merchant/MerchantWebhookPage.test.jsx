import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MerchantWebhookPage from './MerchantWebhookPage'
import { merchantService } from '../../services/merchantService'

vi.mock('../../services/merchantService', () => ({
  merchantService: {
    getMyMerchant: vi.fn(),
    getWebhookSettings: vi.fn(),
    updateWebhookSettings: vi.fn(),
    rotateWebhookSecret: vi.fn(),
  },
}))

const MOCK_MERCHANT = {
  id: 1,
  businessName: 'UrbanNest Commerce',
  status: 'APPROVED',
}

const MOCK_WEBHOOK = {
  url: 'https://api.urbannest.co/webhooks/swiftdrop',
  enabled: true,
  updatedAt: '2026-11-10T12:00:00Z',
}

describe('MerchantWebhookPage', () => {
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
        <MerchantWebhookPage />
      </MemoryRouter>,
    )
  }

  it('renders configured webhook settings with masked secret and delivery events', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getWebhookSettings.mockResolvedValue(MOCK_WEBHOOK)

    renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Webhook Settings' })).toBeInTheDocument()

    await waitFor(() => {
      expect(screen.getByDisplayValue('https://api.urbannest.co/webhooks/swiftdrop')).toBeInTheDocument()
    })

    expect(screen.getByText('Enabled')).toBeInTheDocument()
    expect(screen.getByText(/whsec_••••••••••••••••••••••••••••/)).toBeInTheDocument()
    expect(screen.getByText('Delivery events')).toBeInTheDocument()
    expect(screen.getByText('parcel.created')).toBeInTheDocument()
    expect(screen.getByText('parcel.delivered')).toBeInTheDocument()
  })

  it('handles unconfigured webhook settings (404) gracefully as empty form', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getWebhookSettings.mockRejectedValue({ status: 404, message: 'Not configured' })

    renderPage()

    await waitFor(() => {
      expect(screen.getByPlaceholderText('https://api.urbannest.co/webhooks/swiftdrop')).toBeInTheDocument()
    })

    expect(screen.getByText(/A secret will be generated automatically when you save/)).toBeInTheDocument()
  })

  it('saves webhook settings and displays new secret if returned', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getWebhookSettings.mockResolvedValue(MOCK_WEBHOOK)
    merchantService.updateWebhookSettings.mockResolvedValue({
      settings: {
        url: 'https://api.urbannest.co/webhooks/new',
        enabled: true,
        updatedAt: '2026-11-13T10:00:00Z',
      },
      signingSecret: 'whsec_new_generated_secret_key',
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByDisplayValue('https://api.urbannest.co/webhooks/swiftdrop')).toBeInTheDocument()
    })

    const input = screen.getByLabelText(/Webhook Endpoint URL/)
    fireEvent.change(input, { target: { value: 'https://api.urbannest.co/webhooks/new' } })

    fireEvent.click(screen.getByRole('button', { name: 'Save Settings' }))

    await waitFor(() => {
      expect(merchantService.updateWebhookSettings).toHaveBeenCalledWith(
        'https://api.urbannest.co/webhooks/new',
        true,
      )
    })

    expect(screen.getByText(/Webhook settings saved/)).toBeInTheDocument()
    expect(screen.getByText('whsec_new_generated_secret_key')).toBeInTheDocument()

    // Test Copy button for secret
    const copyBtn = screen.getByRole('button', { name: 'Copy' })
    fireEvent.click(copyBtn)
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('whsec_new_generated_secret_key')
  })

  it('validates that URL must start with https://', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getWebhookSettings.mockResolvedValue(MOCK_WEBHOOK)

    renderPage()

    await waitFor(() => {
      expect(screen.getByDisplayValue('https://api.urbannest.co/webhooks/swiftdrop')).toBeInTheDocument()
    })

    const input = screen.getByLabelText(/Webhook Endpoint URL/)
    fireEvent.change(input, { target: { value: 'http://insecure.url.com' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save Settings' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Webhook URL must be a valid HTTPS URL.')
    expect(merchantService.updateWebhookSettings).not.toHaveBeenCalled()
  })

  it('rotates signing secret after confirmation', async () => {
    merchantService.getMyMerchant.mockResolvedValue(MOCK_MERCHANT)
    merchantService.getWebhookSettings.mockResolvedValue(MOCK_WEBHOOK)
    merchantService.rotateWebhookSecret.mockResolvedValue({
      settings: { ...MOCK_WEBHOOK, updatedAt: '2026-11-13T12:00:00Z' },
      signingSecret: 'whsec_rotated_secret_999',
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Rotate Secret' })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: 'Rotate Secret' }))

    const modal = screen.getByRole('dialog')
    expect(modal).toBeInTheDocument()
    expect(modal).toHaveTextContent(/invalidate/i)

    const confirmBtn = screen.getAllByRole('button', { name: 'Rotate Secret' })[1]
    fireEvent.click(confirmBtn)

    await waitFor(() => {
      expect(merchantService.rotateWebhookSecret).toHaveBeenCalled()
    })

    expect(screen.getByText('whsec_rotated_secret_999')).toBeInTheDocument()
  })

  it('disables saving and rotating when merchant is not approved', async () => {
    merchantService.getMyMerchant.mockResolvedValue({
      ...MOCK_MERCHANT,
      status: 'PENDING',
    })
    merchantService.getWebhookSettings.mockResolvedValue(null)

    renderPage()

    await waitFor(() => {
      expect(screen.getByText(/Webhook settings require business approval/)).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: 'Save Settings' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Rotate Secret' })).toBeDisabled()
  })
})