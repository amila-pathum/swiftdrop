import { describe, expect, it } from 'vitest'
import { ApiError, ApiErrorKind } from './apiClient'
import {
  describeError,
  GENERIC_ERROR_MESSAGE,
  SERVICE_UNAVAILABLE_MESSAGE,
} from './errorMessages'

describe('describeError', () => {
  it.each([ApiErrorKind.NETWORK, ApiErrorKind.SERVICE_UNAVAILABLE])(
    'uses the shared message for %s',
    (kind) => {
      expect(describeError(new ApiError('x', { kind }), { byStatus: { 0: 'no' } }))
        .toBe(SERVICE_UNAVAILABLE_MESSAGE)
    },
  )

  it('uses the page message for a planned endpoint, or the error message', () => {
    const error = new ApiError('Not yet.', { status: 501, kind: ApiErrorKind.NOT_IMPLEMENTED })
    expect(describeError(error, { notImplemented: 'Coming soon.' })).toBe('Coming soon.')
    expect(describeError(error)).toBe('Not yet.')
  })

  it('maps statuses and falls back to a generic message', () => {
    const conflict = new ApiError('Email taken', { status: 409 })
    expect(describeError(conflict, { byStatus: { 409: 'Already registered.' } }))
      .toBe('Already registered.')
    expect(describeError(conflict)).toBe(GENERIC_ERROR_MESSAGE)
  })

  it('never shows raw messages from non-API errors', () => {
    expect(describeError(new Error('TypeError: x is undefined'))).toBe(GENERIC_ERROR_MESSAGE)
    expect(describeError(new Error('storage'), { nonApi: 'Storage blocked.' })).toBe('Storage blocked.')
  })
})
