// Node's own SHA-256, independent of the Web Crypto code under test.
import { createHash } from 'node:crypto'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE_URL } from './config'
import {
  challengeFor,
  createVerifier,
  googleAuthorizationUrl,
  startGoogleSignIn,
  takePendingGoogleSignIn,
} from './googleAuth'

const PENDING_KEY = 'swiftdrop.googleSignIn'

describe('googleAuth', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('PKCE', () => {
    it('creates 43-character base64url verifiers from 32 random bytes', () => {
      const spy = vi.spyOn(crypto, 'getRandomValues')
      const verifiers = new Set(Array.from({ length: 50 }, createVerifier))

      expect(verifiers.size).toBe(50)
      for (const verifier of verifiers) {
        expect(verifier).toMatch(/^[A-Za-z0-9_-]{43}$/)
      }
      expect(spy.mock.calls[0][0]).toHaveLength(32)
    })

    it('derives the S256 challenge: base64url(SHA-256(verifier)) without padding', async () => {
      for (const verifier of [createVerifier(), createVerifier(), 'a'.repeat(128)]) {
        const expected = createHash('sha256').update(verifier, 'ascii').digest('base64url')

        await expect(challengeFor(verifier)).resolves.toBe(expected)
      }
    })
  })

  describe('authorization URL', () => {
    it('points at the backend through the API Gateway with only the challenge', () => {
      const url = new URL(googleAuthorizationUrl('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM'))

      expect(`${url.origin}${url.pathname}`).toBe(`${API_BASE_URL}/oauth2/authorization/google`)
      expect([...url.searchParams.keys()]).toEqual(['client_challenge'])
      expect(url.searchParams.get('client_challenge'))
        .toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM')
    })
  })

  describe('startGoogleSignIn', () => {
    it('stores the verifier and return path, then navigates with the matching challenge', async () => {
      const navigate = vi.fn()

      await startGoogleSignIn('/profile', navigate)

      const stored = JSON.parse(sessionStorage.getItem(PENDING_KEY))
      expect(stored.returnTo).toBe('/profile')
      expect(navigate).toHaveBeenCalledTimes(1)
      const url = new URL(navigate.mock.calls[0][0])
      expect(url.searchParams.get('client_challenge')).toBe(await challengeFor(stored.verifier))
      // The verifier itself never leaves the browser before the exchange.
      expect(navigate.mock.calls[0][0]).not.toContain(stored.verifier)
    })

    it('stores nothing and does not navigate when Web Crypto fails', async () => {
      vi.spyOn(crypto.subtle, 'digest').mockRejectedValue(new Error('insecure context'))
      const navigate = vi.fn()

      await expect(startGoogleSignIn('/dashboard', navigate)).rejects.toThrow()

      expect(sessionStorage.getItem(PENDING_KEY)).toBeNull()
      expect(navigate).not.toHaveBeenCalled()
    })
  })

  describe('takePendingGoogleSignIn', () => {
    const now = Date.parse('2026-06-01T12:00:00Z')
    const verifier = 'a'.repeat(43)
    const store = (value) => sessionStorage.setItem(PENDING_KEY,
      typeof value === 'string' ? value : JSON.stringify(value))

    it('returns the pending sign-in once and removes it', () => {
      store({ verifier, returnTo: '/profile', startedAt: now - 60_000 })

      expect(takePendingGoogleSignIn(now)).toEqual({ verifier, returnTo: '/profile' })
      expect(sessionStorage.getItem(PENDING_KEY)).toBeNull()
      expect(takePendingGoogleSignIn(now)).toBeNull()
    })

    it('returns null when nothing is pending', () => {
      expect(takePendingGoogleSignIn(now)).toBeNull()
    })

    it.each([
      ['corrupt JSON', '{not json'],
      ['missing verifier', { returnTo: '/', startedAt: now }],
      ['malformed verifier', { verifier: 'short', returnTo: '/', startedAt: now }],
      ['missing start time', { verifier, returnTo: '/' }],
      ['older than ten minutes', { verifier, returnTo: '/', startedAt: now - 600_001 }],
      ['started in the future', { verifier, returnTo: '/', startedAt: now + 60_000 }],
    ])('rejects and removes a pending sign-in with %s', (_label, value) => {
      store(value)

      expect(takePendingGoogleSignIn(now)).toBeNull()
      expect(sessionStorage.getItem(PENDING_KEY)).toBeNull()
    })
  })
})
