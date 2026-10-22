import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import { useCatalog, useTable } from '../hooks/useData'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { prepareTable, downloadCsv, downloadJson, downloadBlob } from '../data/table'
import { tableToXlsx } from '../data/xlsx'
import { config, officialDownloadUrl } from '../data/repository'
import type { Category, Dataset, Theme } from '../data/types'
import { StatTableView, useFilteredRows } from '../components/table/StatTableView'
import { ChartBuilder } from '../components/table/ChartBuilder'
import { DatasetRow, ErrorState, FreqTag, Loading, PageHeader } from '../components/ui'
import { Icon } from '../components/Icon'

type Tab = 'table' | 'chart' | 'about'

export function DatasetPage() {
  const { id = '' } = useParams()
  const catalog = useCatalog()
  const { t } = useI18n()

  if (catalog.status === 'loading') return <Loading block />
  if (catalog.status === 'error') return <ErrorState onRetry={catalog.retry} />
  const dataset = catalog.data.datasets.find((d) => d.id === id)
  if (!dataset) {
    return (
      <PageHeader crumbs={[{ label: t('findDataTitle'), to: '/find-data' }, { label: id }]} title={t('datasetNotFound')} lead={t('datasetNotFoundLead')}>
        <Link to={`/find-data?q=${encodeURIComponent(id.replace(/^nis_/, ''))}`} className="btn btn-primary">
          <Icon name="search" size={16} /> {t('searchButton')}
        </Link>
      </PageHeader>
    )
  }
  const category = catalog.data.categories.find((c) => c.id === dataset.categoryId)
  const theme = catalog.data.themes.find((th) => th.id === dataset.themeId)
  const related = catalog.data.datasets.filter((d) => d.categoryId === dataset.categoryId && d.id !== dataset.id).slice(0, 6)
  return <DatasetView key={dataset.id} dataset={dataset} category={category} theme={theme} related={related} />
}

