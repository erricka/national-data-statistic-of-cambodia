import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import type { Category, Dataset, Indicator, SeriesPoint, Theme } from '../data/types'
import { Icon } from './Icon'

export interface Crumb {
  label: string
  to?: string
}

export function Breadcrumbs({ items }: { items: Crumb[] }) {
  const { t } = useI18n()
  return (
    <nav className="breadcrumbs" aria-label="Breadcrumb">
      <ol>
        <li>
          <Link to="/">{t('navHome')}</Link>
        </li>
        {items.map((c, i) => (
          <li key={i}>
            <Icon name="chevronRight" size={14} />
            {c.to && i < items.length - 1 ? <Link to={c.to}>{c.label}</Link> : <span aria-current={i === items.length - 1 ? 'page' : undefined}>{c.label}</span>}
          </li>
        ))}
      </ol>
    </nav>
  )
}

export function PageHeader({
  crumbs,
  eyebrow,
  title,
  subtitle,
  lead,
  children,
  accent,
}: {
  crumbs?: Crumb[]
  eyebrow?: ReactNode
  title: ReactNode
  subtitle?: ReactNode
  lead?: ReactNode
  children?: ReactNode
  accent?: string
}) {
  return (
    <section className="page-header" style={accent ? { ['--accent-line' as string]: accent } : undefined}>
      <div className="container">
        {crumbs && <Breadcrumbs items={crumbs} />}
        {eyebrow && <div className="page-eyebrow">{eyebrow}</div>}
        <h1 className="page-title">{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
        {lead && <p className="page-lead">{lead}</p>}
        {children}
      </div>
    </section>
  )
}

export function Loading({ label, block }: { label?: string; block?: boolean }) {
  const { t } = useI18n()
  return (
    <div className={`loading${block ? ' loading-block' : ''}`} role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label ?? t('loading')}</span>
    </div>
  )
}

export function ErrorState({ onRetry, message }: { onRetry?: () => void; message?: string }) {
  const { t } = useI18n()
  return (
    <div className="error-state" role="alert">
      <Icon name="info" size={22} />
      <p>{message ?? t('loadError')}</p>
      {onRetry && (
        <button type="button" className="btn btn-outline btn-sm" onClick={onRetry}>
          {t('retry')}
        </button>
      )}
    </div>
  )
}

export function SectionHead({ title, lead, action, id }: { title: ReactNode; lead?: ReactNode; action?: ReactNode; id?: string }) {
  return (
    <div className="section-head">
      <div>
        <h2 className="section-title" id={id}>
          {title}
        </h2>
        {lead && <p className="section-lead">{lead}</p>}
      </div>
      {action && <div className="section-action">{action}</div>}
    </div>
  )
}

export function MoreLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="link-arrow">
      {children}
      <Icon name="arrowRight" size={16} />
    </Link>
  )
}

// ---------------------------------------------------------------------------
// Indicators
// ---------------------------------------------------------------------------

export function Sparkline({ series, width = 120, height = 36, color = 'currentColor' }: { series: SeriesPoint[]; width?: number; height?: number; color?: string }) {
  if (series.length < 2) return null
  const vals = series.map((p) => p.value)
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const span = max - min || 1
  const pad = 3
  const pts = vals.map((v, i) => [pad + (i / (vals.length - 1)) * (width - 2 * pad), pad + (1 - (v - min) / span) * (height - 2 * pad)])
  const d = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join('')
  const area = `${d}L${pts.at(-1)![0].toFixed(1)},${height}L${pts[0][0].toFixed(1)},${height}Z`
  const [lx, ly] = pts.at(-1)!
  return (
    <svg className="sparkline" width={width} height={height} viewBox={`0 0 ${width} ${height}`} aria-hidden="true">
      <path d={area} fill={color} opacity={0.08} />
      <path d={d} fill="none" stroke={color} strokeWidth={1.8} strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={lx} cy={ly} r={3} fill={color} />
    </svg>
  )
}

