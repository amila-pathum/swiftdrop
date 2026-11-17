export default function DeliveryTabs({ selected, onChange, counts }) {
  const tabs = [['active', 'Active'], ['completed', 'Completed'], ['all', 'All']]
  function navigate(event, index) {
    const next = event.key === 'ArrowRight' ? (index + 1) % 3 : event.key === 'ArrowLeft' ? (index + 2) % 3 : event.key === 'Home' ? 0 : event.key === 'End' ? 2 : null
    if (next === null) return
    event.preventDefault()
    onChange(tabs[next][0])
    event.currentTarget.parentElement.children[next].focus()
  }
  return <div className="courier-tabs" role="tablist" aria-label="Delivery views">{tabs.map(([value, label], index) => <button type="button" role="tab" key={value} id={`courier-tab-${value}`} aria-selected={selected === value} tabIndex={selected === value ? 0 : -1} aria-controls="courier-delivery-panel" onKeyDown={(event) => navigate(event, index)} onClick={() => onChange(value)}>{label}<span>{counts[value]}</span></button>)}</div>
}
