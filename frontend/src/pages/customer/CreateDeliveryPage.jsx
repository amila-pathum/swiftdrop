import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Alert from '../../components/Alert'
import Button from '../../components/Button'
import DeliveryStepper from '../../components/delivery/DeliveryStepper'
import FormField from '../../components/FormField'
import { parcelService } from '../../services/parcelService'
import CopyTrackingNumberButton from '../../components/CopyTrackingNumberButton'
import FeeEstimate from '../../components/pricing/FeeEstimate'
import { localityOptions, pricingService } from '../../services/pricingService'
import './createDelivery.css'

/**
 * The cities customers can book between: every locality of every active delivery
 * zone, grouped by zone. Loaded once when the form opens.
 */
function useLocalities() {
  const [state, setState] = useState({ status: 'loading', options: [], error: '' })

  const load = useCallback((signal) => pricingService.getZones({ signal })
    .then((zones) => setState({ status: 'ready', options: localityOptions(zones), error: '' }))
    .catch((error) => {
      if (error?.name !== 'AbortError') {
        setState({ status: 'error', options: [],
          error: error?.message || 'Unable to load delivery areas.' })
      }
    }), [])

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
    return () => controller.abort()
  }, [load])

  const retry = () => {
    setState({ status: 'loading', options: [], error: '' })
    load()
  }
  return { ...state, retry }
}

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
  const [validQuote, setQuote] = useState(null)
  const submittingRef = useRef(false)
  const localities = useLocalities()
  const cityField = {
    as: 'select',
    required: true,
    options: localities.options,
    disabled: localities.status !== 'ready',
    hint: localities.status === 'ready' && localities.options.length === 0
      ? 'No delivery areas are available yet.' : undefined,
  }
  const citiesUnavailable = localities.status === 'error' && (
    <Alert variant="error" title="Unable to load delivery areas">
      {localities.error}{' '}
      <button type="button" className="action-link action-link-primary" onClick={localities.retry}>
        Try again
      </button>
    </Alert>
  )
  const cityPlaceholder = (which) => (localities.status === 'loading'
    ? 'Loading delivery areas…' : `Select ${which} city`)
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
    setQuote(null)
  }

  if (createdParcel) {
    return (
      <main className="delivery-page">
        <section className="delivery-panel delivery-success" role="status">
          <header><h1>Delivery booked successfully!</h1></header>
          <div className="delivery-fields">
            <p>Your parcel has been created. Save this tracking number:</p>
            <div className="delivery-tracking-copy-row">
              <p className="delivery-tracking-number">{createdParcel.trackingNumber}</p>
              <CopyTrackingNumberButton trackingNumber={createdParcel.trackingNumber} />
            </div>
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
              <FormField {...field('pickup', 'pickupCity', 'Pickup city', {
                ...cityField,
                placeholder: cityPlaceholder('pickup'),
              })} />
              {citiesUnavailable}
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
              <FormField {...field('receiver', 'deliveryCity', 'Delivery city', {
                ...cityField,
                placeholder: cityPlaceholder('delivery'),
              })} />
              {citiesUnavailable}
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

        </div>
      )}

      <div hidden={step < 2}>
        <FeeEstimate pickupCity={values.pickup.pickupCity}
          deliveryCity={values.receiver.deliveryCity} weight={values.parcel.weight}
          serviceType={values.parcel.serviceType} size={values.parcel.size} onQuote={setQuote} />
      </div>

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