export function ChangeBadge({ indicator }: { indicator: Indicator }) {
  const { t, num, period } = useI18n()
  const v = indicator.change.value
  if (v === null || v === undefined || !indicator.previous) return null
  const dir = v > 0 ? 'up' : v < 0 ? 'down' : 'flat'
  const unit = indicator.change.type === 'pp' ? ` ${t('pp')}` : '%'
  return (
    <span className={`change change-${dir}`}>
      <Icon name={dir === 'up' ? 'arrowUp' : dir === 'down' ? 'arrowDown' : 'minus'} size={14} strokeWidth={2.2} />
      <span>
        {v > 0 ? '+' : ''}
        {num(v, { maximumFractionDigits: 1 })}
        {unit}
      </span>
      <span className="change-vs">{t('vsPeriod', { period: period(indicator.previous.period) })}</span>
    </span>
  )
}

export function IndicatorCard({ indicator, theme, compact }: { indicator: Indicator; theme?: Theme; compact?: boolean }) {
  const { l, fmt, period, freq } = useI18n()
  const unit = l(indicator.unit)
  return (
    <Link to={`/datasets/${indicator.datasetId}`} className={`indicator-card${compact ? ' compact' : ''}`} style={{ ['--theme' as string]: theme?.color ?? 'var(--primary)' }}>
      <span className="indicator-top">
        <span className="indicator-title">{l(indicator.title)}</span>
        <span className="indicator-freq">{freq(indicator.frequency)}</span>
      </span>
      <span className="indicator-value">
        {fmt(indicator.latest.value, indicator.format)}
        {unit !== '%' && <small>{unit}</small>}
      </span>
      <span className="indicator-period">{period(indicator.latest.period)}</span>
      <span className="indicator-bottom">
        <ChangeBadge indicator={indicator} />
        <Sparkline series={indicator.series.slice(indicator.frequency === 'M' ? -24 : -12)} color={theme?.color ?? '#0B3D91'} />
      </span>
    </Link>
  )
}

// ---------------------------------------------------------------------------
// Catalog
// ---------------------------------------------------------------------------

export function ThemeCard({ theme, agencies }: { theme: Theme; agencies?: number }) {
  const { t, l } = useI18n()
  return (
    <Link to={`/themes/${theme.id}`} className="theme-card" style={{ ['--theme' as string]: theme.color }}>
      <span className="theme-card-icon">
        <Icon name={theme.icon} size={30} strokeWidth={1.6} />
      </span>
      <span className="theme-card-body">
        <strong>{l(theme.name)}</strong>
        <span className="theme-card-desc">{l(theme.description)}</span>
        <span className="theme-card-meta">
          {t('datasetsCount', { n: theme.datasetCount })}
          {agencies !== undefined && <> · {t('agenciesCount', { n: agencies })}</>}
        </span>
      </span>
      <Icon name="arrowRight" size={18} className="theme-card-arrow" />
    </Link>
  )
}

export function FreqTag({ dataset }: { dataset: Dataset }) {
  const { freq } = useI18n()
  return <span className={`tag tag-freq-${dataset.frequency ?? 'x'}`}>{freq(dataset.frequency)}</span>
}

export function DatasetRow({ dataset, category, theme, showAgency = true }: { dataset: Dataset; category?: Category; theme?: Theme; showAgency?: boolean }) {
  const { t, l, lOther, date } = useI18n()
  return (
    <li className="dataset-row" style={{ ['--theme' as string]: theme?.color ?? 'var(--primary)' }}>
      <Link to={`/datasets/${dataset.id}`} className="dataset-row-link">
        <span className="dataset-row-main">
          <span className="dataset-row-title">{l(dataset.title)}</span>
          <span className="dataset-row-alt">{lOther(dataset.title)}</span>
          <span className="dataset-row-meta">
            <span className="code-chip">{dataset.code}</span>
            <FreqTag dataset={dataset} />
            {showAgency && category && <span className="meta-item"><Icon name="building" size={14} />{category.abbr ?? l(category.name)}</span>}
            {dataset.coverage && (
              <span className="meta-item">
                <Icon name="calendar" size={14} />
                {dataset.coverage.from === dataset.coverage.to ? dataset.coverage.from : `${dataset.coverage.from}–${dataset.coverage.to}`}
              </span>
            )}
            <span className="meta-item">
              <Icon name="clock" size={14} />
              {t('updated')} {date(dataset.lastUpdated, 'short')}
            </span>
          </span>
        </span>
        <Icon name="chevronRight" size={20} className="dataset-row-chevron" />
      </Link>
    </li>
  )
}
