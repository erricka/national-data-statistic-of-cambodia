// Turns the NIS server's header levels into a renderable grid of merged cells.
//
// Conventions used by the server's report services:
//   ""        continues the cell to the left (colspan) — or, on a lower level under a
//             single-column cell, means "covered by the cell above" (rowspan)
//   "\u200B"  an intentionally blank cell (often on top of a single column)
//   a lower-level title equal to the one above it is the same cell (rowspan)
// Some tables use a compact form instead: the top level lists every title once and the
// second level lists only the sub-columns of the grouped (right-most) titles.

export interface HeaderCell {
  label: string
  row: number
  col: number
  rowSpan: number
  colSpan: number
}

const isBlank = (s: string) => s.replace(/[\s\u200B-\u200D\uFEFF]/g, '') === ''

export function normalizeHeader(levels: string[][], width: number): string[][] {
  let h = levels
    .map((row) => row.map((v) => (v === null || v === undefined ? '' : String(v))))
    .filter((row) => row.some((v) => !isBlank(v)))
  if (!h.length) return [Array.from({ length: width }, (_, i) => `Column ${i + 1}`)]

  // Compact two-level form → expanded form with explicit spans.
  if (h.length === 2) {
    const [top, sub] = h
    const subCount = sub.length
    const leaves = width - subCount
    const groups = top.length - leaves
    if (!top.includes('') && top.length < width && subCount < width && leaves >= 0 && groups > 0 && subCount % groups === 0) {
      const span = subCount / groups
      const newTop: string[] = []
      top.forEach((t, i) => {
        newTop.push(t)
        if (i >= leaves) for (let k = 1; k < span; k++) newTop.push('')
      })
      h = [newTop, [...Array(leaves).fill(''), ...sub]]
    }
  }

  return h.map((row, l) =>
    Array.from({ length: width }, (_, c) => {
      if (c < row.length) return row[c]
      return l === 0 ? '\u200B' : ''
    }),
  )
}

export function buildHeaderGrid(levels: string[][]): HeaderCell[] {
  const L = levels.length
  const W = levels[0]?.length ?? 0
  const cells: HeaderCell[] = []
  const owner: (HeaderCell | null)[][] = levels.map(() => Array(W).fill(null))

  for (let l = 0; l < L; l++) {
    for (let c = 0; c < W; c++) {
      const t = levels[l][c] ?? ''
      const above = l > 0 ? owner[l - 1][c] : null
      const left = c > 0 ? owner[l][c - 1] : null

      if (t === '') {
        if (l === 0) {
          if (left) {
            left.colSpan++
            owner[l][c] = left
            continue
          }
        } else if (above) {
          const leftAbove = c > 0 ? owner[l - 1][c - 1] : null
          if (left && left !== leftAbove && leftAbove === above && left.row === l) {
            left.colSpan++ // continuation inside the same parent group
            owner[l][c] = left
            continue
          }
          if (above.colSpan === 1) {
            above.rowSpan++ // covered from above
            owner[l][c] = above
            continue
          }
        }
        const blank: HeaderCell = { label: '', row: l, col: c, rowSpan: 1, colSpan: 1 }
        cells.push(blank)
        owner[l][c] = blank
        continue
      }

      if (above && above.colSpan === 1 && above.row + above.rowSpan === l && (isBlank(above.label) || above.label === t)) {
        // blank or repeated cell on top of a single column → one tall cell
        above.label = t
        above.rowSpan++
        owner[l][c] = above
        continue
      }

      const cell: HeaderCell = { label: t, row: l, col: c, rowSpan: 1, colSpan: 1 }
      cells.push(cell)
      owner[l][c] = cell
    }
  }
  return cells
}

/** One readable label per column, e.g. "Year 2021 · Domestic visitors". */
export function leafLabels(levels: string[][], cells: HeaderCell[]): string[] {
  const W = levels[0]?.length ?? 0
  return Array.from({ length: W }, (_, c) => {
    const parts = cells
      .filter((cell) => c >= cell.col && c < cell.col + cell.colSpan)
      .sort((a, b) => a.row - b.row)
      .map((cell) => cell.label.trim())
      .filter((s, i, arr) => s && !isBlank(s) && arr.indexOf(s) === i)
    return parts.join(' · ') || `#${c + 1}`
  })
}
