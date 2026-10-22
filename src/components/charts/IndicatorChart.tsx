import { useMemo } from 'react'
import { useI18n } from '../../i18n/I18n'
import type { Indicator } from '../../data/types'
import { EChart, axisStyle, tooltipStyle } from './EChart'

export function IndicatorChart({ indicator, color = '#0B3D91', height = 320, kind }: { indicator: Indicator; color?: string; height?: number; kind?: 'line' | 'bar' }) {
  const { l, fmt, period, num } = useI18n()
  const type = kind ?? (indicator.frequency === 'A' ? 'bar' : 'line')

  const option = useMemo(() => {
    const data = indicator.series
    return {
      grid: { left: 8, right: 16, top: 24, bottom: indicator.frequency === 'M' && data.length > 36 ? 56 : 24, containLabel: true },
      tooltip: {
        trigger: 'axis',
        ...tooltipStyle,
        valueFormatter: (v: number) => `${fmt(v, indicator.format)}${l(indicator.unit) === '%' ? '' : ' ' + l(indicator.unit)}`,
      },
      xAxis: {
        type: 'category',
        data: data.map((p) => period(p.period)),
        boundaryGap: type === 'bar',
        ...axisStyle,
      },
      yAxis: {
        type: 'value',
        scale: type === 'line',
        ...axisStyle,
        axisLabel: { ...axisStyle.axisLabel, formatter: (v: number) => (Math.abs(v) >= 1e6 ? `${num(v / 1e6)}M` : Math.abs(v) >= 1e4 ? `${num(v / 1e3)}K` : num(v)) },
      },
      dataZoom: indicator.frequency === 'M' && data.length > 36 ? [{ type: 'slider', height: 22, bottom: 8, startValue: data.length - 36, borderColor: 'transparent', fillerColor: 'rgba(11,61,145,.12)' }, { type: 'inside' }] : [],
      series: [
        {
          name: l(indicator.title),
          type,
          data: data.map((p) => p.value),
          smooth: type === 'line' ? 0.25 : undefined,
          showSymbol: false,
          symbolSize: 6,
          barMaxWidth: 28,
          itemStyle: { color, borderRadius: type === 'bar' ? [3, 3, 0, 0] : 0 },
          lineStyle: { width: 2.5, color },
          areaStyle: type === 'line' ? { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: color + '33' }, { offset: 1, color: color + '00' }] } } : undefined,
          markLine:
            indicator.format === 'percent1'
              ? { silent: true, symbol: 'none', lineStyle: { color: '#94A3B8', type: 'dashed' }, label: { show: false }, data: [{ yAxis: 0 }] }
              : undefined,
        },
      ],
    }
  }, [indicator, color, type, l, fmt, period, num])

  return <EChart option={option} height={height} ariaLabel={l(indicator.title)} />
}
