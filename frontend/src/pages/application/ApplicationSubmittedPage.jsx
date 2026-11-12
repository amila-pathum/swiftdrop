import { useLocation } from 'react-router-dom'
import { ApplicationSubmittedView } from './ApplicationViews'

// /application/submitted, reached after a successful merchant or courier application.
export default function ApplicationSubmittedPage() {
  const { state } = useLocation()
  return (
    <ApplicationSubmittedView applicationType={state?.applicationType} email={state?.email} />
  )
}
