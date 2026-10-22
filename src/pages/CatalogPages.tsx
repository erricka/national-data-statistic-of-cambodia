import { useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import { useCatalog, useIndicators } from '../hooks/useData'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { DatasetRow, ErrorState, IndicatorCard, Loading, PageHeader, SectionHead, ThemeCard } from '../components/ui'
import { Icon } from '../components/Icon'
import { NotFoundPage } from './NotFoundPage'

export function ThemesPage() {
  const { t } = useI18n()
  const catalog = useCatalog()
  useDocumentTitle(t('themesTitle'))
  if (catalog.status === 'loading') return <Loading block />
  if (catalog.status === 'error') return <ErrorState onRetry={catalog.retry} />
  const { themes, categories } = catalog.data
  return (
    <>
      <PageHeader crumbs={[{ label: t('themesTitle') }]} title={t('themesTitle')} lead={t('themesLead')} />
      <div className="container section-sm">
        <div className="theme-grid">
          {themes.map((th) => (
            <ThemeCard key={th.id} theme={th} agencies={th.categoryIds.filter((id) => (categories.find((c) => c.id === id)?.datasetCount ?? 0) > 0).length} />
          ))}
        </div>
      </div>
    </>
  )
}

export function ThemePage() {
  const { id } = useParams()
  const { t, l } = useI18n()
  const catalog = useCatalog()
  const indicators = useIndicators()
  const theme = catalog.data?.themes.find((th) => th.id === id)
  useDocumentTitle(theme ? l(theme.name) : t('themesTitle'))

  const grouped = useMemo(() => {
    if (!catalog.data || !theme) return []
    return theme.categoryIds
      .map((cid) => ({
        category: catalog.data.categories.find((c) => c.id === cid)!,
        datasets: catalog.data.datasets.filter((d) => d.categoryId === cid).sort((a, b) => a.sortOrder - b.sortOrder || a.code.localeCompare(b.code, 'en', { numeric: true })),
      }))
      .filter((g) => g.category && g.datasets.length)
  }, [catalog.data, theme])

  if (catalog.status === 'loading') return <Loading block />
  if (catalog.status === 'error') return <ErrorState onRetry={catalog.retry} />
  if (!theme) return <NotFoundPage />
  const themeIndicators = indicators.data?.filter((i) => i.themeId === theme.id) ?? []

  return (
    <>
      <PageHeader
        accent={theme.color}
        crumbs={[{ label: t('themesTitle'), to: '/themes' }, { label: l(theme.name) }]}
        eyebrow={
          <span className="theme-badge lg" style={{ ['--theme' as string]: theme.color }}>
            <Icon name={theme.icon} size={22} />
          </span>
        }
        title={l(theme.name)}
        lead={l(theme.description)}
      >
        <div className="chip-row">
          {grouped.map((g) => (
            <a key={g.category.id} href={`#agency-${g.category.id}`} className="chip">
              {g.category.abbr} <span className="chip-count">{g.datasets.length}</span>
            </a>
          ))}
        </div>
      </PageHeader>

      {themeIndicators.length > 0 && (
        <section className="section-sm">
          <div className="container">
            <SectionHead title={t('themeIndicators')} />
            <div className="indicator-grid">
              {themeIndicators.map((ind) => (
                <IndicatorCard key={ind.id} indicator={ind} theme={theme} />
              ))}
            </div>
          </div>
        </section>
      )}

      <section className="section-sm">
        <div className="container">
          <SectionHead title={t('themeDatasets')} lead={t('datasetsCount', { n: theme.datasetCount })} />
          {grouped.map((g) => (
            <div key={g.category.id} className="agency-block" id={`agency-${g.category.id}`}>
              <div className="agency-block-head">
                <h3>
                  <Link to={`/agencies/${g.category.id}`}>{l(g.category.name)}</Link>
                </h3>
                <span className="muted">{t('datasetsCount', { n: g.datasets.length })}</span>
              </div>
              <ul className="dataset-list">
                {g.datasets.map((d) => (
                  <DatasetRow key={d.id} dataset={d} theme={theme} showAgency={false} />
                ))}
              </ul>
            </div>
          ))}
        </div>
      </section>
    </>
  )
}

export function AgenciesPage() {
  const { t, l, date } = useI18n()
  const catalog = useCatalog()
  useDocumentTitle(t('agenciesTitle'))
  if (catalog.status === 'loading') return <Loading block />
  if (catalog.status === 'error') return <ErrorState onRetry={catalog.retry} />
  const { themes, categories, stats } = catalog.data
  return (
    <>
      <PageHeader crumbs={[{ label: t('agenciesTitle') }]} title={t('agenciesTitle')} lead={t('agenciesLead', { n: stats.agencies })} />
      <div className="container section-sm">
        {themes.map((th) => {
          const list = th.categoryIds.map((id) => categories.find((c) => c.id === id)).filter((c) => c && c.datasetCount > 0)
          if (!list.length) return null
          return (
            <section key={th.id} className="agency-theme">
              <h2 className="agency-theme-title" style={{ ['--theme' as string]: th.color }}>
                <span className="theme-badge" style={{ ['--theme' as string]: th.color }}>
                  <Icon name={th.icon} size={18} />
                </span>
                {l(th.name)}
              </h2>
              <ul className="agency-cards">
                {list.map((c) => (
                  <li key={c!.id}>
                    <Link to={`/agencies/${c!.id}`} className="agency-card" style={{ ['--theme' as string]: th.color }}>
                      <span className="agency-abbr">{c!.abbr}</span>
                      <span className="agency-card-body">
                        <strong>{l(c!.name)}</strong>
                        <small>
                          {t('datasetsCount', { n: c!.datasetCount })} · {t('updated')} {date(c!.lastUpdated, 'short')}
                        </small>
                      </span>
                      <Icon name="chevronRight" size={18} />
                    </Link>
                  </li>
                ))}
              </ul>
            </section>
          )
        })}
      </div>
    </>
  )
}

export function AgencyPage() {
  const { id } = useParams()
  const { t, l, lOther } = useI18n()
  const catalog = useCatalog()
  const category = catalog.data?.categories.find((c) => String(c.id) === id)
  useDocumentTitle(category ? l(category.name) : t('agenciesTitle'))
  if (catalog.status === 'loading') return <Loading block />
  if (catalog.status === 'error') return <ErrorState onRetry={catalog.retry} />
  if (!category) return <NotFoundPage />
  const theme = catalog.data.themes.find((th) => th.id === category.themeId)
  const datasets = catalog.data.datasets
    .filter((d) => d.categoryId === category.id)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.code.localeCompare(b.code, 'en', { numeric: true }))

  return (
    <>
      <PageHeader
        accent={theme?.color}
        crumbs={[{ label: t('agenciesTitle'), to: '/agencies' }, { label: category.abbr ?? l(category.name) }]}
        eyebrow={
          <>
            <span className="code-chip code-chip-lg">{category.abbr}</span>
            {theme && (
              <Link to={`/themes/${theme.id}`} className="eyebrow-link">
                <Icon name={theme.icon} size={14} /> {l(theme.name)}
              </Link>
            )}
          </>
        }
        title={l(category.name)}
        subtitle={lOther(category.name)}
      />
      <section className="section-sm">
        <div className="container">
          <SectionHead title={t('agencyDatasets')} lead={t('datasetsCount', { n: datasets.length })} action={<Link className="link-arrow" to={`/find-data?agency=${category.id}`}>{t('navFindData')} <Icon name="arrowRight" size={16} /></Link>} />
          <ul className="dataset-list">
            {datasets.map((d) => (
              <DatasetRow key={d.id} dataset={d} theme={theme} showAgency={false} />
            ))}
          </ul>
        </div>
      </section>
    </>
  )
}
