const paths = {
  sun: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l1.5 1.5 M17.5 17.5 19 19 M5 19l1.5-1.5 M17.5 6.5 19 5',
  pin: 'M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z M12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6',
  box: 'm3 7 9-5 9 5v10l-9 5-9-5Z M3 7l9 5 9-5 M12 12v10 M7 4l10 6',
  list: 'M9 5h12 M9 12h12 M9 19h12 M3 5l1 1 2-3 M3 12l1 1 2-3 M3 19l1 1 2-3',
  person: 'M20 21v-3a5 5 0 0 0-5-5H9a5 5 0 0 0-5 5v3 M12 10a4 4 0 1 0 0-8 4 4 0 0 0 0 8',
  check: 'M22 11a10 10 0 1 1-6-9 M8 11l4 4L22 5',
  arrow: 'm3 11 18-8-8 18-2-8Z M11 13 21 3',
  back: 'm12 5-7 7 7 7 M5 12h16',
  camera: 'M3 6h5l2-3h4l2 3h5v15H3Z M12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8',
}
export default function CourierIcon({ name = 'box', size = 18, className = '' }) {
  return <svg width={size} height={size} className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name] || paths.box} /></svg>
}
