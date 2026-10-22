import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import { useCatalog } from '../hooks/useData'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { ErrorState, Loading, PageHeader } from '../components/ui'
import { Icon } from '../components/Icon'

const WEEKDAYS = {
  en: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
  km: ['ច័ន្ទ', 'អង្គារ', 'ពុធ', 'ព្រហ', 'សុក្រ', 'សៅរ៍', 'អាទិត្យ'],
}

const pad = (n: number) => String(n).padStart(2, '0')

export function ReleaseCalendarPage() {
  const { t, l, lang, date, monthName } = useI18n()
  const catalog = useCatalog()
  const [params, setParams] = useSearchParams()
  const [day, setDay] = useState<string | null>(null)
  useDocumentTitle(t('calendarTitle'))

  const byDate = useMemo(() => {
    const m = new Map<string, { datasetId: string; uploadId: string }[]>()
    for (const r of catalog.data?.releases ?? []) {
      if (!m.has(r.date)) m.set(r.date, [])
      m.get(r.date)!.push(r)
    }
    return m
  }, [catalog.data])

  if (catalog.status === 'loading') return <Loading block />
  if (catalog.status === 'error') return <ErrorState onRetry={catalog.retry} />

  const latest = catalog.data.stats.lastRelease ?? new Date().toISOString().slice(0, 10)
  const month = params.get('month') ?? latest.slice(0, 7)
  const [y, m] = month.split('-').map(Number)
  const first = new Date(Date.UTC(y, m - 1, 1))
  const daysInMonth = new Date(Date.UTC(y, m, 0)).getUTCDate()
  const lead = (first.getUTCDay() + 6) % 7 // Monday-first
  const cells: (number | null)[] = [...Array(lead).fill(null), ...Array.from({ length: daysInMonth }, (_, i) => i + 1)]
  while (cells.length % 7) cells.push(null)

  const shift = (delta: number) => {
    const d = new Date(Date.UTC(y, m - 1 + delta, 1))
    setParams({ month: `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}` })
    setDay(null)
  }

  const datasets = new Map(catalog.data.datasets.map((d) => [d.id, d]))
  const categories = new Map(catalog.data.categories.map((c) => [c.id, c]))
  const monthDates = [...byDate.keys()].filter((d) => d.startsWith(month)).sort().reverse()
  const listDates = day ? monthDates.filter((d) => d === day) : monthDates
  const monthCount = monthDates.reduce((s, d) => s + (byDate.get(d)?.length ?? 0), 0)
  const today = new Date().toISOString().slice(0, 10)
  const monthLabel = `${monthName(m, true)} ${y}`

  return (
    <>
      <PageHeader crumbs={[{ label: t('calendarTitle') }]} title={t('calendarTitle')} lead={t('calendarLead')} />
      <div className="container section-sm calendar-layout">
        <section className="calendar card" aria-label={monthLabel}>
          <header className="calendar-head">
            <button type="button" className="icon-btn" onClick={() => shift(-1)} aria-label={t('prevMonth')}>
              <Icon name="chevronLeft" />
            </button>
            <h2>{monthLabel}</h2>
            <button type="button" className="icon-btn" onClick={() => shift(1)} aria-label={t('nextMonth')}>
              <Icon name="chevronRight" />
            </button>
          </header>
          <div className="calendar-grid" role="grid">
            {WEEKDAYS[lang].map((w) => (
              <div key={w} className="calendar-weekday" role="columnheader">
                {w}
              </div>
            ))}
            {cells.map((d, i) => {
              if (!d) return <div key={i} className="calendar-cell blank" />
              const iso = `${month}-${pad(d)}`
              const items = byDate.get(iso) ?? []
              return (
                <button
                  key={i}
                  type="button"
                  className={`calendar-cell${items.length ? ' has' : ''}${day === iso ? ' on' : ''}${iso === today ? ' today' : ''}`}
                  disabled={!items.length}
                  onClick={() => setDay(day === iso ? null : iso)}
                  aria-label={`${date(iso)}${items.length ? ` — ${t('releasesOnDay', { n: items.length })}` : ''}`}
                >
                  <span className="calendar-day">{d}</span>
                  {items.length > 0 && <span className="calendar-count">{items.length}</span>}
                </button>
              )
            })}
          </div>
        </section>

        <section className="calendar-list">
          <h2 className="section-title sm">
            {day ? date(day) : t('releasesInMonth', { n: monthCount, month: monthLabel })}
            {day && (
              <button type="button" className="btn-link" onClick={() => setDay(null)}>
                {t('viewAll')}
              </button>
            )}
          </h2>
          {!listDates.length && <p className="muted">{t('noReleasesMonth')}</p>}
          {listDates.map((d) => (
            <div key={d} className="calendar-day-group">
              <h3>
                <Icon name="calendar" size={16} /> {date(d)}
              </h3>
              <ul className="release-list compact">
                {byDate.get(d)!.map((r) => {
                  const ds = datasets.get(r.datasetId)
                  if (!ds) return null
                  return (
                    <li key={r.uploadId}>
                      <Link to={`/datasets/${ds.id}`}>
                        <span className="release-body">
                          <span className="release-title">{l(ds.title)}</span>
                          <span className="release-meta">
                            <span className="code-chip">{ds.code}</span> {l(categories.get(ds.categoryId)?.name)}
                          </span>
                        </span>
                        <Icon name="chevronRight" size={18} />
                      </Link>
                    </li>
                  )
                })}
              </ul>
            </div>
          ))}
        </section>
      </div>
    </>
  )
}
