Courier frontend
================

Uses the existing React, JSX, React Router and plain CSS stack. No new dependencies.

Routes: `/courier/dashboard` (Today), `/courier/deliveries`, `/courier/deliveries/:parcelId`, `/courier/profile`. All require an active COURIER account. The existing dashboard URL remains the sign-in destination.

Each page has its own directory. Shared delivery cards, status badges, tabs, availability and authenticated proof photos live in `components/courier`. API calls live in `services/courierService.js`; fetching and submissions use `hooks/courier`. Local page forms are in each page's `components` directory.

The screens use authenticated backend data. Missing profiles, empty assignments, unavailable services and permission errors have explicit states. No sample deliveries are substituted. GPS capture is user initiated; a manual location description is supported. Photos accept JPG/PNG up to 5 MB and remain private behind authenticated requests.

Delivery submission IDs are retained in memory while confirmation is pending or uncertain. Check confirmation or retry sends the same operation and photo. HTTP 202 never displays delivery success. Keep the page open while an operation is pending; it is not persisted across reloads. Locations are recorded checkpoints, not continuous live GPS tracking.

Backend integration remaining: courier-service's dispatch assignment and parcel status adapters still target proposed upstream contracts. The current dispatch implementation uses legacy UUID courier IDs and does not implement the required courier-owned assignment endpoint; parcel permissions/status-update contracts also require alignment. These screens cannot load real assignments end to end until those contracts are implemented. Profile and availability use the implemented courier-service endpoints; administrators must provision the courier profile for the signed-in identity user.

Run `npm run dev`, sign in with an active courier account and use the courier sidebar. Validation: `npm run build`, `npm test`, and ESLint on the changed source. Backend authorization remains authoritative.
