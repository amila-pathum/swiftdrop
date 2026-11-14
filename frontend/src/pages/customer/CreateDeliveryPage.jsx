import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Alert from '../../components/Alert'
import Button from '../../components/Button'
import DeliveryStepper from '../../components/delivery/DeliveryStepper'
import FormField from '../../components/FormField'
import { parcelService } from '../../services/parcelService'
import { getDeliveryQuote } from '../../services/pricingService'
import './createDelivery.css'

const DISTRICTS = [
  'Ampara', 'Anuradhapura', 'Badulla', 'Batticaloa', 'Colombo', 'Galle', 'Gampaha',
  'Hambantota', 'Jaffna', 'Kalutara', 'Kandy', 'Kegalle', 'Kilinochchi', 'Kurunegala',
  'Mannar', 'Matale', 'Matara', 'Monaragala', 'Mullaitivu', 'Nuwara Eliya',
  'Polonnaruwa', 'Puttalam', 'Ratnapura', 'Trincomalee', 'Vavuniya',
]
const DISTRICT_OPTIONS = DISTRICTS.map((district) => ({ value: district, label: district }))

const INITIAL = {
  pickup: { pickupAddressLine1: '', pickupAddressLine2: '', pickupCity: '' },
  receiver: {
    receiverName: '', receiverPhone: '', deliveryAddressLine1: '',
    deliveryAddressLine2: '', deliveryCity: '',
  },
  parcel: { weight: '', size: '', serviceType: 'STANDARD' },
}

const required = (value) => String(value ?? '').trim().length > 0
const isPhone = (value) => /^\+?[0-9 ()-]{7,30}$/.test(value.trim())

function validate(step, values) {
  const errors = {}
  if (step === 0) {
    for (const key of ['pickupAddressLine1', 'pickupCity']) {
      if (!required(values.pickup[key])) errors[key] = 'This field is required'
    }
  } else if (step === 1) {
    for (const key of ['receiverName', 'deliveryAddressLine1', 'deliveryCity']) {
      if (!required(values.receiver[key])) errors[key] = 'This field is required'
    }
    if (!isPhone(values.receiver.receiverPhone)) {
      errors.receiverPhone = 'Enter a valid phone number'
    }
  } else if (step === 2) {
    const weight = Number(values.parcel.weight)
    if (!required(values.parcel.weight) || !Number.isFinite(weight) || weight < 0.001) {
      errors.weight = 'Enter a weight of at least 0.001 kg'
    }
    if (!['SMALL', 'MEDIUM', 'LARGE'].includes(values.parcel.size)) {
      errors.size = 'Select a parcel size'
    }
  }
  return errors
}

function Summary({ title, step, onEdit, children }) {
  return (
    <section className="delivery-summary">
      <header>
        <strong>{title}</strong>
        <button type="button" onClick={() => onEdit(step)}>Edit</button>
      </header>
      <div>{children}</div>
    </section>
  )
}

