// Holds only the tokens and their expiry times. Never store passwords or user data here.
//
// By default the session lives in sessionStorage (this tab only). With
// "Remember me" it lives in localStorage instead, so it survives closing the
// tab or browser. The short-lived access token is renewed with the refresh
// token (see apiClient.refreshSession); the session ends when the refresh
// token expires or the backend refuses it.
const TOKEN_KEY = 'swiftdrop.accessToken'
const EXPIRES_AT_KEY = 'swiftdrop.tokenExpiresAt'
const REFRESH_TOKEN_KEY = 'swiftdrop.refreshToken'
const REFRESH_EXPIRES_AT_KEY = 'swiftdrop.refreshExpiresAt'
const KEYS = [TOKEN_KEY, EXPIRES_AT_KEY, REFRESH_TOKEN_KEY, REFRESH_EXPIRES_AT_KEY]

// Changes whenever this tab saves or clears a session, so a refresh started
// for an earlier session never writes over a newer one (or a sign-out).
let generation = 0

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

const holdsSession = (store) => KEYS.some((key) => Boolean(store?.getItem(key)))

// The store holding the current session: this tab's first, then a remembered
// one. getItem can throw; callers guard it.
function storage() {
  const session = sessionStore()
  if (holdsSession(session)) return session
  const persistent = persistentStore()
  if (holdsSession(persistent)) return persistent
  return session
}

const isPositive = (value) => Number.isFinite(value) && value > 0
const isToken = (value) => typeof value === 'string' && value.trim() !== ''

export function sessionGeneration() {
  return generation
}

/**
 * @param {boolean} [options.persistent=false] Keep the session after the tab is
 *   closed ("Remember me").
 * @param {string} [options.refreshToken] Stored with the access token when given.
 * @param {number} [options.refreshExpiresInSeconds] Required with refreshToken.
 * @returns {number} When the access token expires (ms since the epoch).
 */
export function saveSession(
  accessToken,
  expiresInSeconds,
  now = Date.now(),
  { persistent = false, refreshToken, refreshExpiresInSeconds } = {},
) {
  if (!isToken(accessToken)) {
    throw new TypeError('accessToken must be a non-empty string')
  }
  if (!isPositive(expiresInSeconds)) {
    throw new TypeError('expiresInSeconds must be a positive number')
  }
  if (refreshToken !== undefined
      && (!isToken(refreshToken) || !isPositive(refreshExpiresInSeconds))) {
    throw new TypeError('refreshToken needs a non-empty value and a positive lifetime')
  }

  const expiresAt = now + expiresInSeconds * 1000
  clearSession()
  try {
    const store = persistent ? persistentStore() : sessionStore()
    store.setItem(TOKEN_KEY, accessToken)
    store.setItem(EXPIRES_AT_KEY, String(expiresAt))
    if (refreshToken !== undefined) {
      store.setItem(REFRESH_TOKEN_KEY, refreshToken)
      store.setItem(REFRESH_EXPIRES_AT_KEY, String(now + refreshExpiresInSeconds * 1000))
    }
  } catch {
    clearSession()
    throw new Error('Unable to store the session. Check that browser storage is enabled.')
  }
  return expiresAt
}

/**
 * Returns { accessToken, expiresAt, refreshToken?, refreshExpiresAt? } or null.
 * Corrupt values are cleared; a corrupt refresh token is only ignored.
 */
export function getSession() {
  let accessToken
  let rawExpiresAt
  let refreshToken
  let rawRefreshExpiresAt
  try {
    const store = storage()
    accessToken = store?.getItem(TOKEN_KEY)
    rawExpiresAt = store?.getItem(EXPIRES_AT_KEY)
    refreshToken = store?.getItem(REFRESH_TOKEN_KEY)
    rawRefreshExpiresAt = store?.getItem(REFRESH_EXPIRES_AT_KEY)
  } catch {
    return null
  }

  if (!accessToken && !rawExpiresAt && !refreshToken) {
    return null
  }

  const expiresAt = Number(rawExpiresAt)
  if (!accessToken || !rawExpiresAt || !isPositive(expiresAt)) {
    clearSession()
    return null
  }
  const refreshExpiresAt = Number(rawRefreshExpiresAt)
  if (!refreshToken || !isPositive(refreshExpiresAt)) {
    return { accessToken, expiresAt }
  }
  return { accessToken, expiresAt, refreshToken, refreshExpiresAt }
}

/** True when the access token has expired (or there is no session). */
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

// Returns the refresh token only while it is still valid.
export function getRefreshToken(now = Date.now()) {
  const session = getSession()
  if (!session?.refreshToken || now >= session.refreshExpiresAt) {
    return null
  }
  return session.refreshToken
}

export function canRefresh(now = Date.now()) {
  return getRefreshToken(now) !== null
}

/** True when neither token can be used any more: the user must sign in again. */
export function isSessionOver(now = Date.now()) {
  return isSessionExpired(now) && !canRefresh(now)
}

/** True when the current session was saved with "Remember me". */
export function isPersistentSession() {
  try {
    return !holdsSession(sessionStore()) && holdsSession(persistentStore())
  } catch {
    return false
  }
}

export function clearSession() {
  generation += 1
  for (const store of [sessionStore(), persistentStore()]) {
    try {
      KEYS.forEach((key) => store?.removeItem(key))
    } catch {
      // Nothing else can be done if storage is unavailable.
    }
  }
}
