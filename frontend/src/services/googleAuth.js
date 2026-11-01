import { API_BASE_URL } from './config'

// Google sign-in runs through the backend (API Gateway -> Identity Service).
// The browser never sees a Google client ID, secret or token: it only proves,
// with a PKCE-style verifier, that the one-time code it gets back is its own.

const AUTHORIZATION_PATH = '/oauth2/authorization/google'
const PENDING_KEY = 'swiftdrop.googleSignIn'
// Matches the backend's sign-in session lifetime.
const PENDING_MAX_AGE_MS = 10 * 60 * 1000

function base64url(bytes) {
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** 32 random bytes, base64url without padding (43 characters). */
export function createVerifier() {
  const bytes = new Uint8Array(32)
  crypto.getRandomValues(bytes)
  return base64url(bytes)
}

/** base64url(SHA-256(verifier)), the PKCE S256 challenge. */
export async function challengeFor(verifier) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  return base64url(new Uint8Array(digest))
}

export function googleAuthorizationUrl(challenge) {
  return `${API_BASE_URL}${AUTHORIZATION_PATH}?client_challenge=${encodeURIComponent(challenge)}`
}

/**
 * Starts Google sign-in: remembers the verifier and where to go afterwards in
 * this tab's sessionStorage, then leaves the page for the backend.
 * Throws if Web Crypto or storage is unavailable; nothing is stored then.
 */
export async function startGoogleSignIn(returnTo, navigate = (url) => window.location.assign(url)) {
  const verifier = createVerifier()
  const challenge = await challengeFor(verifier)
  sessionStorage.setItem(PENDING_KEY, JSON.stringify({
    verifier,
    returnTo,
    startedAt: Date.now(),
  }))
  navigate(googleAuthorizationUrl(challenge))
}

/**
 * Returns and removes the pending sign-in ({ verifier, returnTo }), or null if
 * there is none, it is malformed, or it is too old. Called once per callback,
 * so the verifier never outlives the attempt.
 */
export function takePendingGoogleSignIn(now = Date.now()) {
  let raw
  try {
    raw = sessionStorage.getItem(PENDING_KEY)
    sessionStorage.removeItem(PENDING_KEY)
  } catch {
    return null
  }
  let pending
  try {
    pending = JSON.parse(raw)
  } catch {
    return null
  }
  if (typeof pending?.verifier !== 'string'
      || !/^[A-Za-z0-9_-]{43,128}$/.test(pending.verifier)
      || !Number.isFinite(pending.startedAt)
      || now - pending.startedAt > PENDING_MAX_AGE_MS
      || now < pending.startedAt) {
    return null
  }
  return { verifier: pending.verifier, returnTo: pending.returnTo }
}
