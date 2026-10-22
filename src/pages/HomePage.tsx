import { lazy, Suspense, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import { useCatalog, useIndicators, useProvinces } from '../hooks/useData'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { SearchBox } from '../components/SearchBox'
import { ChangeBadge, ErrorState, IndicatorCard, Loading, MoreLink, SectionHead, ThemeCard } from '../components/ui'
import { ProvincePanel } from '../components/ProvincePanel'
import { Icon } from '../components/Icon'

const POPULAR = [
  { en: 'Inflation', km: 'អតិផរណា', q: 'inflation' },
  { en: 'Tourism', km: 'ទេសចរណ៍', q: 'tourism' },
  { en: 'Exchange rate', km: 'អត្រាប្តូរប្រាក់', q: 'exchange rate' },
  { en: 'Schools', km: 'សាលារៀន', q: 'schools' },
  { en: 'Rice', km: 'ស្រូវ', q: 'rice' },
  { en: 'Investment', km: 'វិនិយោគ', q: 'investment' },
]

const FOCUS = ['tourist-arrivals', 'cpi-inflation', 'imports', 'air-passengers']

// ECharts is large, so the focus chart loads after the rest of the page
const IndicatorChart = lazy(() => import('../components/charts/IndicatorChart').then((m) => ({ default: m.IndicatorChart })))

export function HomePage() {
  const { t, l, lang, num, date, fmt, period } = useI18n()
  useDocumentTitle()
  const catalog = useCatalog()
  const indicators = useIndicators()
  const provinces = useProvinces()
  const [focusId, setFocusId] = useState(FOCUS[0])

  const themeById = useMemo(() => new Map(catalog.data?.themes.map((th) => [th.id, th]) ?? []), [catalog.data])
  const datasetById = useMemo(() => new Map(catalog.data?.datasets.map((d) => [d.id, d]) ?? []), [catalog.data])
  const categoryById = useMemo(() => new Map(catalog.data?.categories.map((c) => [c.id, c]) ?? []), [catalog.data])
  const focusList = indicators.data?.filter((i) => FOCUS.includes(i.id)).sort((a, b) => FOCUS.indexOf(a.id) - FOCUS.indexOf(b.id)) ?? []
  const focus = focusList.find((i) => i.id === focusId) ?? focusList[0]
  const stats = catalog.data?.stats

  return (
    <div className="home">
      <section className="hero">
        <div className="hero-art" aria-hidden="true">
          <svg viewBox="0 0 1200 400" preserveAspectRatio="none">
            <path d="M0 330 C 150 300, 250 340, 380 280 S 620 220, 760 240 S 1000 140, 1200 120" />
            <path d="M0 360 C 180 350, 300 360, 420 320 S 680 290, 820 280 S 1040 220, 1200 210" />
            <path d="M0 300 C 120 250, 260 300, 400 230 S 600 170, 740 180 S 980 80, 1200 50" />
          </svg>
          <div className="hero-bars">
            {[38, 52, 46, 64, 58, 72, 68, 84, 78, 92].map((h, i) => (
              <i key={i} style={{ height: `${h}%` }} />
            ))}
          </div>
        </div>
        <div className="container hero-inner">
          <p className="hero-eyebrow">{t('heroEyebrow')}</p>
          <h1 className="hero-title">{t('heroTitle')}</h1>
          <p className="hero-lead">{t('heroLead', { n: stats?.agencies ?? 29 })}</p>
          <SearchBox size="lg" />
          <div className="hero-popular">
            <span>{t('popular')}</span>
            {POPULAR.map((p) => (
              <Link key={p.q} to={`/find-data?q=${encodeURIComponent(p.q)}`} className="chip chip-light">
                {p[lang]}
              </Link>
            ))}
          </div>
        </div>
        {stats && (
          <div className="container">
            <dl className="hero-stats">
              <div>
                <dt>{t('statDatasets')}</dt>
                <dd>{num(stats.datasets)}</dd>
              </div>
              <div>
                <dt>{t('statAgencies')}</dt>
                <dd>{num(stats.agencies)}</dd>
              </div>
              <div>
                <dt>{t('statRecords')}</dt>
                <dd>{num(stats.records)}</dd>
              </div>
              <div>
                <dt>{t('statUpdated')}</dt>
                <dd>{date(stats.lastRelease, 'short')}</dd>
              </div>
            </dl>
          </div>
        )}
      </section>

      <section className="section" aria-labelledby="latest-data">
        <div className="container">
          <SectionHead id="latest-data" title={t('latestData')} lead={t('latestDataLead')} action={<MoreLink to="/indicators">{t('viewAllIndicators')}</MoreLink>} />
          {indicators.status === 'loading' && <Loading block />}
          {indicators.status === 'error' && <ErrorState onRetry={indicators.retry} />}
          {indicators.data && (
            <div className="indicator-grid">
              {indicators.data.map((ind) => (
                <IndicatorCard key={ind.id} indicator={ind} theme={themeById.get(ind.themeId)} />
              ))}
            </div>
          )}
        </div>
      </section>

      <section className="section section-tint" aria-labelledby="themes">
        <div className="container">
          <SectionHead id="themes" title={t('searchByTheme')} lead={t('searchByThemeLead')} action={<MoreLink to="/themes">{t('viewAll')}</MoreLink>} />
          {catalog.status === 'loading' && <Loading block />}
          {catalog.status === 'error' && <ErrorState onRetry={catalog.retry} />}
          {catalog.data && (
            <div className="theme-grid">
              {catalog.data.themes.map((th) => (
                <ThemeCard key={th.id} theme={th} agencies={th.categoryIds.filter((id) => (categoryById.get(id)?.datasetCount ?? 0) > 0).length} />
              ))}
            </div>
          )}
        </div>
      </section>

      {focus && (
        <section className="section" aria-labelledby="focus">
          <div className="container">
            <SectionHead id="focus" title={t('dataInFocus')} lead={t('dataInFocusLead')} />
            <div className="focus">
              <div className="focus-tabs" role="tablist" aria-label={t('dataInFocus')}>
                {focusList.map((ind) => (
                  <button key={ind.id} type="button" role="tab" aria-selected={ind.id === focus.id} className={ind.id === focus.id ? 'on' : ''} onClick={() => setFocusId(ind.id)}>
                    {l(ind.title)}
                  </button>
                ))}
              </div>
              <div className="focus-body" role="tabpanel">
                <div className="focus-summary">
                  <span className="focus-label">{t('latest')} · {period(focus.latest.period)}</span>
                  <span className="focus-value">
                    {fmt(focus.latest.value, focus.format)} {l(focus.unit) !== '%' && <small>{l(focus.unit)}</small>}
                  </span>
                  <ChangeBadge indicator={focus} />
                  <p className="focus-source">
                    {t('source')}: {l(categoryById.get(datasetById.get(focus.datasetId)?.categoryId ?? -1)?.name)}
                  </p>
                  <Link to={`/datasets/${focus.datasetId}`} className="btn btn-primary btn-sm">
                    <Icon name="table" size={16} /> {t('viewData')}
                  </Link>
                </div>
                <div className="focus-chart">
                  <Suspense fallback={<div className="chart-placeholder" style={{ height: 340 }} />}>
                    <IndicatorChart indicator={focus} color={themeById.get(focus.themeId)?.color} height={340} />
                  </Suspense>
                </div>
              </div>
            </div>
          </div>
        </section>
      )}

      <section className="section section-tint" aria-labelledby="provinces">
        <div className="container">
          <SectionHead id="provinces" title={t('provincesAtAGlance')} lead={t('provincesLead')} action={<MoreLink to="/provinces">{t('exploreProvinces')}</MoreLink>} />
          {provinces.status === 'loading' && <Loading block />}
          {provinces.status === 'error' && <ErrorState onRetry={provinces.retry} />}
          {provinces.data && <ProvincePanel provinces={provinces.data.provinces} indicators={provinces.data.indicators} />}
        </div>
      </section>

      {catalog.data && (
        <section className="section" aria-labelledby="recent">
          <div className="container split">
            <div>
              <SectionHead id="recent" title={t('recentReleases')} lead={t('recentReleasesLead')} action={<MoreLink to="/release-calendar">{t('viewCalendar')}</MoreLink>} />
              <ul className="release-list">
                {catalog.data.releases.slice(0, 8).map((r) => {
                  const d = datasetById.get(r.datasetId)
                  if (!d) return null
                  const [y, m, day] = r.date.split('-')
                  return (
                    <li key={r.uploadId}>
                      <Link to={`/datasets/${d.id}`}>
                        <span className="release-date" aria-label={date(r.date)}>
                          <b>{Number(day)}</b>
                          <span>{date(`${y}-${m}`, 'short')}</span>
                        </span>
                        <span className="release-body">
                          <span className="release-title">{l(d.title)}</span>
                          <span className="release-meta">
                            <span className="code-chip">{d.code}</span> {l(categoryById.get(d.categoryId)?.name)}
                          </span>
                        </span>
                        <Icon name="chevronRight" size={18} />
                      </Link>
                    </li>
                  )
                })}
              </ul>
            </div>
            <aside className="cta-stack">
              <div className="cta-card cta-primary">
                <Icon name="download" size={26} />
                <h3>{t('useOurData')}</h3>
                <p>{t('useOurDataLead')}</p>
                <Link to="/about#data-access" className="link-arrow light">
                  {t('dataAccess')} <Icon name="arrowRight" size={16} />
                </Link>
              </div>
              <div className="cta-card">
                <Icon name="search" size={26} />
                <h3>{t('cantFind')}</h3>
                <p>{t('cantFindLead')}</p>
                <Link to="/find-data?view=az" className="link-arrow">
                  {t('navAllDatasets')} <Icon name="arrowRight" size={16} />
                </Link>
              </div>
            </aside>
          </div>
        </section>
      )}

      {catalog.data && (
        <section className="section section-tint" aria-labelledby="agencies">
          <div className="container">
            <SectionHead id="agencies" title={t('browseByAgency')} lead={t('browseByAgencyLead')} action={<MoreLink to="/agencies">{t('viewAll')}</MoreLink>} />
            <ul className="agency-grid">
              {catalog.data.categories
                .filter((c) => c.datasetCount > 0)
                .map((c) => (
                  <li key={c.id}>
                    <Link to={`/agencies/${c.id}`} className="agency-chip" style={{ ['--theme' as string]: themeById.get(c.themeId ?? '')?.color }}>
                      <span className="agency-abbr">{c.abbr}</span>
                      <span className="agency-name">{l(c.name)}</span>
                      <span className="agency-count">{c.datasetCount}</span>
                    </Link>
                  </li>
                ))}
            </ul>
          </div>
        </section>
      )}
    </div>
  )
}
