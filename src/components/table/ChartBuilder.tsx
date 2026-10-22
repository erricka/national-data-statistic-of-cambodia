import { useEffect, useMemo, useRef, useState } from 'react'
import { useI18n } from '../../i18n/I18n'
import type { Cell } from '../../data/types'
import { TOTAL_LABEL, toNumber, type PreparedTable, downloadBlob } from '../../data/table'
import { EChart, PALETTE, axisStyle, tooltipStyle, type EChartHandle } from '../charts/EChart'
import { Icon } from '../Icon'

type ChartKind = 'line' | 'bar' | 'stacked' | 'hbar'
type Orientation = 'columns' | 'rows'

const MAX_SERIES = 12
const TIME_LABEL = /(19|20)\d{2}|ខែ|month|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|q[1-4]|ត្រីមាស|ឆមាស/i

interface Item {
  key: number
  label: string
}

export function ChartBuilder({ table, rows: allRows, title }: { table: PreparedTable; rows: Cell[][]; title: string }) {
  const { t, num } = useI18n()
  const chartRef = useRef<EChartHandle>(null)
  const isTotal = (r: Cell[]) => TOTAL_LABEL.test(String(r[table.labelColumn] ?? '').trim())
  const hasTotals = allRows.some(isTotal) && allRows.some((r) => !isTotal(r))
  const [includeTotals, setIncludeTotals] = useState(false)
  const rows = useMemo(() => (hasTotals && !includeTotals ? allRows.filter((r) => !isTotal(r)) : allRows), [allRows, hasTotals, includeTotals])

  // Numeric columns, without empty leading/trailing ones (e.g. placeholder future years)
  const measureCols = useMemo(() => {
    const cols = table.kinds.map((k, c) => (k === 'measure' ? c : -1)).filter((c) => c >= 0)
    const hasData = (c: number) => rows.some((r) => {
      const n = toNumber(r[c])
      return n !== null && n !== 0
    })
    let lo = 0
    let hi = cols.length - 1
    while (lo <= hi && !hasData(cols[lo])) lo++
    while (hi >= lo && !hasData(cols[hi])) hi--
    return cols.slice(lo, hi + 1).filter((c) => rows.some((r) => toNumber(r[c]) !== null))
  }, [table, rows])
  const timeInColumns = useMemo(() => {
    const timeish = measureCols.filter((c) => TIME_LABEL.test(table.labels[c])).length
    return measureCols.length >= 4 && timeish / measureCols.length >= 0.5
  }, [measureCols, table])

  const [orientation, setOrientation] = useState<Orientation>(timeInColumns ? 'rows' : 'columns')
  const [kind, setKind] = useState<ChartKind>(timeInColumns ? 'line' : 'bar')
  const [filter, setFilter] = useState('')

  const rowLabel = (r: Cell[], i: number) => {
    const main = r[table.labelColumn]
    const label = main === null || main === undefined || main === '' ? `#${i + 1}` : String(main)
    const extra = table.detailColumn >= 0 ? r[table.detailColumn] : null
    return extra === null || extra === undefined || extra === '' ? label : `${label} · ${extra}`
  }

  const items: Item[] = useMemo(() => {
    if (orientation === 'columns') return measureCols.map((c) => ({ key: c, label: table.labels[c] }))
    return rows.map((r, i) => ({ key: i, label: rowLabel(r, i) })).filter((it) => measureCols.some((c) => toNumber(rows[it.key][c]) !== null))
  }, [orientation, measureCols, rows, table])

  const [selected, setSelected] = useState<number[]>([])
  useEffect(() => {
    setSelected(items.slice(0, Math.min(orientation === 'columns' ? 4 : 5, items.length)).map((i) => i.key))
  }, [items, orientation])

  const option = useMemo(() => {
    const cats = orientation === 'columns' ? rows.map((r, i) => rowLabel(r, i)) : measureCols.map((c) => table.labels[c])
    const series = selected
      .filter((k) => items.some((i) => i.key === k))
      .map((k) => {
        const label = items.find((i) => i.key === k)!.label
        const data = orientation === 'columns' ? rows.map((r) => toNumber(r[k])) : measureCols.map((c) => toNumber(rows[k][c]))
        return {
          name: label,
          type: kind === 'line' ? 'line' : 'bar',
          stack: kind === 'stacked' ? 'total' : undefined,
          data,
          smooth: kind === 'line' ? 0.2 : undefined,
          showSymbol: kind === 'line' && cats.length <= 40,
          symbolSize: 5,
          connectNulls: kind === 'line',
          barMaxWidth: 26,
          emphasis: { focus: 'series' },
        }
      })
    const horizontal = kind === 'hbar'
    const zoom = cats.length > 30
    const catAxis = {
      type: 'category',
      data: cats,
      ...axisStyle,
      axisLabel: { ...axisStyle.axisLabel, hideOverlap: true, width: horizontal ? 160 : 110, overflow: 'truncate', rotate: !horizontal && cats.length > 8 && !zoom ? 30 : 0 },
    }
    const valAxis = {
      type: 'value',
      ...axisStyle,
      axisLabel: { ...axisStyle.axisLabel, formatter: (v: number) => (Math.abs(v) >= 1e6 ? `${num(v / 1e6)}M` : Math.abs(v) >= 1e4 ? `${num(v / 1e3)}K` : num(v)) },
    }
    return {
      legend: { type: 'scroll', top: 0, left: 0, right: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 8, textStyle: { fontSize: 12 } },
      grid: { left: 8, right: 24, top: 44, bottom: zoom ? 64 : 16, containLabel: true },
      tooltip: { trigger: 'axis', axisPointer: { type: kind === 'line' ? 'line' : 'shadow' }, ...tooltipStyle, valueFormatter: (v: number | null) => (v === null || v === undefined ? '–' : num(v, { maximumFractionDigits: 2 })) },
      xAxis: horizontal ? valAxis : catAxis,
      yAxis: horizontal ? { ...catAxis, inverse: true } : valAxis,
      dataZoom: zoom
        ? horizontal
          ? [{ type: 'slider', yAxisIndex: 0, right: 0, width: 18, startValue: 0, endValue: 24 }, { type: 'inside', yAxisIndex: 0 }]
          : [{ type: 'slider', height: 22, bottom: 10, startValue: Math.max(0, cats.length - 30), endValue: cats.length - 1 }, { type: 'inside' }]
        : [],
      series,
    }
  }, [orientation, kind, selected, items, rows, measureCols, table, num])

  if (!measureCols.length || !rows.length) {
    return <p className="empty-note">{t('chartNoNumeric')}</p>
  }

  const visibleItems = filter.trim() ? items.filter((i) => i.label.toLowerCase().includes(filter.trim().toLowerCase())) : items
  const toggle = (k: number) =>
    setSelected((s) => (s.includes(k) ? s.filter((x) => x !== k) : s.length >= MAX_SERIES ? s : [...s, k]))
  const height = kind === 'hbar' ? Math.min(900, Math.max(360, Math.min(rows.length, 25) * 28 + 80)) : 440

  return (
    <div className="chart-builder">
      <aside className="chart-controls">
        <div className="field">
          <span className="field-label">{t('chartType')}</span>
          <div className="segmented segmented-icons">
            {(
              [
                ['line', 'chart', t('chartLine')],
                ['bar', 'bars', t('chartBar')],
                ['stacked', 'layers', t('chartStacked')],
                ['hbar', 'menu', t('chartHBar')],
              ] as const
            ).map(([k, icon, label]) => (
              <button key={k} type="button" className={kind === k ? 'on' : ''} aria-pressed={kind === k} onClick={() => setKind(k)} title={label}>
                <Icon name={icon} size={16} />
                <span>{label}</span>
              </button>
            ))}
          </div>
        </div>
        <div className="field">
          <span className="field-label">{t('chartSeriesFrom')}</span>
          <div className="segmented">
            <button type="button" className={orientation === 'columns' ? 'on' : ''} aria-pressed={orientation === 'columns'} onClick={() => setOrientation('columns')}>
              {t('chartColumns')}
            </button>
            <button type="button" className={orientation === 'rows' ? 'on' : ''} aria-pressed={orientation === 'rows'} onClick={() => setOrientation('rows')}>
              {t('chartRows')}
            </button>
          </div>
        </div>
        {hasTotals && (
          <label className="check">
            <input type="checkbox" checked={includeTotals} onChange={(e) => setIncludeTotals(e.target.checked)} />
            {t('chartIncludeTotals')}
          </label>
        )}
        <div className="field series-field">
          <span className="field-label">
            {t('chartSelect')} <small>({selected.length}/{MAX_SERIES})</small>
          </span>
          <input type="search" className="input-sm" placeholder={t('filterSeries')} value={filter} onChange={(e) => setFilter(e.target.value)} />
          <div className="series-actions">
            <button type="button" className="btn-link" onClick={() => setSelected(items.slice(0, MAX_SERIES).map((i) => i.key))}>
              {t('chartSelectAll', { n: Math.min(MAX_SERIES, items.length) })}
            </button>
            <button type="button" className="btn-link" onClick={() => setSelected([])}>
              {t('chartClear')}
            </button>
          </div>
          <ul className="series-list">
            {visibleItems.slice(0, 300).map((it) => {
              const on = selected.includes(it.key)
              const color = on ? PALETTE[selected.indexOf(it.key) % PALETTE.length] : undefined
              return (
                <li key={it.key}>
                  <label className={on ? 'on' : ''}>
                    <input type="checkbox" checked={on} onChange={() => toggle(it.key)} disabled={!on && selected.length >= MAX_SERIES} />
                    <span className="swatch" style={{ background: color }} />
                    <span className="series-label">{it.label}</span>
                  </label>
                </li>
              )
            })}
          </ul>
        </div>
      </aside>
      <div className="chart-stage">
        {selected.length ? (
          <>
            <EChart ref={chartRef} option={option} height={height} ariaLabel={title} />
            <div className="chart-foot">
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                onClick={() => {
                  const url = chartRef.current?.toPng()
                  if (!url) return
                  fetch(url)
                    .then((r) => r.blob())
                    .then((b) => downloadBlob(b, `${table.id}-chart.png`, 'image/png'))
                }}
              >
                <Icon name="image" size={16} /> {t('chartSaveImage')}
              </button>
            </div>
          </>
        ) : (
          <p className="empty-note">{t('chartPickOne')}</p>
        )}
      </div>
    </div>
  )
}
