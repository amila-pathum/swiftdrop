const paths = {
  send: 'm22 2-7 20-4-9-9-4 20-7ZM22 2 11 13',
  retry: 'M3 3v6h6M3.5 9a9 9 0 1 1-.5 7',
  previous: 'm14 6-6 6 6 6',
  next: 'm10 6 6 6-6 6',
  check: 'm5 12 4 4L19 6',
}

export default function WebhookActionIcon({ name }) {
  return <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
    strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={paths[name]} />
  </svg>
}
