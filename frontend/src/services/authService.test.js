import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, ApiErrorKind } from './apiClient'
import { exchangeGoogleCode, getCurrentUser, login, register } from './authService'
import { API_BASE_URL } from './config'
import { getAccessToken, getSession, saveSession } from './tokenStorage'

const USER = {
  id: 1,
  name: 'Test Customer',
  email: 'customer@example.com',
  role: 'CUSTOMER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
}

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('authService', () => {
  let fetchMock

  beforeEach(() => {
    sessionStorage.clear()
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  describe('register', () => {
    it('posts name, email and password and returns the created user', async () => {
      fetchMock.mockResolvedValue(jsonResponse(201, USER))

      await expect(register('Test Customer', 'customer@example.com', 'password123'))
        .resolves.toEqual(USER)

      const [url, init] = fetchMock.mock.calls[0]
      expect(url).toBe(`${API_BASE_URL}/api/auth/register`)
      expect(init.method).toBe('POST')
      expect(JSON.parse(init.body)).toEqual({
        name: 'Test Customer',
        email: 'customer@example.com',
        password: 'password123',
      })
      expect(init.headers.Authorization).toBeUndefined()
      expect(getSession()).toBeNull()
    })

    it('surfaces a duplicate email conflict', async () => {
      fetchMock.mockResolvedValue(jsonResponse(409, { error: 'Email is already registered' }))

      const error = await register('A', 'taken@example.com', 'password123').catch((e) => e)

      expect(error).toBeInstanceOf(ApiError)
      expect(error.status).toBe(409)
      expect(error.message).toBe('Email is already registered')
    })
  })

  describe('login', () => {
    const loginResponse = {
      accessToken: 'signed.jwt.token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: USER,
    }

    it('posts credentials, stores the token and returns the user and expiry', async () => {
      fetchMock.mockResolvedValue(jsonResponse(200, loginResponse))
      const before = Date.now()

      const result = await login('customer@example.com', 'password123')

      const [url, init] = fetchMock.mock.calls[0]
      expect(url).toBe(`${API_BASE_URL}/api/auth/login`)
      expect(init.method).toBe('POST')
      expect(JSON.parse(init.body)).toEqual({
        email: 'customer@example.com',
        password: 'password123',
      })
      expect(result.user).toEqual(USER)
      expect(result).not.toHaveProperty('accessToken')
      expect(result.expiresAt).toBeGreaterThanOrEqual(before + 900_000)
      expect(getAccessToken()).toBe('signed.jwt.token')
      expect(JSON.stringify({ ...sessionStorage })).not.toContain('password123')
    })

    it.each([
      [401, 'Invalid email or password'],
      [403, 'This account is not permitted to sign in'],
    ])('does not store a session when login fails with %i', async (status, message) => {
      fetchMock.mockResolvedValue(jsonResponse(status, { error: message }))

      const error = await login('customer@example.com', 'wrong').catch((e) => e)

      expect(error.status).toBe(status)
      expect(error.message).toBe(message)
      expect(getSession()).toBeNull()
    })

    it.each([
      ['missing token', { ...loginResponse, accessToken: undefined }],
      ['wrong token type', { ...loginResponse, tokenType: 'Basic' }],
      ['invalid expiry', { ...loginResponse, expiresIn: 0 }],
      ['missing user', { ...loginResponse, user: null }],
    ])('rejects a malformed login response (%s)', async (_label, body) => {
      fetchMock.mockResolvedValue(jsonResponse(200, body))

      const error = await login('customer@example.com', 'password123').catch((e) => e)

      expect(error).toBeInstanceOf(ApiError)
      expect(error.kind).toBe(ApiErrorKind.INVALID_RESPONSE)
      expect(getSession()).toBeNull()
    })
  })

  describe('exchangeGoogleCode', () => {
    const VERIFIER = 'v'.repeat(43)
    const loginResponse = {
      accessToken: 'google.jwt.token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: USER,
    }

    it('posts the code and verifier, stores the token and returns the user', async () => {
      fetchMock.mockResolvedValue(jsonResponse(200, loginResponse))

      const result = await exchangeGoogleCode('one-time-code', VERIFIER)

      const [url, init] = fetchMock.mock.calls[0]
      expect(url).toBe(`${API_BASE_URL}/api/auth/google/exchange`)
      expect(init.method).toBe('POST')
      expect(init.headers.Authorization).toBeUndefined()
      expect(JSON.parse(init.body)).toEqual({ code: 'one-time-code', verifier: VERIFIER })
      expect(result.user).toEqual(USER)
      expect(result).not.toHaveProperty('accessToken')
      expect(getAccessToken()).toBe('google.jwt.token')
    })

    it('does not store a session when the code is rejected', async () => {
      fetchMock.mockResolvedValue(jsonResponse(401, { error: 'Invalid or expired sign-in code' }))

      const error = await exchangeGoogleCode('used-code', VERIFIER).catch((e) => e)

      expect(error.status).toBe(401)
      expect(getSession()).toBeNull()
    })

    it('validates the response exactly like a password login', async () => {
      fetchMock.mockResolvedValue(jsonResponse(200, { ...loginResponse, tokenType: 'Basic' }))

      const error = await exchangeGoogleCode('one-time-code', VERIFIER).catch((e) => e)

      expect(error.kind).toBe(ApiErrorKind.INVALID_RESPONSE)
      expect(getSession()).toBeNull()
    })
  })

  describe('getCurrentUser', () => {
    it('requests the profile with the bearer token', async () => {
      saveSession('signed.jwt.token', 900)
      fetchMock.mockResolvedValue(jsonResponse(200, USER))

      await expect(getCurrentUser()).resolves.toEqual(USER)

      const [url, init] = fetchMock.mock.calls[0]
      expect(url).toBe(`${API_BASE_URL}/api/users/me`)
      expect(init.method).toBe('GET')
      expect(init.headers.Authorization).toBe('Bearer signed.jwt.token')
    })

    it('surfaces a rejected token', async () => {
      saveSession('stale.jwt.token', 900)
      fetchMock.mockResolvedValue(
        jsonResponse(401, { error: 'Invalid or expired access token' }))

      const error = await getCurrentUser().catch((e) => e)

      expect(error.status).toBe(401)
      expect(error.message).toBe('Invalid or expired access token')
    })

    it('does not call the server without a session', async () => {
      const error = await getCurrentUser().catch((e) => e)

      expect(error.kind).toBe(ApiErrorKind.UNAUTHENTICATED)
      expect(fetchMock).not.toHaveBeenCalled()
    })
  })
})
