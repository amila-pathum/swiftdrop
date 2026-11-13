import { useCallback, useRef, useState } from 'react'

/**
 * Form state shared by the authentication forms, reusable for any form.
 *
 * - Validates on submit; after the first attempt, re-validates on every change.
 * - Focuses the first invalid field (its id must be `${idPrefix}-${fieldName}`).
 * - Ignores a second submit while one is in flight.
 *
 * @param {object} options
 * @param {object} options.initialValues
 * @param {(values) => Object<string, string>} options.validate Returns { field: message }.
 * @param {string} options.idPrefix
 */
export function useForm({ initialValues, validate, idPrefix }) {
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState(null)
  const [attempted, setAttempted] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  // Guards against a second submit before the disabled state has rendered.
  const submittingRef = useRef(false)

  const focusField = useCallback((field) => {
    document.getElementById(`${idPrefix}-${field}`)?.focus()
  }, [idPrefix])

  /** Marks one field invalid (e.g. "email already registered") and focuses it. */
  const setFieldError = useCallback((field, message) => {
    setErrors((current) => ({ ...current, [field]: message }))
    focusField(field)
  }, [focusField])

  function handleChange(event) {
    const { name, type, checked, value } = event.target
    const next = { ...values, [name]: type === 'checkbox' ? checked : value }
    setValues(next)
    if (attempted) {
      setErrors(validate(next))
    }
  }

  /**
   * Returns a submit handler. `action(values)` runs only when the form is valid;
   * report failures from inside it with setFormError / setFieldError.
   */
  function handleSubmit(action) {
    return async (event) => {
      event.preventDefault()
      if (submittingRef.current) return

      setAttempted(true)
      const found = validate(values)
      setErrors(found)
      const firstInvalid = Object.keys(found)[0]
      if (firstInvalid) {
        focusField(firstInvalid)
        return
      }

      submittingRef.current = true
      setSubmitting(true)
      setFormError(null)
      try {
        await action(values)
      } finally {
        submittingRef.current = false
        setSubmitting(false)
      }
    }
  }

  return {
    values,
    errors,
    formError,
    submitting,
    handleChange,
    handleSubmit,
    setFormError,
    setFieldError,
    /** Props for a FormField bound to `name`. */
    field: (name) => ({
      id: `${idPrefix}-${name}`,
      name,
      value: values[name],
      onChange: handleChange,
      error: errors[name],
    }),
  }
}
