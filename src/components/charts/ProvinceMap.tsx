import { useLayoutEffect, useMemo, useRef, useState } from 'react'
import cambodiaMap from '@svg-maps/cambodia'
import { useI18n } from '../../i18n/I18n'
import type { Province } from '../../data/types'

interface MapShape {
  viewBox: string
  locations: { id: string; name: string; path: string }[]
}
const MAP = cambodiaMap as unknown as MapShape

// Sequential blues (light → dark), 5 classes
export const MAP_SCALE = ['#DCE7F7', '#A9C4EC', '#6E9BDB', '#2F66BF', '#0B3D91']

export function quantileBreaks(values: number[], classes = MAP_SCALE.length): number[] {
  const v = [...values].sort((a, b) => a - b)
  if (!v.length) return []
  return Array.from({ length: classes - 1 }, (_, i) => v[Math.min(v.length - 1, Math.floor(((i + 1) * v.length) / classes))])
}

export function classOf(value: number, breaks: number[]) {
  let i = 0
  while (i < breaks.length && value >= breaks[i]) i++
  return Math.min(i, MAP_SCALE.length - 1)
}

interface Props {
  provinces: Province[]
  values: Record<string, number>
  unit: string
  selected?: string | null
  onSelect?: (id: string | null) => void
  height?: number
}

export function ProvinceMap({ provinces, values, unit, selected, onSelect, height }: Props) {
  const { l, t, num } = useI18n()
  const wrap = useRef<HTMLDivElement>(null)
  const svgRef = useRef<SVGSVGElement>(null)
  const [viewBox, setViewBox] = useState(MAP.viewBox)

  // The packaged viewBox has empty margins; fit it to the drawn provinces.
  useLayoutEffect(() => {
    const g = svgRef.current?.querySelector('g')
    if (!g) return
    const b = g.getBBox()
    if (b.width && b.height) setViewBox(`${b.x - 4} ${b.y - 4} ${b.width + 8} ${b.height + 8}`)
  }, [])
  const [hover, setHover] = useState<{ id: string; x: number; y: number } | null>(null)
  const byId = useMemo(() => new Map(provinces.map((p) => [p.id, p])), [provinces])
  const breaks = useMemo(() => quantileBreaks(Object.values(values).filter((v) => Number.isFinite(v))), [values])

  const fmtV = (v: number) => `${num(v, { maximumFractionDigits: 1 })}${unit === '%' ? '%' : ` ${unit}`}`
  const active = hover?.id ?? selected ?? null
  const info = active ? byId.get(active) : null

  return (
    <div className="province-map" ref={wrap} style={height ? { height } : undefined}>
      <svg ref={svgRef} viewBox={viewBox} role="group" aria-label={t('provincesAtAGlance')}>
        <g>
        {MAP.locations.map((loc) => {
          const v = values[loc.id]
          const has = v !== undefined && Number.isFinite(v)
          const fill = has ? MAP_SCALE[classOf(v, breaks)] : '#EEF1F5'
          const p = byId.get(loc.id)
          const label = `${p ? l(p.name) : loc.name}: ${has ? fmtV(v) : t('noData')}`
          return (
            <path
              key={loc.id}
              d={loc.path}
              fill={fill}
              className={`province${active === loc.id ? ' is-active' : ''}${has ? '' : ' no-data'}`}
              tabIndex={0}
              role="button"
              aria-label={label}
              aria-pressed={selected === loc.id}
              onMouseMove={(e) => {
                const r = wrap.current?.getBoundingClientRect()
                if (r) setHover({ id: loc.id, x: e.clientX - r.left, y: e.clientY - r.top })
              }}
              onMouseLeave={() => setHover(null)}
              onFocus={() => onSelect?.(loc.id)}
              onClick={() => onSelect?.(selected === loc.id ? null : loc.id)}
            />
          )
        })}
        </g>
      </svg>
      {hover && info && (
        <div className="map-tooltip" style={{ left: hover.x, top: hover.y }}>
          <strong>{l(info.name)}</strong>
          <span>{values[info.id] !== undefined ? fmtV(values[info.id]) : t('noData')}</span>
        </div>
      )}
      <div className="map-legend" aria-hidden="true">
        {MAP_SCALE.map((c, i) => (
          <span key={c} className="map-legend-item">
            <i style={{ background: c }} />
            {i === 0 ? `< ${num(breaks[0] ?? 0, { notation: 'compact' })}` : i === MAP_SCALE.length - 1 ? `≥ ${num(breaks[i - 1] ?? 0, { notation: 'compact' })}` : num(breaks[i - 1] ?? 0, { notation: 'compact' })}
          </span>
        ))}
        <span className="map-legend-item">
          <i style={{ background: '#EEF1F5' }} />
          {t('noData')}
        </span>
      </div>
    </div>
  )
}
