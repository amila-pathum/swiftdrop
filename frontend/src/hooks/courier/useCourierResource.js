import { useCallback, useEffect, useState } from 'react'

/** Discards stale responses and aborts requests when identity, filters or route change. */
export function useCourierResource(loader, key) {
  const [revision, setRevision] = useState(0)
  const [result, setResult] = useState(null)
  const reload = useCallback(() => setRevision((value) => value + 1), [])
  useEffect(() => {
    const controller = new AbortController()
    let current = true
    Promise.resolve().then(() => loader({ signal: controller.signal })).then(
      (data) => { if (current) setResult({ key, revision, data, error: null }) },
      (error) => { if (current && error?.name !== 'AbortError') setResult({ key, revision, data: null, error }) },
    )
    return () => { current = false; controller.abort() }
  }, [loader, key, revision])
  const fresh = result?.key === key && result?.revision === revision
  return { data: fresh ? result.data : null, error: fresh ? result.error : null, loading: !fresh, reload }
}