export default function CreateDeliveryPage() {
  const [step, setStep] = useState(0)
  const [values, setValues] = useState(INITIAL)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState('')
  const [createdParcel, setCreatedParcel] = useState(null)
  const [quoteState, setQuoteState] = useState({
    status: 'idle', quote: null, signature: null, error: '',
  })
  const quoteRequest = useRef(0)
  const submittingRef = useRef(false)
  const pricingSignature = JSON.stringify([
    values.pickup.pickupCity,
    values.receiver.deliveryCity,
    values.parcel.weight,
    values.parcel.serviceType,
  ])
  const validQuote = quoteState.status === 'success'
    && quoteState.signature === pricingSignature ? quoteState.quote : null
  const quoting = quoteState.status === 'loading' && quoteState.signature === pricingSignature
  const quoteError = quoteState.status === 'error'
    && quoteState.signature === pricingSignature ? quoteState.error : ''
  const currency = (amount) => new Intl.NumberFormat('en-LK', {
    style: 'currency', currency: 'LKR',
  }).format(Number(amount))

  const field = (group, key, label, extra = {}) => ({
    id: `delivery-${group}-${key}`,
    label,
    value: values[group][key],
    onChange: (event) => {
      setValues((current) => ({
        ...current,
        [group]: { ...current[group], [key]: event.target.value },
      }))
      setErrors((current) => ({ ...current, [key]: undefined }))
    },
    error: errors[key],
    ...extra,
  })

  const next = () => {
    const found = validate(step, values)
    setErrors(found)
    if (Object.keys(found).length === 0 && (step !== 2 || validQuote)) {
      setStep((current) => Math.min(current + 1, 3))
    }
  }
  const previous = () => {
    setErrors({})
    setStep((current) => Math.max(current - 1, 0))
  }
  const jump = (target) => {
    setErrors({})
    setSubmitError('')
    setStep(target)
  }
  const estimate = async () => {
    const found = validate(2, values)
    setErrors(found)
    if (Object.keys(found).length > 0) {
      setQuoteState({
        status: 'error', quote: null, signature: pricingSignature,
        error: 'Complete the parcel details before requesting an estimate.',
      })
      return
    }
    const requestId = ++quoteRequest.current
    setQuoteState({ status: 'loading', quote: null, signature: pricingSignature, error: '' })
    try {
      const quote = await getDeliveryQuote({
        pickupCity: values.pickup.pickupCity,
        deliveryCity: values.receiver.deliveryCity,
        weight: values.parcel.weight,
        serviceType: values.parcel.serviceType,
      })
      if (requestId === quoteRequest.current) {
        setQuoteState({
          status: 'success', quote, signature: pricingSignature, error: '',
        })
      }
    } catch (error) {
      if (requestId === quoteRequest.current) {
        setQuoteState({
          status: 'error', quote: null, signature: pricingSignature,
          error: error?.message || 'Unable to estimate the delivery fee.',
        })
      }
    }
  }
  const submit = async () => {
    if (submittingRef.current || !validQuote) return
    submittingRef.current = true
    setSubmitting(true)
    setSubmitError('')
    try {
      const parcel = await parcelService.createParcel({
        receiverName: values.receiver.receiverName.trim(),
        receiverPhone: values.receiver.receiverPhone.trim(),
        pickupAddressLine1: values.pickup.pickupAddressLine1.trim(),
        pickupAddressLine2: values.pickup.pickupAddressLine2.trim() || null,
        pickupCity: values.pickup.pickupCity,
        deliveryAddressLine1: values.receiver.deliveryAddressLine1.trim(),
        deliveryAddressLine2: values.receiver.deliveryAddressLine2.trim() || null,
        deliveryCity: values.receiver.deliveryCity,
        weight: Number(values.parcel.weight),
        size: values.parcel.size,
        parcelType: values.parcel.serviceType,
      })
      if (!parcel?.trackingNumber || !parcel?.id || parcel.status !== 'CREATED'
          || !Number.isFinite(Number(parcel.charge))) {
        throw new Error('The booking response was incomplete.')
      }
      setCreatedParcel(parcel)
    } catch (error) {
      const message = error?.message || 'Unable to confirm the delivery.'
      setSubmitError(`${message} Check My Deliveries before retrying because the parcel may already have been created.`)
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  const createAnother = () => {
    setValues(INITIAL)
    setStep(0)
    setErrors({})
    setSubmitError('')
    setCreatedParcel(null)
    setQuoteState({ status: 'idle', quote: null, signature: null, error: '' })
  }

  if (createdParcel) {
    return (
      <main className="delivery-page">
        <section className="delivery-panel delivery-success" role="status">
          <header><h1>Delivery booked successfully!</h1></header>
          <div className="delivery-fields">
            <p>Your parcel has been created. Save this tracking number:</p>
            <p className="delivery-tracking-number">{createdParcel.trackingNumber}</p>
            <p>Status: <strong>{createdParcel.status}</strong></p>
            <p>Final delivery charge: <strong>{currency(createdParcel.charge)}</strong></p>
            <p>Parcel ID: {createdParcel.id}</p>
            <Button onClick={createAnother}>Create Another Delivery</Button>
          </div>
        </section>
      </main>
    )
  }

  return (
    <main className="delivery-page">
      <nav className="delivery-breadcrumb" aria-label="Breadcrumb">
        <Link to="/customer/dashboard">Overview</Link> › Create Delivery
      </nav>
      <h1>Create Delivery</h1>
      <p className="delivery-intro">Book a parcel pickup anywhere we deliver in Sri Lanka.</p>
      {submitError && <Alert variant="error">{submitError}</Alert>}
      <DeliveryStepper step={step} />

      {step < 3 ? (
        <section className="delivery-panel">
          <header>
            <strong>
              Step {step + 1}: {['Pickup details', 'Receiver details', 'Parcel details'][step]}
            </strong>
            {step === 1 && <p>Who is receiving the parcel?</p>}
          </header>
          <div className="delivery-fields">
            {step === 0 && <>
              <FormField {...field('pickup', 'pickupAddressLine1', 'Pickup address line 1', {
                required: true,
                maxLength: 255,
              })} />
              <FormField {...field('pickup', 'pickupAddressLine2', 'Pickup address line 2 (optional)', {
                maxLength: 255,
              })} />
              <FormField {...field('pickup', 'pickupCity', 'Pickup district', {
                as: 'select',
                required: true,
                placeholder: 'Select pickup district',
                options: DISTRICT_OPTIONS,
              })} />
            </>}

            {step === 1 && <>
              <div className="delivery-grid">
                <FormField {...field('receiver', 'receiverName', 'Receiver name', {
                  required: true,
                  maxLength: 150,
                })} />
                <FormField {...field('receiver', 'receiverPhone', 'Receiver phone', {
                  required: true,
                  maxLength: 30,
                })} />
              </div>
              <FormField {...field('receiver', 'deliveryAddressLine1', 'Delivery address line 1', {
                required: true,
                maxLength: 255,
              })} />
              <FormField {...field('receiver', 'deliveryAddressLine2', 'Delivery address line 2 (optional)', {
                maxLength: 255,
              })} />
              <FormField {...field('receiver', 'deliveryCity', 'Delivery district', {
                as: 'select',
                required: true,
                placeholder: 'Select delivery district',
                options: DISTRICT_OPTIONS,
              })} />
            </>}

            {step === 2 && <>
              <FormField {...field('parcel', 'serviceType', 'Service type', {
                as: 'select',
                required: true,
                options: [
                  { value: 'STANDARD', label: 'Standard' },
                  { value: 'EXPRESS', label: 'Express' },
                ],
              })} />
              <div className="delivery-grid">
                <FormField {...field('parcel', 'weight', 'Weight (kg)', {
                  type: 'number', min: '0.001', step: '0.001', required: true, max: '9999999.999',
                })} />
                <FormField {...field('parcel', 'size', 'Parcel size', {
                  as: 'select',
                  required: true,
                  placeholder: 'Select parcel size',
                  options: [
                    { value: 'SMALL', label: 'Small' },
                    { value: 'MEDIUM', label: 'Medium' },
                    { value: 'LARGE', label: 'Large' },
                  ],
                })} />
              </div>
              <div className="delivery-estimate-placeholder" aria-live="polite">
                {validQuote ? (
                  <span>
                    <strong>Estimated fee {currency(validQuote.amount)}</strong>
                    {' · '}{values.pickup.pickupCity} → {values.receiver.deliveryCity},
                    {' '}{values.parcel.weight} kg
                  </span>
                ) : <span>Get an estimate to see your delivery fee before confirming.</span>}
                <Button onClick={estimate} disabled={quoting}>
                  {quoting ? 'Calculating…' : validQuote ? 'Recalculate Estimate' : 'Get Estimate'}
                </Button>
              </div>
              {quoteError && <p className="form-error" role="alert">{quoteError}</p>}
            </>}
          </div>
        </section>
      ) : (
        <div className="delivery-review">
          <div className="delivery-grid">
            <Summary title="Pickup" step={0} onEdit={jump}>
              <p>{values.pickup.pickupAddressLine1}</p>
              {values.pickup.pickupAddressLine2 && <p>{values.pickup.pickupAddressLine2}</p>}
              <p>{values.pickup.pickupCity}</p>
            </Summary>
            <Summary title="Receiver" step={1} onEdit={jump}>
              <p>{values.receiver.receiverName}</p>
              <p>{values.receiver.receiverPhone}</p>
              <p>{values.receiver.deliveryAddressLine1}</p>
              {values.receiver.deliveryAddressLine2 && <p>{values.receiver.deliveryAddressLine2}</p>}
              <p>{values.receiver.deliveryCity}</p>
            </Summary>
          </div>
          <Summary title="Parcel" step={2} onEdit={jump}>
            <p>{values.parcel.weight} kg</p>
            <p>{values.parcel.size}</p>
            <p>{values.parcel.serviceType === 'EXPRESS' ? 'Express' : 'Standard'} service</p>
          </Summary>
          {validQuote ? (
            <section className="delivery-summary">
              <header><strong>Estimated delivery fee</strong></header>
              <div>
                <p>Base fee: {currency(validQuote.breakdown.baseFee)}</p>
                <p>Weight fee: {currency(validQuote.breakdown.weightFee)}</p>
                <p>Express fee: {currency(validQuote.breakdown.expressFee)}</p>
                <strong>Total: {currency(validQuote.amount)}</strong>
                <p>The server recalculates the final charge when you confirm.</p>
              </div>
            </section>
          ) : (
            <div className="delivery-estimate-placeholder">
              Your estimate has changed. Return to Parcel details to request a new estimate.
            </div>
          )}
        </div>
      )}

      <div className="delivery-navigation">
        <Button variant="outline" onClick={previous} disabled={step === 0}>Back</Button>
        {step < 3
          ? <Button onClick={next} disabled={step === 2 && !validQuote}>Continue →</Button>
          : <Button onClick={submit} loading={submitting} loadingText="Creating delivery…"
              disabled={!validQuote || submitting}>Confirm Delivery</Button>}
      </div>
    </main>
  )
}
