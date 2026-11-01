import { describe, expect, it } from 'vitest'
import { isGoogleLoginEnabled, resolveApiBaseUrl } from './config'

describe('resolveApiBaseUrl', () => {
  it('defaults to the local API Gateway', () => {
    expect(resolveApiBaseUrl(undefined)).toBe('http://localhost:8080')
    expect(resolveApiBaseUrl('   ')).toBe('http://localhost:8080')
  })

  it('uses the configured URL without trailing slashes', () => {
    expect(resolveApiBaseUrl('https://api.example.com/')).toBe('https://api.example.com')
    expect(resolveApiBaseUrl(' http://gateway:8080// ')).toBe('http://gateway:8080')
  })
})

describe('isGoogleLoginEnabled', () => {
  it('is enabled only by the exact value "true"', () => {
    expect(isGoogleLoginEnabled({ VITE_GOOGLE_LOGIN_ENABLED: 'true' })).toBe(true)
    expect(isGoogleLoginEnabled({})).toBe(false)
    expect(isGoogleLoginEnabled({ VITE_GOOGLE_LOGIN_ENABLED: 'false' })).toBe(false)
    expect(isGoogleLoginEnabled({ VITE_GOOGLE_LOGIN_ENABLED: 'TRUE' })).toBe(false)
    expect(isGoogleLoginEnabled({ VITE_GOOGLE_LOGIN_ENABLED: '1' })).toBe(false)
  })
})
