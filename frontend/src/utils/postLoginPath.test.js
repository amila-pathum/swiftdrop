import { describe, expect, it } from 'vitest'
import { postLoginPath, safeReturnPath } from './postLoginPath'

describe('postLoginPath', () => {
  it('returns the requested page with its query and hash', () => {
    expect(postLoginPath({ pathname: '/profile', search: '?tab=1', hash: '#top' }))
      .toBe('/profile?tab=1#top')
  })

  it.each([
    [undefined],
    [{ pathname: '/login' }],
    [{ pathname: '/register' }],
    [{ pathname: '/auth/google/callback' }],
    [{ pathname: '//evil.example' }],
    [{ pathname: 'https://evil.example' }],
  ])('falls back to the dashboard for %j', (from) => {
    expect(postLoginPath(from)).toBe('/dashboard')
  })
})

describe('safeReturnPath', () => {
  it('keeps a path on this site', () => {
    expect(safeReturnPath('/profile')).toBe('/profile')
    expect(safeReturnPath('/profile?tab=1#top')).toBe('/profile?tab=1#top')
  })

  it.each([
    undefined,
    null,
    42,
    '',
    'profile',
    'https://evil.example/profile',
    '//evil.example/profile',
    '/\\evil.example',
    '\\\\evil.example',
    'javascript:alert(1)',
    '/login',
    '/register',
    '/auth/google/callback',
  ])('falls back to the dashboard for %j', (value) => {
    expect(safeReturnPath(value)).toBe('/dashboard')
  })
})
