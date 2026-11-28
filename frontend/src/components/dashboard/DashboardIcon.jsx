// Line icons shared by the role dashboards (24px grid, drawn with currentColor).
const PATHS = {
  package: 'm7.5 4.3 9 5.2 M21 8v8a2 2 0 0 1-1 1.7l-7 4a2 2 0 0 1-2 0l-7-4A2 2 0 0 1 3 16V8a2 2 0 0 1 1-1.7l7-4a2 2 0 0 1 2 0l7 4A2 2 0 0 1 21 8Z M3.3 7 12 12l8.7-5 M12 22V12',
  truck: 'M1 3h15v13H1Z M16 8h4l3 3v5h-7Z M5.5 21a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5 M18.5 21a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5',
  users: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M23 21v-2a4 4 0 0 0-3-3.9 M16 3.1a4 4 0 0 1 0 7.8',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8',
  store: 'm2 7 4.4-4.4A2 2 0 0 1 7.8 2h8.4a2 2 0 0 1 1.4.6L22 7 M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8 M15 22v-4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4 M2 7h20',
  alert: 'M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0 M12 9v4 M12 17h.01',
  check: 'M22 11a10 10 0 1 1-6-9 M8 11l4 4L22 5',
  xcircle: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M15 9l-6 6 M9 9l6 6',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20 M12 6v6l4 2',
  chart: 'M3 3v18h18 M7 16v-5 M12 16V8 M17 16v-9',
  inbox: 'M22 12h-6l-2 3h-4l-2-3H2 M5.5 5.1 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.5-6.9A2 2 0 0 0 16.8 4H7.2a2 2 0 0 0-1.7 1.1Z',
  send: 'm22 2-7 20-4-9-9-4Z M22 2 11 13',
  route: 'M6 19a3 3 0 1 0 0-6 3 3 0 0 0 0 6 M18 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6 M9 16h5.5a3.5 3.5 0 0 0 0-7H12',
  refresh: 'M21 12a9 9 0 0 1-15 6.7L3 16 M3 12a9 9 0 0 1 15-6.7L21 8 M21 3v5h-5 M3 21v-5h5',
  plus: 'M12 5v14 M5 12h14',
  arrowRight: 'M5 12h14 M13 6l6 6-6 6',
  chevronRight: 'm9 18 6-6-6-6',
  calendar: 'M3 6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z M16 2v4 M8 2v4 M3 10h18',
  wallet: 'M20 12V8H6a2 2 0 0 1 0-4h12v4 M4 6v12a2 2 0 0 0 2 2h14v-4 M18 12a2 2 0 0 0 0 4h4v-4Z',
}

export default function DashboardIcon({ name, size = 18, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className={className}>
      <path d={PATHS[name] || PATHS.package} />
    </svg>
  )
}
