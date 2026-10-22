import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import type { Province, ProvinceIndicator } from '../data/types'
import { ProvinceMap, MAP_SCALE, classOf, quantileBreaks } from './charts/ProvinceMap'
import { Icon } from './Icon'

interface Props {
  provinces: Province[]
  indicators: ProvinceIndicator[]
  /** Show the full ranking table instead of the top/bottom summary */
  full?: boolean
}

export function ProvincePanel({ provinces, indicators, full }: Props) {
  const { t, l, num } = useI18n()
  const [indId, setIndId] = useState(indicators[0]?.id)
  const ind = indicators.find((i) => i.id === indId) ?? indicators[0]
  const [year, setYear] = useState<number | null>(null)
  const [selected, setSelected] = useState<string | null>(null)
  const yr = year && ind?.years.includes(year) ? year : ind?.years.at(-1)

  const values = useMemo(() => (ind && yr ? ind.values[String(yr)] ?? {} : {}), [ind, yr])
  const ranking = useMemo(
    () =>
      provinces
        .filter((p) => values[p.id] !== undefined)
        .map((p) => ({ p, v: values[p.id] }))
        .sort((a, b) => b.v - a.v),
    [provinces, values],
  )
  const total = ranking.reduce((s, r) => s + r.v, 0)
  const breaks = useMemo(() => quantileBreaks(Object.values(values)), [values])
  if (!ind || !yr) return null
  const unit = l(ind.unit)
  const isPct = unit === '%'
  const max = ranking[0]?.v || 1
  const sel = selected ? ranking.find((r) => r.p.id === selected) : null
  const list = full ? ranking : [...ranking.slice(0, 5)]

  return (
    <div className="province-panel">
      <div className="province-controls">
        <label className="field">
          <span className="field-label">{t('indicator')}</span>
          <select value={ind.id} onChange={(e) => { setIndId(e.target.value); setSelected(null) }}>
            {indicators.map((i) => (
              <option key={i.id} value={i.id}>
                {l(i.title)}
              </option>
            ))}
          </select>
        </label>
        <div className="field">
          <span className="field-label">{t('year')}</span>
          <div className="segmented" role="radiogroup" aria-label={t('year')}>
            {ind.years.map((y) => (
              <button key={y} type="button" role="radio" aria-checked={y === yr} className={y === yr ? 'on' : ''} onClick={() => setYear(y)}>
                {y}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="province-body">
        <div className="province-map-wrap">
          <ProvinceMap provinces={provinces} values={values} unit={unit} selected={selected} onSelect={setSelected} />
        </div>
        <div className="province-side">
          <div className="province-headline">
            {sel ? (
              <>
                <span className="province-headline-label">{l(sel.p.name)}</span>
                <span className="province-headline-value">
                  {num(sel.v, { maximumFractionDigits: 1 })} <small>{unit}</small>
                </span>
                <span className="province-headline-sub">
                  #{ranking.indexOf(sel) + 1} / {ranking.length}
                  {!isPct && total > 0 && <> · {num((sel.v / total) * 100, { maximumFractionDigits: 1 })}% {t('share').toLowerCase()}</>}
                </span>
              </>
            ) : (
              <>
                <span className="province-headline-label">{isPct ? l(ind.title) : `${t('total')} · ${yr}`}</span>
                <span className="province-headline-value">
                  {isPct ? '' : num(total, { maximumFractionDigits: 0 })} <small>{isPct ? '' : unit}</small>
                </span>
                <span className="province-headline-sub">{l(ind.title)}</span>
              </>
            )}
          </div>
          <h3 className="province-list-title">{full ? t('rank') : t('highest')}</h3>
          <ol className="rank-list">
            {list.map((r, i) => (
              <li key={r.p.id} className={selected === r.p.id ? 'on' : ''}>
                <button type="button" onClick={() => setSelected(selected === r.p.id ? null : r.p.id)}>
                  <span className="rank-no">{ranking.indexOf(r) + 1 || i + 1}</span>
                  <span className="rank-name">{l(r.p.name)}</span>
                  <span className="rank-bar" aria-hidden="true">
                    <i style={{ width: `${Math.max(2, (r.v / max) * 100)}%`, background: MAP_SCALE[classOf(r.v, breaks)] }} />
                  </span>
                  <span className="rank-value">{num(r.v, { maximumFractionDigits: 1 })}</span>
                </button>
              </li>
            ))}
          </ol>
          <Link to={`/datasets/${ind.datasetId}`} className="link-arrow">
            {t('viewData')} <Icon name="arrowRight" size={16} />
          </Link>
        </div>
      </div>
    </div>
  )
}
