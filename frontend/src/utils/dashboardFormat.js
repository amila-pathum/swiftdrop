// Date labels shared by the role dashboards (Sri Lanka time).
export const todayLabel = () => new Intl.DateTimeFormat('en-GB', {
  weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Asia/Colombo',
}).format(new Date())

export const dateTimeLabel = (value) => {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—'
    : new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Colombo' }).format(date)
}
