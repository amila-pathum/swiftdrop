export const PUBLIC_ONLY_PATHS = Object.freeze([
  '/login',
  '/register',
  '/register/merchant',
  '/register/courier',
  '/forgot-password',
])
const NOT_A_DESTINATION = [...PUBLIC_ONLY_PATHS, '/auth/google/callback', '/application/submitted']

// Where to send a user who has just signed in: the page ProtectedRoute
// redirected them away from, if any, otherwise /dashboard, which resolves to
// their role dashboard or, for accounts awaiting approval, their application status.
export function postLoginPath(from) {
  const pathname = from?.pathname
  if (typeof pathname !== 'string'
      || !pathname.startsWith('/')
      || pathname.startsWith('//')
      || NOT_A_DESTINATION.includes(pathname)) {
    return '/dashboard'
  }
  return `${pathname}${from.search ?? ''}${from.hash ?? ''}`
}

/**
 * The same rule for a path that was stored as a string (Google sign-in keeps it
 * in sessionStorage across the redirect). Anything that is not a path on this
 * site, such as "https://evil.example" or "//evil.example", becomes /dashboard.
 */
export function safeReturnPath(value) {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')
      || value.includes('\\')) {
    return '/dashboard'
  }
  let url
  try {
    url = new URL(value, 'http://swiftdrop.invalid')
  } catch {
    return '/dashboard'
  }
  if (url.origin !== 'http://swiftdrop.invalid') {
    return '/dashboard'
  }
  return postLoginPath(url)
}
