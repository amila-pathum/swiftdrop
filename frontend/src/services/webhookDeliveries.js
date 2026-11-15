import { request } from './apiClient'

const base = '/api/merchants/me/webhook/deliveries'
export const webhookDeliveries = {
  list: (page, failedOnly, signal) => request(`${base}?page=${page}&size=20&failedOnly=${failedOnly}`, { auth: true, signal }),
  detail: (id, signal) => request(`${base}/${encodeURIComponent(id)}`, { auth: true, signal }),
  retry: (id) => request(`${base}/${encodeURIComponent(id)}/retry`, { auth: true, method: 'POST' }),
  test: () => request(`${base}/test`, { auth: true, method: 'POST' }),
}
