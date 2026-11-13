import { useLocation } from 'react-router-dom'
import { ApplicationSubmittedView } from './ApplicationViews'

// /application/submitted, reached after merchant or courier sign-up. The state says
// whether the business application itself was submitted, or only the account created.
export default function ApplicationSubmittedPage() {
  const { state } = useLocation()
  return (
    <ApplicationSubmittedView
      applicationType={state?.applicationType}
      email={state?.email}
      applicationSubmitted={state?.applicationSubmitted !== false}
      applicationUnavailable={state?.applicationUnavailable === true}
    />
  )
}