function DatasetView({ dataset, category, theme, related }: { dataset: Dataset; category?: Category; theme?: Theme; related: Dataset[] }) {
  const { t, l, lOther, lang, date, num, freq } = useI18n()
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('view') === 'chart' ? 'chart' : params.get('view') === 'about' ? 'about' : 'table'
  const raw = useTable(dataset.id)
  const table = useMemo(() => (raw.data ? prepareTable(raw.data) : null), [raw.data])
  const [query, setQuery] = useState('')
  const rows = useFilteredRows(table?.rows, query)
  const [copied, setCopied] = useState(false)
  useDocumentTitle(l(dataset.title))

  const setTab = (v: Tab) => {
    const next = new URLSearchParams(params)
    if (v === 'table') next.delete('view')
    else next.set('view', v)
    setParams(next, { replace: true })
  }

  const fileBase = `${dataset.id}_${dataset.lastUpdated}`
  const sourceLine = `${t('source')}: ${l(category?.name)} · ${t('orgName')}`
  const official = officialDownloadUrl(dataset.id)
  const pageUrl = typeof window !== 'undefined' ? window.location.href.split('?')[0] : ''
  const jsonUrl = new URL(`${config.dataBase}/tables/${dataset.id}.json`, typeof window !== 'undefined' ? window.location.origin : 'http://localhost').toString()

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* clipboard unavailable */
    }
  }

  const coverage = dataset.coverage ? (dataset.coverage.from === dataset.coverage.to ? String(dataset.coverage.from) : `${dataset.coverage.from}–${dataset.coverage.to}`) : '–'
  const citation = `${t('orgName')} (${dataset.lastUpdated.slice(0, 4)}). ${l(dataset.title)} [${dataset.code}]. ${l(category?.name)}. ${pageUrl} (${lang === 'km' ? 'ចូលមើល' : 'accessed'} ${date(new Date().toISOString().slice(0, 10))}).`

  return (
    <>
      <PageHeader
        accent={theme?.color}
        crumbs={[
          { label: t('findDataTitle'), to: '/find-data' },
          ...(theme ? [{ label: l(theme.name), to: `/themes/${theme.id}` }] : []),
          ...(category ? [{ label: category.abbr ?? l(category.name), to: `/agencies/${category.id}` }] : []),
          { label: dataset.code },
        ]}
        eyebrow={
          <>
            <span className="code-chip code-chip-lg">{dataset.code}</span>
            <FreqTag dataset={dataset} />
            {category && (
              <Link to={`/agencies/${category.id}`} className="eyebrow-link">
                <Icon name="building" size={14} /> {l(category.name)}
              </Link>
            )}
          </>
        }
        title={l(dataset.title)}
        subtitle={lOther(dataset.title) !== l(dataset.title) ? lOther(dataset.title) : undefined}
      >
        <dl className="meta-strip">
          <div>
            <dt>{t('lastUpdated')}</dt>
            <dd>{date(dataset.lastUpdated)}</dd>
          </div>
          <div>
            <dt>{t('coverage')}</dt>
            <dd>{coverage}</dd>
          </div>
          <div>
            <dt>{t('frequency')}</dt>
            <dd>{freq(dataset.frequency)}</dd>
          </div>
          <div>
            <dt>{t('records')}</dt>
            <dd>{num(dataset.records)}</dd>
          </div>
          <div>
            <dt>{t('releases')}</dt>
            <dd>{dataset.releases.length}</dd>
          </div>
        </dl>
        <div className="action-bar">
          <DownloadMenu
            disabled={!table}
            items={[
              { label: t('downloadXlsx'), icon: 'table', onClick: () => table && downloadBlob(tableToXlsx(table, { title: l(dataset.title), source: sourceLine }), `${fileBase}.xlsx`, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet') },
              { label: t('downloadCsv'), icon: 'download', onClick: () => table && downloadCsv(table, `${fileBase}.csv`) },
              { label: t('downloadJson'), icon: 'code', onClick: () => table && downloadJson(table, `${fileBase}.json`) },
              ...(official ? [{ label: t('downloadOfficial'), icon: 'external', href: official }] : []),
            ]}
          />
          <button type="button" className="btn btn-ghost" onClick={copyLink}>
            <Icon name={copied ? 'check' : 'link'} size={16} /> {copied ? t('copied') : t('copyLink')}
          </button>
          <button type="button" className="btn btn-ghost hide-sm" onClick={() => window.print()}>
            <Icon name="print" size={16} /> {t('print')}
          </button>
        </div>
      </PageHeader>

      <div className="container dataset-body">
        <div className="tabs" role="tablist" aria-label={l(dataset.title)}>
          {(
            [
              ['table', 'table', t('tabTable')],
              ['chart', 'chart', t('tabChart')],
              ['about', 'info', t('tabAbout')],
            ] as const
          ).map(([k, icon, label]) => (
            <button key={k} type="button" role="tab" aria-selected={tab === k} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>
              <Icon name={icon} size={18} /> {label}
            </button>
          ))}
        </div>

        <div className="tab-panel" role="tabpanel">
          {tab !== 'about' && raw.status === 'loading' && <Loading block />}
          {tab !== 'about' && raw.status === 'error' && <ErrorState onRetry={raw.retry} />}
          {tab !== 'about' && table && !table.rows.length && (
            <div className="empty">
              <Icon name="table" size={32} />
              <p>{t('noDataRows')}</p>
            </div>
          )}
          {tab === 'table' && table && table.rows.length > 0 && (
            <>
              <StatTableView table={table} rows={rows} query={query} onQuery={setQuery} />
              <p className="table-source">
                <Icon name="info" size={14} /> {sourceLine}. {t('tableNote')}
              </p>
            </>
          )}
          {tab === 'chart' && table && table.rows.length > 0 && (
            <>
              {query && (
                <p className="chart-filter-note">
                  <Icon name="filter" size={14} /> {t('rowsFiltered', { n: num(rows.length), total: num(table.rows.length) })} — “{query}”
                  <button type="button" className="btn-link" onClick={() => setQuery('')}>{t('chartClear')}</button>
                </p>
              )}
              <ChartBuilder table={table} rows={rows} title={l(dataset.title)} />
              <p className="table-source">
                <Icon name="info" size={14} /> {sourceLine}
              </p>
            </>
          )}
          {tab === 'about' && (
            <div className="about-grid">
              <section className="card">
                <h2 className="card-title">{t('tabAbout')}</h2>
                <dl className="kv">
                  <dt>{t('tableId')}</dt>
                  <dd><code>{dataset.id}</code></dd>
                  <dt>{lang === 'en' ? 'Title' : 'ចំណងជើង'}</dt>
                  <dd>{dataset.title.en}<br /><span lang="km">{dataset.title.km}</span></dd>
                  <dt>{t('agency')}</dt>
                  <dd>{category ? <Link to={`/agencies/${category.id}`}>{l(category.name)}</Link> : '–'}</dd>
                  <dt>{t('theme')}</dt>
                  <dd>{theme ? <Link to={`/themes/${theme.id}`}>{l(theme.name)}</Link> : '–'}</dd>
                  <dt>{t('frequency')}</dt>
                  <dd>{freq(dataset.frequency)}</dd>
                  <dt>{t('coverage')}</dt>
                  <dd>{coverage}</dd>
                  <dt>{t('records')}</dt>
                  <dd>{num(dataset.records)}</dd>
                  {dataset.table && (
                    <>
                      <dt>{t('tabTable')}</dt>
                      <dd>{t('rowsCount', { n: num(dataset.table.rows) })} × {t('columnsCount', { n: num(dataset.table.columns) })}</dd>
                    </>
                  )}
                </dl>
              </section>

              <section className="card">
                <h2 className="card-title">{t('releaseHistory')}</h2>
                <table className="simple-table">
                  <thead>
                    <tr>
                      <th>{t('released')}</th>
                      <th>{t('submitted')}</th>
                      <th>{t('uploadId')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dataset.releases.map((r) => (
                      <tr key={r.uploadId}>
                        <td>{date(r.date, 'short')}</td>
                        <td>{date(r.submitted, 'short')}</td>
                        <td><code className="wrap">{r.uploadId}</code></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>

              <section className="card">
                <h2 className="card-title">{t('howToCite')}</h2>
                <p className="citation">{citation}</p>
              </section>

              <section className="card">
                <h2 className="card-title">{t('dataAccess')}</h2>
                <p>{t('dataAccessBody', { id: dataset.id })}</p>
                <pre className="code-block"><code>{`GET ${jsonUrl}

POST ${config.apiBase || '<NIS_API>'}/DTSI003
{ "sourceId": "${dataset.id}", "upload_id": "", "page": 1, "size": 0 }`}</code></pre>
              </section>
            </div>
          )}
        </div>

        {related.length > 0 && (
          <section className="related">
            <h2 className="section-title sm">{t('related')}</h2>
            <ul className="dataset-list">
              {related.map((d) => (
                <DatasetRow key={d.id} dataset={d} theme={theme} showAgency={false} />
              ))}
            </ul>
          </section>
        )}
      </div>
    </>
  )
}

interface MenuItem {
  label: string
  icon: string
  onClick?: () => void
  href?: string
}

function DownloadMenu({ items, disabled }: { items: MenuItem[]; disabled?: boolean }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const close = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && setOpen(false)
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])
  return (
    <div className="menu" ref={ref}>
      <button type="button" className="btn btn-primary" aria-haspopup="menu" aria-expanded={open} disabled={disabled} onClick={() => setOpen((o) => !o)}>
        <Icon name="download" size={16} /> {t('download')} <Icon name="chevronDown" size={16} />
      </button>
      {open && (
        <div className="menu-panel" role="menu">
          {items.map((it) =>
            it.href ? (
              <a key={it.label} role="menuitem" href={it.href} onClick={() => setOpen(false)}>
                <Icon name={it.icon} size={16} /> {it.label}
              </a>
            ) : (
              <button
                key={it.label}
                type="button"
                role="menuitem"
                onClick={() => {
                  it.onClick?.()
                  setOpen(false)
                }}
              >
                <Icon name={it.icon} size={16} /> {it.label}
              </button>
            ),
          )}
        </div>
      )}
    </div>
  )
}
