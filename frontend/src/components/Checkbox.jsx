/** Checkbox with its label. Remaining props (name, checked, onChange) go to the input. */
export default function Checkbox({ id, label, className, ...props }) {
  return (
    <label htmlFor={id} className={['checkbox', className].filter(Boolean).join(' ')}>
      <input id={id} type="checkbox" {...props} />
      <span>{label}</span>
    </label>
  )
}
