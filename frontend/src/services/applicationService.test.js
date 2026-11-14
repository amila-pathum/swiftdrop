import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiErrorKind } from './apiClient'
import { applyAsCourier, applyAsMerchant, getMyApplication } from './applicationService'
import { API_BASE_URL } from './config'
import { ENDPOINTS } from './endpoints'
import { getSession, saveSession } from './tokenStorage'

const json = (status, body) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
})

const MERCHANT = {
  businessName: 'Lanka Books',
  businessRegistrationNumber: 'PV 1',
  businessEmail: 'orders@lankabooks.lk',
  businessPhone: '0112345678',
  businessAddress: '45 Duplication Road',
  contactName: 'Nimal Perera',
  contactEmail: 'nimal@lankabooks.lk',
  password: 'password123',
  confirmPassword: 'password123',
}

const COURIER = {
  name: 'Kasun Silva',
  email: 'kasun@example.com',
  phone: '0715551234',
  address: '8 Temple Road',
  vehicleType: 'MOTORCYCLE',
  vehicleRegistrationNumber: 'WP BAA-1234',
  preferredArea: 'Kandy',
  password: 'password123',
  confirmPassword: 'password123',
}

const pendingUser = (role, email) => ({ id: 5, name: 'N', email, role, status: 'PENDING_APPROVAL' })
const loginResponse = (user) => ({
  accessToken: 'new-account-token', tokenType: 'Bearer', expiresIn: 900, user,
})

describe('merchant and courier sign-up', () => {
  let fetchMock
  const calls = () => fetchMock.mock.calls.map(([url, init]) => ({
    path: url.replace(API_BASE_URL, ''),
    body: init.body ? JSON.parse(init.body) : undefined,
    authorization: init.headers.Authorization,
  }))

  beforeEach(() => {
    ENDPOINTS.merchantApplication.available = true
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    ENDPOINTS.merchantApplication.available = true
    ENDPOINTS.courierApplication.available = false
  })

  describe('while the application endpoints are planned', () => {
    it('creates only the merchant identity account and reports the application as not submitted', async () => {
      ENDPOINTS.merchantApplication.available = false
      fetchMock.mockResolvedValue(json(201, pendingUser('MERCHANT', MERCHANT.contactEmail)))

      const result = await applyAsMerchant(MERCHANT)

      expect(calls()).toEqual([{
        path: '/api/auth/register/merchant',
        body: { name: 'Nimal Perera', email: 'nimal@lankabooks.lk', password: 'password123' },
        authorization: undefined,
      }])
      expect(result.user.role).toBe('MERCHANT')
      expect(result.applicationSubmitted).toBe(false)
      expect(result.applicationError.kind).toBe(ApiErrorKind.NOT_IMPLEMENTED)
      expect(getSession()).toBeNull()
    })

    it('creates only the courier identity account, with its profile fields', async () => {
      fetchMock.mockResolvedValue(json(201, pendingUser('COURIER', COURIER.email)))

      const result = await applyAsCourier(COURIER)

      expect(calls()).toEqual([{
        path: '/api/auth/register/courier',
        body: {
          name: 'Kasun Silva',
          email: 'kasun@example.com',
          password: 'password123',
          phone: '0715551234',
          address: '8 Temple Road',
        },
        authorization: undefined,
      }])
      expect(result.applicationSubmitted).toBe(false)
    })

    it('rejects when the account itself cannot be created', async () => {
      fetchMock.mockResolvedValue(json(409, { error: 'Email is already registered' }))

      const error = await applyAsMerchant(MERCHANT).catch((e) => e)

      expect(error.status).toBe(409)
      expect(fetchMock).toHaveBeenCalledTimes(1)
    })
  })

  describe('once the owning service publishes its endpoint', () => {
    // ENDPOINTS is frozen at the top level only; the entries can be flipped as
    // the owning team will do when the endpoint goes live.
    it('submits the business application with the new merchant\'s own token and no password', async () => {
      ENDPOINTS.merchantApplication.available = true
      const user = pendingUser('MERCHANT', MERCHANT.contactEmail)
      fetchMock
        .mockResolvedValueOnce(json(201, user))
        .mockResolvedValueOnce(json(200, loginResponse(user)))
        .mockResolvedValueOnce(json(201, { id: 1, status: 'PENDING' }))

      const result = await applyAsMerchant(MERCHANT)

      const [, login, application] = calls()
      expect(login).toEqual({
        path: '/api/auth/login',
        body: { email: 'nimal@lankabooks.lk', password: 'password123' },
        authorization: undefined,
      })
      expect(application).toEqual({
        path: '/api/merchants/register',
        body: {
          businessName: 'Lanka Books',
          registrationNumber: 'PV 1',
          contactName: 'Nimal Perera',
          email: 'orders@lankabooks.lk',
          phone: '0112345678',
          address: '45 Duplication Road',
        },
        authorization: 'Bearer new-account-token',
      })
      expect(result).toMatchObject({ applicationSubmitted: true, applicationError: null })
      // The applicant signs in normally afterwards.
      expect(getSession()).toBeNull()
    })

    it('loads merchant application status from the business profile and maps review fields', async () => {
      saveSession('pending-merchant-token', 900)
      fetchMock.mockResolvedValue(json(200, { status: 'REJECTED', createdAt: '2026-11-14T00:00:00Z', rejectionReason: 'Please correct registration details' }))
      const controller = new AbortController()
      expect(await getMyApplication('MERCHANT', { signal: controller.signal })).toEqual({
        status: 'REJECTED', submittedAt: '2026-11-14T00:00:00Z', reviewNote: 'Please correct registration details',
      })
      expect(calls()[0].path).toBe('/api/merchants/me')
      expect(calls()[0].authorization).toBe('Bearer pending-merchant-token')
      expect(fetchMock.mock.calls[0][1].signal).toBe(controller.signal)
    })

    it('preserves a missing application response so an existing account can complete its profile', async () => {
      saveSession('pending-merchant-token', 900)
      fetchMock.mockResolvedValue(json(404, { error: 'Merchant application not found' }))
      await expect(getMyApplication('MERCHANT')).rejects.toMatchObject({ status: 404 })
    })

    it('submits the courier application with vehicle details only', async () => {
      ENDPOINTS.courierApplication.available = true
      const user = pendingUser('COURIER', COURIER.email)
      fetchMock
        .mockResolvedValueOnce(json(201, user))
        .mockResolvedValueOnce(json(200, loginResponse(user)))
        .mockResolvedValueOnce(json(201, { id: 1, status: 'PENDING' }))

      await applyAsCourier(COURIER)

      expect(calls()[2]).toEqual({
        path: '/api/couriers/applications',
        body: {
          vehicleType: 'MOTORCYCLE',
          vehicleRegistrationNumber: 'WP BAA-1234',
          preferredArea: 'Kandy',
        },
        authorization: 'Bearer new-account-token',
      })
    })

    it('keeps the account and reports a failed application without throwing', async () => {
      ENDPOINTS.merchantApplication.available = true
      const user = pendingUser('MERCHANT', MERCHANT.contactEmail)
      fetchMock
        .mockResolvedValueOnce(json(201, user))
        .mockResolvedValueOnce(json(200, loginResponse(user)))
        .mockResolvedValueOnce(json(503, { error: 'down' }))

      const result = await applyAsMerchant(MERCHANT)

      expect(result.user).toEqual(user)
      expect(result.applicationSubmitted).toBe(false)
      expect(result.applicationError.kind).toBe(ApiErrorKind.SERVICE_UNAVAILABLE)
      expect(getSession()).toBeNull()
    })
  })
})
