import { describe, expect, it } from 'vitest'
import { resolveApplicationView } from './applicationStatus'
import { homePathFor, navigationFor, roleLabel } from './roles'

const user = (role, status = 'ACTIVE') => ({ id: 1, name: 'U', role, status })

describe('homePathFor', () => {
  it.each([
    ['CUSTOMER', '/customer/dashboard'],
    ['MERCHANT', '/merchant/dashboard'],
    ['COURIER', '/courier/dashboard'],
    ['DISPATCHER', '/dispatcher/dashboard'],
    ['ADMIN', '/admin/dashboard'],
  ])('sends an active %s to %s', (role, path) => {
    expect(homePathFor(user(role))).toBe(path)
  })

  it.each(['PENDING_APPROVAL', 'SUSPENDED', 'DISABLED', undefined])(
    'sends a %s account to the application status page',
    (status) => {
      expect(homePathFor({ role: 'MERCHANT', status })).toBe('/application/status')
    },
  )

  it.each([null, undefined, { role: 'SUPERUSER', status: 'ACTIVE' }, { role: 'toString', status: 'ACTIVE' }])(
    'returns null for %j',
    (value) => {
      expect(homePathFor(value)).toBeNull()
    },
  )
})

describe('navigationFor', () => {
  it('gives an active user their dashboard and profile', () => {
    expect(navigationFor(user('COURIER'))).toEqual([
      { to: '/courier/dashboard', label: 'Dashboard' },
      { to: '/profile', label: 'Profile' },
    ])
  })

  it('gives a pending user only the application status and profile', () => {
    expect(navigationFor(user('MERCHANT', 'PENDING_APPROVAL'))).toEqual([
      { to: '/application/status', label: 'Application status' },
      { to: '/profile', label: 'Profile' },
    ])
  })

  it('returns nothing when signed out', () => {
    expect(navigationFor(null)).toEqual([])
  })
})

describe('roleLabel', () => {
  it('formats roles for display', () => {
    expect(roleLabel('DISPATCHER')).toBe('Dispatcher')
    expect(roleLabel(undefined)).toBe('')
  })
})

describe('resolveApplicationView', () => {
  it.each([
    ['pending account, no record', user('MERCHANT', 'PENDING_APPROVAL'), null, 'pending'],
    ['active account', user('MERCHANT'), null, 'approved'],
    ['rejected record', user('COURIER', 'DISABLED'), { status: 'REJECTED' }, 'rejected'],
    ['rejection wins over a pending account', user('COURIER', 'PENDING_APPROVAL'), { status: 'REJECTED' }, 'rejected'],
    ['approved record, account not yet active', user('COURIER', 'PENDING_APPROVAL'), { status: 'APPROVED' }, 'pending'],
    ['suspended account', user('MERCHANT', 'SUSPENDED'), null, 'inactive'],
    ['disabled account without a record is never shown as rejected', user('MERCHANT', 'DISABLED'), null, 'inactive'],
  ])('%s -> %s', (_label, u, application, expected) => {
    expect(resolveApplicationView(u, application)).toBe(expected)
  })
})
