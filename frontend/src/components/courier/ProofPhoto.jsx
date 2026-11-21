import { useEffect, useState } from 'react'
import Button from '../Button'
import { courierService } from '../../services/courierService'

export default function ProofPhoto({ parcelId, proofId }) {
  const [requested, setRequested] = useState(false)
  const [result, setResult] = useState(null)
  useEffect(() => {
    if (!requested) return undefined
    const controller = new AbortController()
    let current = true
    let url
    courierService.getPhoto(parcelId, proofId, { signal: controller.signal }).then((blob) => {
      if (!current) return
      if (!['image/png', 'image/jpeg'].includes(blob.type)) throw new Error('Invalid image response')
      url = URL.createObjectURL(blob); setResult({ url })
    }).catch((error) => { if (current && error.name !== 'AbortError') setResult({ error: 'The proof photo could not be loaded.' }) })
    return () => { current = false; controller.abort(); if (url) URL.revokeObjectURL(url) }
  }, [requested, parcelId, proofId])
  if (!requested) return <Button variant="outline" size="sm" onClick={() => setRequested(true)}>View proof photo</Button>
  if (result?.error) return <p role="alert">{result.error}</p>
  return result?.url ? <img className="courier-proof-image" src={result.url} alt="Recorded delivery proof" /> : <p role="status">Loading proof photo...</p>
}
