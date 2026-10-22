import { useMemo, useState } from 'react'
import { useI18n } from '../../i18n/I18n'
import type { Cell } from '../../data/types'
import { formatCell, type PreparedTable } from '../../data/table'
import { Icon } from '../Icon'

const PAGE_SIZES = [25, 50, 100, 0] // 0 = all

const NO_ROWS: Cell[][] = []

export function useFilteredRows(all: Cell[][] | undefined, query: string): Cell[][] {
  return useMemo(() => {
    const rows = all ?? NO_ROWS
    const q = query.trim().toLowerCase()
    if (!q) return rows
    return rows.filter((r) => r.some((v) => v !== null && String(v).toLowerCase().includes(q)))
  }, [all, query])
}

export function StatTableView({ table, rows, query, onQuery }: { table: PreparedTable; rows: Cell[][]; query: string; onQuery: (q: string) => void }) {
  const { t, num } = useI18n()
  const [size, setSize] = useState(50)
  const [page, setPage] = useState(0)
  const pageSize = size || rows.length || 1
  const pages = Math.max(1, Math.ceil(rows.length / pageSize))
  const current = Math.min(page, pages - 1)
  const slice = rows.slice(current * pageSize, current * pageSize + pageSize)

  const headerRows = useMemo(() => {
    const byRow: (typeof table.cells)[] = table.header.map(() => [])
    for (const c of table.cells) byRow[c.row].push(c)
    return byRow.map((r) => r.sort((a, b) => a.col - b.col))
  }, [table])

  // The row-label column stays visible while scrolling sideways
  const sticky = table.labelColumn
  const from = rows.length ? current * pageSize + 1 : 0
  const to = Math.min(rows.length, (current + 1) * pageSize)

  return (
    <div className="stat-table">
      <div className="table-toolbar">
        <label className="table-filter">
          <Icon name="filter" size={16} />
          <input
            type="search"
            value={query}
            placeholder={t('filterRows')}
            aria-label={t('filterRows')}
            onChange={(e) => {
              onQuery(e.target.value)
              setPage(0)
            }}
          />
        </label>
        <span className="table-count" aria-live="polite">
          {query.trim()
            ? t('rowsFiltered', { n: num(rows.length), total: num(table.rows.length) })
            : t('rowsShown', { from: num(from), to: num(to), n: num(rows.length) })}
        </span>
        <label className="table-size">
          <span>{t('perPage')}</span>
          <select
            value={size}
            onChange={(e) => {
              setSize(Number(e.target.value))
              setPage(0)
            }}
          >
            {PAGE_SIZES.map((s) => (
              <option key={s} value={s}>
                {s || t('all')}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="table-scroll" tabIndex={0} role="region" aria-label={t('tabTable')}>
        <table>
          <thead>
            {headerRows.map((cells, r) => (
              <tr key={r}>
                {cells.map((c) => (
                  <th
                    key={`${c.row}-${c.col}`}
                    colSpan={c.colSpan > 1 ? c.colSpan : undefined}
                    rowSpan={c.rowSpan > 1 ? c.rowSpan : undefined}
                    scope={c.colSpan > 1 ? 'colgroup' : 'col'}
                    className={`${c.colSpan > 1 ? 'group' : ''}${c.col === sticky && c.colSpan === 1 ? ' sticky-col' : ''}${c.colSpan === 1 && table.kinds[c.col] === 'measure' ? ' num' : ''}`}
                  >
                    {c.label.replace(/\u200B/g, '')}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {slice.map((row, i) => (
              <tr key={current * pageSize + i}>
                {row.map((v, c) => {
                  const kind = table.kinds[c]
                  const cls = `${kind === 'measure' ? 'num' : kind === 'code' ? 'code' : ''}${c === sticky ? ' sticky-col' : ''}${v === null || v === '' ? ' empty' : ''}`
                  return c === sticky ? (
                    <th key={c} scope="row" className={cls}>
                      {formatCell(v, kind)}
                    </th>
                  ) : (
                    <td key={c} className={cls}>
                      {formatCell(v, kind)}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {pages > 1 && (
        <div className="pager">
          <button type="button" className="icon-btn" disabled={current === 0} onClick={() => setPage(current - 1)} aria-label={t('prevPage')}>
            <Icon name="chevronLeft" />
          </button>
          <span>{t('page', { p: current + 1, n: pages })}</span>
          <button type="button" className="icon-btn" disabled={current >= pages - 1} onClick={() => setPage(current + 1)} aria-label={t('nextPage')}>
            <Icon name="chevronRight" />
          </button>
        </div>
      )}
    </div>
  )
}
