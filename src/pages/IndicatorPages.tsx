import { Link } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import { useCatalog, useIndicators, useProvinces } from '../hooks/useData'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { ChangeBadge, ErrorState, Loading, PageHeader } from '../components/ui'
import { IndicatorChart } from '../components/charts/IndicatorChart'
import { ProvincePanel } from '../components/ProvincePanel'
import { Icon } from '../components/Icon'

export function IndicatorsPage() {
  const { t, l, fmt, period, freq } = useI18n()
  const indicators = useIndicators()
  const catalog = useCatalog()
  useDocumentTitle(t('indicatorsTitle'))
  if (indicators.status === 'loading' || catalog.status === 'loading') return <Loading block />
  if (indicators.status === 'error') return <ErrorState onRetry={indicators.retry} />
  if (catalog.status === 'error') return <ErrorState onRetry={catalog.retry} />
  const themes = new Map(catalog.data.themes.map((th) => [th.id, th]))
  const datasets = new Map(catalog.data.datasets.map((d) => [d.id, d]))
  const categories = new Map(catalog.data.categories.map((c) => [c.id, c]))

  return (
    <>
      <PageHeader crumbs={[{ label: t('indicatorsTitle') }]} title={t('indicatorsTitle')} lead={t('indicatorsLead')}>
        <nav className="chip-row" aria-label={t('indicatorsTitle')}>
          {indicators.data.map((ind) => (
            <a key={ind.id} href={`#ind-${ind.id}`} className="chip">
              {l(ind.title)}
            </a>
          ))}
        </nav>
      </PageHeader>
      <div className="container section-sm indicator-panels">
        {indicators.data.map((ind) => {
          const theme = themes.get(ind.themeId)
          const ds = datasets.get(ind.datasetId)
          const cat = ds ? categories.get(ds.categoryId) : undefined
          return (
            <article key={ind.id} id={`ind-${ind.id}`} className="indicator-panel" style={{ ['--theme' as string]: theme?.color }}>
              <header className="indicator-panel-head">
                <div>
                  <span className="indicator-panel-theme">
                    <Icon name={theme?.icon ?? 'chart'} size={14} /> {l(theme?.name)} · {freq(ind.frequency)}
                  </span>
                  <h2>{l(ind.title)}</h2>
                </div>
                <div className="indicator-panel-value">
                  <span className="value">
                    {fmt(ind.latest.value, ind.format)} {l(ind.unit) !== '%' && <small>{l(ind.unit)}</small>}
                  </span>
                  <span className="period">{period(ind.latest.period)}</span>
                  <ChangeBadge indicator={ind} />
                </div>
              </header>
              <IndicatorChart indicator={ind} color={theme?.color} height={300} />
              <footer className="indicator-panel-foot">
                <span>
                  {t('source')}: {l(cat?.name)} · <span className="code-chip">{ds?.code}</span>
                </span>
                <Link to={`/datasets/${ind.datasetId}`} className="link-arrow">
                  {t('viewData')} <Icon name="arrowRight" size={16} />
                </Link>
              </footer>
            </article>
          )
        })}
      </div>
    </>
  )
}

export function ProvincesPage() {
  const { t } = useI18n()
  const provinces = useProvinces()
  useDocumentTitle(t('provincesTitle'))
  return (
    <>
      <PageHeader crumbs={[{ label: t('provincesTitle') }]} title={t('provincesTitle')} lead={t('provincesPageLead')} />
      <div className="container section-sm">
        {provinces.status === 'loading' && <Loading block />}
        {provinces.status === 'error' && <ErrorState onRetry={provinces.retry} />}
        {provinces.data && <ProvincePanel provinces={provinces.data.provinces} indicators={provinces.data.indicators} full />}
      </div>
    </>
  )
}
