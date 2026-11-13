import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
  // "Remember me" sessions live in localStorage; never leak them between tests.
  localStorage.clear()
  sessionStorage.clear()
})
