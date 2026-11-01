// Holds only the access token and its expiry. Never store passwords or user data here.
const TOKEN_KEY = 'swiftdrop.accessToken'
const EXPIRES_AT_KEY = 'swiftdrop.tokenExpiresAt'

// sessionStorage access can throw (e.g. blocked storage), so every call is guarded.
function storage() {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

export function saveSession(accessToken, expiresInSeconds, now = Date.now()) {
  if (typeof accessToken !== 'string' || accessToken.trim() === '') {
    throw new TypeError('accessToken must be a non-empty string')
  }
  if (!Number.isFinite(expiresInSeconds) || expiresInSeconds <= 0) {
    throw new TypeError('expiresInSeconds must be a positive number')
  }

  const expiresAt = now + expiresInSeconds * 1000
  try {
    const store = storage()
    store.setItem(TOKEN_KEY, accessToken)
    store.setItem(EXPIRES_AT_KEY, String(expiresAt))
  } catch {
    clearSession()
    throw new Error('Unable to store the session. Check that browser storage is enabled.')
  }
  return expiresAt
}

// Returns { accessToken, expiresAt } or null. Corrupt values are cleared.
export function getSession() {
  let accessToken
  let rawExpiresAt
  try {
    accessToken = storage()?.getItem(TOKEN_KEY)
    rawExpiresAt = storage()?.getItem(EXPIRES_AT_KEY)
  } catch {
    return null
  }

  if (!accessToken && !rawExpiresAt) {
    return null
  }

  const expiresAt = Number(rawExpiresAt)
  if (!accessToken || !rawExpiresAt || !Number.isFinite(expiresAt) || expiresAt <= 0) {
    clearSession()
    return null
  }
  return { accessToken, expiresAt }
}

export function isSessionExpired(now = Date.now()) {
  const session = getSession()
  return session === null || now >= session.expiresAt
}

// Returns the token only while it is still valid.
export function getAccessToken(now = Date.now()) {
  const session = getSession()
  if (session === null || now >= session.expiresAt) {
    return null
  }
  return session.accessToken
}

export function clearSession() {
  try {
    storage()?.removeItem(TOKEN_KEY)
    storage()?.removeItem(EXPIRES_AT_KEY)
  } catch {
    // Nothing else can be done if storage is unavailable.
  }
}
