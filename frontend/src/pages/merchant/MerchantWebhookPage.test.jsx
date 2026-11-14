import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MerchantWebhookPage from './MerchantWebhookPage'
import { merchantService } from '../../services/merchantService'
import { webhookDeliveries } from '../../services/webhookDeliveries'

vi.mock('../../services/merchantService', () => ({ merchantService: {
  getMyMerchant: vi.fn(), getWebhookSettings: vi.fn(), updateWebhookSettings: vi.fn(), rotateWebhookSecret: vi.fn(), registerMyMerchant: vi.fn(),
} }))
vi.mock('../../services/webhookDeliveries', () => ({ webhookDeliveries: { test: vi.fn() } }))
const merchant = { id: 1, businessName: 'Demo store', status: 'APPROVED' }
const settings = { url: 'https://store.example/hooks/swiftdrop', enabled: true, updatedAt: '2026-11-10T12:00:00Z' }
const show = () => render(<MemoryRouter><MerchantWebhookPage /></MemoryRouter>)
const loaded = () => screen.findByDisplayValue(settings.url)

beforeEach(() => {
  vi.resetAllMocks()
  merchantService.getMyMerchant.mockResolvedValue(merchant)
  merchantService.getWebhookSettings.mockResolvedValue(settings)
  Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } })
  Object.assign(HTMLDialogElement.prototype, {
    showModal() { this.setAttribute('open', '') }, close() { this.removeAttribute('open') },
  })
})

