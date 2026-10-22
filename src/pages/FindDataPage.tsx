import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import { useCatalog } from '../hooks/useData'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { searchDatasets } from '../data/search'
import type { Dataset, Frequency } from '../data/types'
import { SearchBox } from '../components/SearchBox'
import { DatasetRow, ErrorState, Loading, PageHeader } from '../components/ui'
import { Icon } from '../components/Icon'

const FREQS: Exclude<Frequency, null>[] = ['M', 'Q', 'S', 'A']
type Sort = 'relevance' | 'updated' | 'title' | 'code'

export function FindDataPage() {
  const { t, l, lang, freq, num } = useI18n()
  const catalog = useCatalog()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const theme = params.get('theme') ?? ''
  const agency = params.get('agency') ?? ''
  const fq = params.get('freq') ?? ''
  const view = params.get('view') === 'az' ? 'az' : 'list'
  const sort = (params.get('sort') as Sort) || (q ? 'relevance' : 'updated')
  const [limit, setLimit] = useState(30)
  const [filtersOpen, setFiltersOpen] = useState(false)
  useDocumentTitle(t('findDataTitle'))

  const set = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: key !== 'q' })
    setLimit(30)
  }

  const data = catalog.data
  const hits = useMemo(() => (data ? searchDatasets(data, q) : []), [data, q])

  const filtered = useMemo(() => {
    let list = hits.filter(
      (h) =>
        (!theme || h.dataset.themeId === theme) &&
        (!agency || String(h.dataset.categoryId) === agency) &&
        (!fq || (h.dataset.frequency ?? 'x') === fq),
    )
    const byTitle = (a: Dataset, b: Dataset) => l(a.title).localeCompare(l(b.title), lang)
    if (sort === 'updated') list = [...list].sort((a, b) => b.dataset.lastUpdated.localeCompare(a.dataset.lastUpdated) || byTitle(a.dataset, b.dataset))
    if (sort === 'title' || view === 'az') list = [...list].sort((a, b) => byTitle(a.dataset, b.dataset))
    if (sort === 'code') list = [...list].sort((a, b) => a.dataset.code.localeCompare(b.dataset.code, 'en', { numeric: true }))
    return list.map((h) => h.dataset)
  }, [hits, theme, agency, fq, sort, view, l, lang])

  // Facet counts reflect the other active filters
  const counts = useMemo(() => {
    const th = new Map<string, number>()
    const ag = new Map<number, number>()
    const fr = new Map<string, number>()
    for (const { dataset: d } of hits) {
      const okTheme = !theme || d.themeId === theme
      const okAgency = !agency || String(d.categoryId) === agency
      const okFreq = !fq || (d.frequency ?? 'x') === fq
      if (okAgency && okFreq && d.themeId) th.set(d.themeId, (th.get(d.themeId) ?? 0) + 1)
      if (okTheme && okFreq) ag.set(d.categoryId, (ag.get(d.categoryId) ?? 0) + 1)
      if (okTheme && okAgency) fr.set(d.frequency ?? 'x', (fr.get(d.frequency ?? 'x') ?? 0) + 1)
    }
    return { th, ag, fr }
  }, [hits, theme, agency, fq])

  if (catalog.status === 'loading') return <Loading block />
  if (catalog.status === 'error' || !data) return <ErrorState onRetry={catalog.retry} />

  const categories = new Map(data.categories.map((c) => [c.id, c]))
  const themes = new Map(data.themes.map((th) => [th.id, th]))
  const activeFilters = [theme, agency, fq].filter(Boolean).length

  const groups = view === 'az'
    ? filtered.reduce<Map<string, Dataset[]>>((m, d) => {
        const first = l(d.title).trim().charAt(0).toUpperCase() || '#'
        const key = /[0-9]/.test(first) ? '#' : first
        if (!m.has(key)) m.set(key, [])
        m.get(key)!.push(d)
        return m
      }, new Map())
    : null

  return (
    <>
      <PageHeader crumbs={[{ label: t('findDataTitle') }]} title={t('findDataTitle')} lead={t('findDataLead', { n: num(data.stats.datasets) })}>
        <div className="page-search">
          <SearchBox size="lg" initial={q} onSubmit={(v) => set('q', v)} />
        </div>
      </PageHeader>

      <div className="container find-layout">
        <aside className={`filters${filtersOpen ? ' open' : ''}`} aria-label={t('filters')}>
          <div className="filters-head">
            <h2>{t('filters')}</h2>
            {activeFilters > 0 && (
              <button type="button" className="btn-link" onClick={() => { setParams(q ? { q } : {}); setLimit(30) }}>
                {t('clearFilters')}
              </button>
            )}
          </div>

          <fieldset className="facet">
            <legend>{t('filterTheme')}</legend>
            <label className={!theme ? 'on' : ''}>
              <input type="radio" name="theme" checked={!theme} onChange={() => set('theme', '')} />
              <span>{t('all')}</span>
            </label>
            {data.themes.map((th) => (
              <label key={th.id} className={theme === th.id ? 'on' : ''} style={{ ['--theme' as string]: th.color }}>
                <input type="radio" name="theme" checked={theme === th.id} onChange={() => set('theme', th.id)} />
                <span className="facet-dot" />
                <span>{l(th.name)}</span>
                <span className="facet-count">{counts.th.get(th.id) ?? 0}</span>
              </label>
            ))}
          </fieldset>

          <fieldset className="facet">
            <legend>{t('filterFrequency')}</legend>
            <label className={!fq ? 'on' : ''}>
              <input type="radio" name="freq" checked={!fq} onChange={() => set('freq', '')} />
              <span>{t('all')}</span>
            </label>
            {[...FREQS, 'x' as const].map((f) => (counts.fr.get(f) ? (
              <label key={f} className={fq === f ? 'on' : ''}>
                <input type="radio" name="freq" checked={fq === f} onChange={() => set('freq', f)} />
                <span>{freq(f === 'x' ? null : f)}</span>
                <span className="facet-count">{counts.fr.get(f)}</span>
              </label>
            ) : null))}
          </fieldset>

          <div className="facet">
            <label className="field-label" htmlFor="agency-filter">{t('filterAgency')}</label>
            <select id="agency-filter" value={agency} onChange={(e) => set('agency', e.target.value)}>
              <option value="">{t('all')}</option>
              {data.categories
                .filter((c) => c.datasetCount > 0)
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {l(c.name)} ({counts.ag.get(c.id) ?? 0})
                  </option>
                ))}
            </select>
          </div>
        </aside>

        <section className="results" aria-live="polite">
          <div className="results-bar">
            <p className="results-count">
              {q ? t('resultsFor', { n: num(filtered.length), q }) : t('resultsCount', { n: num(filtered.length) })}
            </p>
            <div className="results-tools">
              <button type="button" className="btn btn-outline btn-sm filters-toggle" onClick={() => setFiltersOpen((o) => !o)} aria-expanded={filtersOpen}>
                <Icon name="filter" size={16} /> {t('filters')} {activeFilters > 0 && <span className="badge">{activeFilters}</span>}
              </button>
              <div className="segmented" role="radiogroup" aria-label="View">
                <button type="button" role="radio" aria-checked={view === 'list'} className={view === 'list' ? 'on' : ''} onClick={() => set('view', '')}>
                  <Icon name="menu" size={16} /> {t('viewList')}
                </button>
                <button type="button" role="radio" aria-checked={view === 'az'} className={view === 'az' ? 'on' : ''} onClick={() => set('view', 'az')}>
                  {t('viewAZ')}
                </button>
              </div>
              {view === 'list' && (
                <label className="sort">
                  <span>{t('sortBy')}</span>
                  <select value={sort} onChange={(e) => set('sort', e.target.value)}>
                    {q && <option value="relevance">{t('sortRelevance')}</option>}
                    <option value="updated">{t('sortUpdated')}</option>
                    <option value="title">{t('sortTitle')}</option>
                    <option value="code">{t('sortCode')}</option>
                  </select>
                </label>
              )}
            </div>
          </div>

          {activeFilters > 0 && (
            <div className="active-filters">
              {theme && (
                <button type="button" className="chip chip-removable" onClick={() => set('theme', '')}>
                  {l(themes.get(theme)?.name)} <Icon name="close" size={14} />
                </button>
              )}
              {agency && (
                <button type="button" className="chip chip-removable" onClick={() => set('agency', '')}>
                  {categories.get(Number(agency))?.abbr ?? l(categories.get(Number(agency))?.name)} <Icon name="close" size={14} />
                </button>
              )}
              {fq && (
                <button type="button" className="chip chip-removable" onClick={() => set('freq', '')}>
                  {freq(fq === 'x' ? null : (fq as Frequency))} <Icon name="close" size={14} />
                </button>
              )}
            </div>
          )}

          {!filtered.length && (
            <div className="empty">
              <Icon name="search" size={32} />
              <h3>{t('noResults')}</h3>
              <p>{t('noResultsHint')}</p>
              <Link to="/find-data" className="btn btn-outline btn-sm">{t('clearFilters')}</Link>
            </div>
          )}

          {groups ? (
            <>
              <nav className="az-index" aria-label="A–Z">
                {[...groups.keys()].map((k) => (
                  <a key={k} href={`#az-${k}`}>{k}</a>
                ))}
              </nav>
              {[...groups.entries()].map(([k, list]) => (
                <div key={k} className="az-group" id={`az-${k}`}>
                  <h2 className="az-letter">{k}</h2>
                  <ul className="dataset-list">
                    {list.map((d) => (
                      <DatasetRow key={d.id} dataset={d} category={categories.get(d.categoryId)} theme={themes.get(d.themeId ?? '')} />
                    ))}
                  </ul>
                </div>
              ))}
            </>
          ) : (
            <>
              <ul className="dataset-list">
                {filtered.slice(0, limit).map((d) => (
                  <DatasetRow key={d.id} dataset={d} category={categories.get(d.categoryId)} theme={themes.get(d.themeId ?? '')} />
                ))}
              </ul>
              {filtered.length > limit && (
                <div className="more">
                  <button type="button" className="btn btn-outline" onClick={() => setLimit((n) => n + 30)}>
                    {t('viewAll')} ({num(filtered.length - limit)})
                    <Icon name="chevronDown" size={16} />
                  </button>
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </>
  )
}
