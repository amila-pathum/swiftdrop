const STEPS = ['Pickup details', 'Receiver details', 'Parcel details', 'Review and confirm']

export default function DeliveryStepper({ step }) {
  return (
    <ol className="delivery-stepper" aria-label="Delivery booking progress">
      {STEPS.map((label, index) => (
        <li key={label} className={index < step ? 'complete' : index === step ? 'current' : ''}
          aria-current={index === step ? 'step' : undefined}>
          <span className="delivery-step-number" aria-hidden="true">{index < step ? '✓' : index + 1}</span>
          <span>{label}</span>
        </li>
      ))}
    </ol>
  )
}
