// Holds only the access token and its expiry. Never store passwords or user data here.
//
// By default the session lives in sessionStorage (this tab only). With
// "Remember me" it lives in localStorage instead, so it survives closing the
// tab or browser. Either way it ends when the token expires: there is no
// refresh token, and the backend's token lifetime always applies.
const TOKEN_KEY = 'swiftdrop.accessToken'
const EXPIRES_AT_KEY = 'swiftdrop.tokenExpiresAt'

// Storage access can throw (e.g. blocked storage), so every call is guarded.
function storageOf(name) {
  try {
    return window[name]
  } catch {
    return null
  }
}

const sessionStore = () => storageOf('sessionStorage')
const persistentStore = () => storageOf('localStorage')

const holdsSession = (store) => Boolean(store?.getItem(TOKEN_KEY) || store?.getItem(EXPIRES_AT_KEY))

// The store holding the current session: this tab's first, then a remembered
// one. getItem can throw; callers guard it.
function storage() {
  const session = sessionStore()
  if (holdsSession(session)) return session
  const persistent = persistentStore()
  if (holdsSession(persistent)) return persistent
  return session
}

/**
 * @param {boolean} [options.persistent=false] Keep the session after the tab is
 *   closed ("Remember me").
 */
export function saveSession(
  accessToken,
  expiresInSeconds,
  now = Date.now(),
  { persistent = false } = {},
) {
  if (typeof accessToken !== 'string' || accessToken.trim() === '') {
    throw new TypeError('accessToken must be a non-empty string')
  }
  if (!Number.isFinite(expiresInSeconds) || expiresInSeconds <= 0) {
    throw new TypeError('expiresInSeconds must be a positive number')
  }

  const expiresAt = now + expiresInSeconds * 1000
  clearSession()
  try {
    const store = persistent ? persistentStore() : sessionStore()
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
    const store = storage()
    accessToken = store?.getItem(TOKEN_KEY)
    rawExpiresAt = store?.getItem(EXPIRES_AT_KEY)
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
  for (const store of [sessionStore(), persistentStore()]) {
    try {
      store?.removeItem(TOKEN_KEY)
      store?.removeItem(EXPIRES_AT_KEY)
    } catch {
      // Nothing else can be done if storage is unavailable.
    }
  }
}
