import { request } from './apiClient'

export const merchantService = {
  // Merchant self-profile
  getMyMerchant: (signal) =>
    request('/api/merchants/me', { auth: true, signal }),

  // Merchant Dashboard statistics and real orders
  getMyDashboard: (signal) =>
    request('/api/merchants/me/dashboard', { auth: true, signal }),

  // Admin approvals
  getAllMerchants: (status, signal) => {
    const query = status ? `?status=${encodeURIComponent(status)}` : ''
    return request(`/api/merchants/admin${query}`, { auth: true, signal })
  },

  getMerchantById: (merchantId, signal) =>
    request(`/api/merchants/admin/${encodeURIComponent(merchantId)}`, { auth: true, signal }),

  approveMerchant: (merchantId) =>
    request(`/api/merchants/admin/${encodeURIComponent(merchantId)}/approve`, {
      method: 'PATCH',
      auth: true,
    }),

  rejectMerchant: (merchantId, reason) =>
    request(`/api/merchants/admin/${encodeURIComponent(merchantId)}/reject`, {
      method: 'PATCH',
      body: { reason },
      auth: true,
    }),

  // API Key management
  getApiKeys: (signal) =>
    request('/api/merchants/me/api-keys', { auth: true, signal }),

  createApiKey: (name) =>
    request('/api/merchants/me/api-keys', {
      method: 'POST',
      body: { name },
      auth: true,
    }),

  revokeApiKey: (keyId) =>
    request(`/api/merchants/me/api-keys/${encodeURIComponent(keyId)}`, {
      method: 'DELETE',
      auth: true,
    }),

  // Webhook settings
  getWebhookSettings: (signal) =>
    request('/api/merchants/me/webhook', { auth: true, signal }),

  updateWebhookSettings: (url, enabled) =>
    request('/api/merchants/me/webhook', {
      method: 'PUT',
      body: { url, enabled },
      auth: true,
    }),

  rotateWebhookSecret: () =>
    request('/api/merchants/me/webhook/rotate-secret', {
      method: 'POST',
      auth: true,
    }),
}

export default merchantService

