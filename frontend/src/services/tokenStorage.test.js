import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearSession,
  getAccessToken,
  getSession,
  isSessionExpired,
  saveSession,
} from './tokenStorage'

const NOW = 1_700_000_000_000

describe('tokenStorage', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('saves the token with an expiry timestamp in sessionStorage', () => {
    const expiresAt = saveSession('token-123', 900, NOW)

    expect(expiresAt).toBe(NOW + 900_000)
    expect(getSession()).toEqual({ accessToken: 'token-123', expiresAt: NOW + 900_000 })
    expect(sessionStorage.length).toBe(2)
    expect(localStorage.length).toBe(0)
  })

  it('returns the token only before it expires', () => {
    saveSession('token-123', 900, NOW)

    expect(getAccessToken(NOW + 899_999)).toBe('token-123')
    expect(isSessionExpired(NOW + 899_999)).toBe(false)
    expect(getAccessToken(NOW + 900_000)).toBeNull()
    expect(isSessionExpired(NOW + 900_000)).toBe(true)
  })

  it('treats a missing session as expired', () => {
    expect(getSession()).toBeNull()
    expect(getAccessToken()).toBeNull()
    expect(isSessionExpired()).toBe(true)
  })

  it('clears the session', () => {
    saveSession('token-123', 900, NOW)
    clearSession()

    expect(getSession()).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })

  it.each([
    ['non-numeric expiry', 'token-123', 'soon'],
    ['negative expiry', 'token-123', '-5'],
    ['missing expiry', 'token-123', null],
    ['missing token', null, String(NOW)],
  ])('discards corrupt stored values (%s)', (_label, token, expiresAt) => {
    if (token !== null) sessionStorage.setItem('swiftdrop.accessToken', token)
    if (expiresAt !== null) sessionStorage.setItem('swiftdrop.tokenExpiresAt', expiresAt)

    expect(getSession()).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })

  it.each([
    ['', 900],
    ['   ', 900],
    [undefined, 900],
    ['token-123', 0],
    ['token-123', -1],
    ['token-123', Number.NaN],
    ['token-123', '900'],
  ])('rejects invalid input (token=%j, expiresIn=%s)', (token, expiresIn) => {
    expect(() => saveSession(token, expiresIn, NOW)).toThrow(TypeError)
    expect(sessionStorage.length).toBe(0)
  })

  it('fails loudly when storage cannot be written', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError')
    })

    expect(() => saveSession('token-123', 900, NOW)).toThrow(/Unable to store the session/)
    expect(getSession()).toBeNull()
  })

  it('returns null when storage cannot be read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })

    expect(getSession()).toBeNull()
    expect(isSessionExpired()).toBe(true)
  })

  describe('remember me', () => {
    it('keeps a persistent session in localStorage only', () => {
      saveSession('remembered', 900, NOW, { persistent: true })

      expect(localStorage.length).toBe(2)
      expect(sessionStorage.length).toBe(0)
      expect(getSession()).toEqual({ accessToken: 'remembered', expiresAt: NOW + 900_000 })
      expect(getAccessToken(NOW + 1)).toBe('remembered')
    })

    it('still expires a persistent session with the token', () => {
      saveSession('remembered', 900, NOW, { persistent: true })
      expect(getAccessToken(NOW + 900_000)).toBeNull()
      expect(isSessionExpired(NOW + 900_000)).toBe(true)
    })

    it('replaces a session in the other store when signing in again', () => {
      saveSession('remembered', 900, NOW, { persistent: true })
      saveSession('tab-only', 900, NOW)

      expect(localStorage.length).toBe(0)
      expect(getSession().accessToken).toBe('tab-only')

      saveSession('remembered-again', 900, NOW, { persistent: true })
      expect(sessionStorage.length).toBe(0)
      expect(getSession().accessToken).toBe('remembered-again')
    })

    it('clears both stores on sign-out', () => {
      sessionStorage.setItem('swiftdrop.accessToken', 'a')
      localStorage.setItem('swiftdrop.accessToken', 'b')
      clearSession()

      expect(sessionStorage.length).toBe(0)
      expect(localStorage.length).toBe(0)
    })
  })
})
