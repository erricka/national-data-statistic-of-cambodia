import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { DataZoomComponent, GridComponent, LegendComponent, MarkLineComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'

echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, MarkLineComponent, CanvasRenderer])

/** Categorical palette: NIS navy, Cambodian red, logo gold/green/orange, then supporting hues. */
export const PALETTE = ['#0B3D91', '#D21F3C', '#E0A63B', '#2E7D32', '#00838F', '#7B1FA2', '#C8551B', '#5C6BC0', '#AD1457', '#558B2F', '#37474F', '#0288D1']

export const FONT = '"Inter Variable", "Noto Sans Khmer", system-ui, sans-serif'

export interface EChartHandle {
  toPng: () => string | undefined
}

export const EChart = forwardRef<EChartHandle, { option: EChartsCoreOption; height?: number; ariaLabel?: string }>(function EChart(
  { option, height = 360, ariaLabel },
  ref,
) {
  const el = useRef<HTMLDivElement>(null)
  const chart = useRef<echarts.ECharts | null>(null)

  useImperativeHandle(ref, () => ({
    toPng: () => chart.current?.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' }),
  }))

  useEffect(() => {
    if (!el.current) return
    const c = echarts.init(el.current, undefined, { renderer: 'canvas' })
    chart.current = c
    const ro = new ResizeObserver(() => c.resize())
    ro.observe(el.current)
    return () => {
      ro.disconnect()
      c.dispose()
      chart.current = null
    }
  }, [])

  useEffect(() => {
    chart.current?.setOption(
      {
        color: PALETTE,
        textStyle: { fontFamily: FONT, color: '#334155' },
        animationDuration: 450,
        ...option,
      },
      true,
    )
  }, [option])

  return <div ref={el} className="echart" style={{ height }} role="img" aria-label={ariaLabel} />
})

/** Shared axis / tooltip styling */
export const axisStyle = {
  axisLine: { lineStyle: { color: '#CBD5E1' } },
  axisTick: { show: false },
  axisLabel: { color: '#64748B', fontSize: 12, fontFamily: FONT },
  splitLine: { lineStyle: { color: '#EEF2F7' } },
}

export const tooltipStyle = {
  backgroundColor: '#0F172A',
  borderWidth: 0,
  textStyle: { color: '#F8FAFC', fontFamily: FONT, fontSize: 12 },
  extraCssText: 'border-radius:8px;box-shadow:0 8px 24px rgba(15,23,42,.25);',
}
