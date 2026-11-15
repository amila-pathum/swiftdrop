import { useEffect, useRef, useState } from 'react'
import Button from '../Button'
import { getDeliveryQuote } from '../../services/pricingService'

const currency = (amount) => new Intl.NumberFormat('en-LK', {
  style: 'currency', currency: 'LKR',
}).format(Number(amount))

const SIZES = ['SMALL', 'MEDIUM', 'LARGE']

/**
 * Fee estimate for a delivery. Takes the parcel's price inputs, calls the quote
 * endpoint when the user asks, shows the amount with its breakdown, and reports the
 * quote (or null when there is none for the current inputs) through `onQuote`.
 */
export default function FeeEstimate({ pickupCity, deliveryCity, weight, serviceType, size, onQuote }) {
  // Changing any price input discards the previous quote and its pending request,
  // including when inputs change away and back before that request completes.
  const signature = JSON.stringify([pickupCity, deliveryCity, weight, serviceType, size])
  return <CurrentEstimate key={signature} pickupCity={pickupCity} deliveryCity={deliveryCity}
    weight={weight} serviceType={serviceType} size={size} onQuote={onQuote} />
}

function CurrentEstimate({ pickupCity, deliveryCity, weight, serviceType, size, onQuote }) {
  const [state, setState] = useState({ quote: null, loading: false, error: '' })
  const requestId = useRef(0)
  const controller = useRef(null)

  // The parent hears about a quote as soon as it arrives (not in a later effect),
  // and loses it when the inputs change and this estimate unmounts.
  useEffect(() => () => onQuote(null), [onQuote])

  useEffect(() => () => {
    requestId.current += 1
    controller.current?.abort()
  }, [])

  const estimate = async () => {
    const id = ++requestId.current
    controller.current?.abort()
    onQuote(null)
    const numericWeight = Number(weight)
    if (!pickupCity?.trim() || !deliveryCity?.trim()
      || !Number.isFinite(numericWeight) || numericWeight < 0.001 || numericWeight > 9999999.999
      || !['STANDARD', 'EXPRESS'].includes(serviceType) || !SIZES.includes(size)) {
      setState({ quote: null, loading: false,
        error: 'Choose pickup and delivery cities, a positive weight, a size and a service type.' })
      return
    }
    controller.current = new AbortController()
    setState({ quote: null, loading: true, error: '' })
    try {
      const quote = await getDeliveryQuote({ pickupCity, deliveryCity, weight, serviceType, size }, {
        signal: controller.current.signal,
      })
      if (id === requestId.current) {
        setState({ quote, loading: false, error: '' })
        onQuote(quote)
      }
    } catch (error) {
      if (id === requestId.current && error?.name !== 'AbortError') {
        setState({ quote: null, loading: false,
          error: error?.message || 'Unable to estimate the delivery fee.' })
      }
    }
  }

  const { quote } = state
  return (
    <section className="delivery-summary" aria-label="Delivery fee estimate" aria-busy={state.loading}>
      <div aria-live="polite">
        {quote ? <>
          <strong>Estimated fee {currency(quote.amount)}</strong>
          <p>{pickupCity} → {deliveryCity}, {weight} kg</p>
          <p>Base fee: {currency(quote.breakdown.baseFee)}</p>
          <p>Additional weight: {currency(quote.breakdown.weightFee)}</p>
          <p>Size: {currency(quote.breakdown.sizeFee)}</p>
          {serviceType === 'EXPRESS' && <p>Express: {currency(quote.breakdown.expressFee)}</p>}
          <p>The server recalculates the final charge when you confirm.</p>
        </> : <p>Get an estimate to see your delivery fee before confirming.</p>}
      </div>
      {state.error && <p className="form-error" role="alert">{state.error}</p>}
      <Button onClick={estimate} disabled={state.loading}>
        {state.loading ? 'Calculating…' : quote ? 'Recalculate Estimate' : 'Get Estimate'}
      </Button>
    </section>
  )
}