describe('Merchant webhook settings', () => {
  const businessDetails = {
    businessName: 'Example Store', registrationNumber: 'BR-001', contactName: 'Store Owner',
    email: 'owner@example.test', phone: '0771234567', address: '123 Example Street',
  }
  async function fillProfile() {
    await screen.findByRole('heading', { name: 'Set up your business profile' })
    for (const [name, value] of Object.entries(businessDetails)) {
      fireEvent.change(document.getElementById(`merchant-setup-${name}`), { target: { value: ` ${value} ` } })
    }
  }

  it('registers a missing business profile for the current account and waits for approval', async () => {
    merchantService.getMyMerchant.mockRejectedValue({ status: 404, message: 'Not Found' })
    merchantService.registerMyMerchant.mockResolvedValue({ ...merchant, status: 'PENDING' })
    show(); await fillProfile()
    fireEvent.click(screen.getByRole('button', { name: 'Submit business profile' }))
    await screen.findByText(/Business profile submitted/)
    expect(merchantService.registerMyMerchant).toHaveBeenCalledWith(businessDetails)
    expect(merchantService.getWebhookSettings).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Send Test Event' })).toBeDisabled()
    expect(screen.getByText(/business profile status is PENDING/)).toBeInTheDocument()
  })

  it('validates the profile and submits only once while registration is pending', async () => {
    merchantService.getMyMerchant.mockRejectedValue({ status: 404 })
    let finish
    merchantService.registerMyMerchant.mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    show()
    const submit = await screen.findByRole('button', { name: 'Submit business profile' })
    fireEvent.click(submit)
    expect(screen.getByRole('alert')).toHaveTextContent('Complete all business profile fields.')
    expect(merchantService.registerMyMerchant).not.toHaveBeenCalled()
    await fillProfile()
    fireEvent.click(submit); fireEvent.click(submit)
    expect(merchantService.registerMyMerchant).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: /Submitting/ })).toBeDisabled()
    finish({ ...merchant, status: 'PENDING' })
    await screen.findByText(/Business profile submitted/)
  })

  it('can check an existing profile after a registration conflict without another POST', async () => {
    merchantService.getMyMerchant.mockRejectedValueOnce({ status: 404 }).mockResolvedValue(merchant)
    merchantService.registerMyMerchant.mockRejectedValue({ status: 409 })
    show(); await fillProfile()
    fireEvent.click(screen.getByRole('button', { name: 'Submit business profile' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('already exists')
    fireEvent.click(screen.getByRole('button', { name: 'Check profile status' }))
    await loaded()
    expect(merchantService.registerMyMerchant).toHaveBeenCalledTimes(1)
  })

  it('keeps business details available after registration fails', async () => {
    merchantService.getMyMerchant.mockRejectedValue({ status: 404 })
    merchantService.registerMyMerchant.mockRejectedValue(new Error('Service unavailable'))
    show(); await fillProfile()
    fireEvent.click(screen.getByRole('button', { name: 'Submit business profile' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Service unavailable')
    expect(screen.getByLabelText(/Business name/)).toHaveValue(' Example Store ')
    expect(screen.getByRole('button', { name: 'Submit business profile' })).toBeEnabled()
  })

  it('shows the two webhook views and configured settings without revealing the stored secret', async () => {
    show(); await loaded()
    expect(screen.getByRole('heading', { level: 1, name: 'Webhooks' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Delivery History' })).toHaveAttribute('href', '/merchant/webhook-deliveries')
    expect(screen.getByRole('switch', { name: 'Endpoint enabled' })).toBeChecked()
    expect(screen.getByLabelText('Signing secret hidden')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Copy' })).not.toBeInTheDocument()
    expect(screen.getByText('Status-change events')).toBeInTheDocument()
  })

  it('treats missing configuration as an empty form and disables testing until saved', async () => {
    merchantService.getWebhookSettings.mockRejectedValue({ status: 404 })
    show()
    expect(await screen.findByText(/A secret will be generated automatically/)).toBeInTheDocument()
    expect(screen.getByLabelText(/Endpoint URL/)).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Send Test Event' })).toBeDisabled()
  })

  it('saves the endpoint and switch, then allows copying the one-time secret', async () => {
    merchantService.updateWebhookSettings.mockResolvedValue({ settings: { ...settings, enabled: false }, signingSecret: 'whsec_new' })
    show(); await loaded()
    fireEvent.click(screen.getByRole('switch'))
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText('whsec_new')).toBeInTheDocument()
    expect(merchantService.updateWebhookSettings).toHaveBeenCalledWith(settings.url, false)
    fireEvent.click(screen.getByRole('button', { name: 'Copy' }))
    await screen.findByRole('button', { name: 'Copied' })
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('whsec_new')
    expect(screen.getByRole('button', { name: 'Send Test Event' })).toBeDisabled()
  })

  it.each(['http://insecure.example/hooks', 'https://', 'https://user:password@store.example/hooks', 'https://store.example/#fragment'])(
    'rejects invalid endpoint %s without saving', async (url) => {
      show(); await loaded()
      fireEvent.change(screen.getByLabelText(/Endpoint URL/), { target: { value: url } })
      fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
      expect(screen.getByRole('alert')).toHaveTextContent('Webhook URL must be a valid HTTPS URL.')
      expect(merchantService.updateWebhookSettings).not.toHaveBeenCalled()
    },
  )

  it('queues a test once and describes it as queued, not received', async () => {
    let finish
    webhookDeliveries.test.mockImplementation(() => new Promise((resolve) => { finish = resolve }))
    show(); await loaded()
    const button = screen.getByRole('button', { name: 'Send Test Event' })
    fireEvent.click(button); fireEvent.click(button)
    expect(webhookDeliveries.test).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: /Queuing/ })).toBeDisabled()
    finish({ state: 'PENDING' })
    expect(await screen.findByRole('status')).toHaveTextContent('Test event queued. Open Delivery History to check the receiver outcome.')
  })

  it('requires saving URL changes before a test and re-enables testing after saving', async () => {
    const nextSettings = { ...settings, url: 'https://store.example/new-hook' }
    merchantService.updateWebhookSettings.mockResolvedValue({ settings: nextSettings })
    show(); await loaded()
    fireEvent.change(screen.getByLabelText(/Endpoint URL/), { target: { value: nextSettings.url } })
    expect(screen.getByRole('button', { name: 'Send Test Event' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Send Test Event' })).toBeEnabled())
  })

  it('reports a failed test request without claiming the webhook was delivered', async () => {
    webhookDeliveries.test.mockRejectedValue(new Error('Notification service unavailable'))
    show(); await loaded()
    fireEvent.click(screen.getByRole('button', { name: 'Send Test Event' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Notification service unavailable')
    expect(screen.queryByText(/Test event queued/)).not.toBeInTheDocument()
  })

  it('keeps a failed secret rotation open and supports a successful retry', async () => {
    merchantService.rotateWebhookSecret.mockRejectedValueOnce(new Error('Try again later'))
      .mockResolvedValue({ settings, signingSecret: 'whsec_rotated' })
    show(); await loaded()
    fireEvent.click(screen.getByRole('button', { name: 'Rotate secret' }))
    const dialog = screen.getByRole('dialog')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Rotate secret' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Try again later')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Rotate secret' }))
    expect(await screen.findByText('whsec_rotated')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('does not claim a copy succeeded if clipboard access fails', async () => {
    merchantService.updateWebhookSettings.mockResolvedValue({ settings, signingSecret: 'whsec_new' })
    navigator.clipboard.writeText.mockRejectedValue(new Error('Denied'))
    show(); await loaded()
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Copy' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to copy the secret.')
    expect(screen.queryByRole('button', { name: 'Copied' })).not.toBeInTheDocument()
  })

  it('disables settings actions for merchants awaiting approval', async () => {
    merchantService.getMyMerchant.mockResolvedValue({ ...merchant, status: 'PENDING' })
    show()
    await screen.findByText('Webhook settings require business approval')
    for (const name of ['Save changes', 'Rotate secret', 'Send Test Event']) expect(screen.getByRole('button', { name })).toBeDisabled()
    expect(merchantService.getWebhookSettings).not.toHaveBeenCalled()
  })

  it('recovers from a settings lookup outage without treating it as missing settings', async () => {
    merchantService.getWebhookSettings.mockRejectedValueOnce(new Error('Settings unavailable')).mockResolvedValue(settings)
    show()
    expect(await screen.findByRole('alert')).toHaveTextContent('Settings unavailable')
    expect(screen.queryByText(/A secret will be generated/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await loaded()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
