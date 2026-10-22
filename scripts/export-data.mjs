#!/usr/bin/env node
/**
 * Builds the static data snapshot the portal reads from `public/data/`.
 *
 * Put the database address in a `.env` file (copy `.env.example`; `.env` is never
 * committed), then run `npm run export:data`:
 *
 *   DATABASE_URL=postgres://user:pass@host:5432/nis
 *   NIS_API_URL=https://host/api/nis-portal      (optional)
 *
 * Variables set in the shell take precedence over `.env`.
 *
 * Options:
 *   --tables=api|db|skip  where statistical tables come from (default: api when NIS_API_URL is set, else db)
 *                         api  – the official pivoted tables served by the NIS portal server (DTSI003)
 *                         db   – raw rows straight from PostgreSQL (fallback; column names as headers)
 *                         skip – keep the table files that are already on disk
 *   --out=<dir>           output directory (default: public/data)
 *
 * Only data the NIS portal already publishes is exported: datasets with status 'Y' and
 * rows that belong to an APPROVED upload (datasource_history.status = '01').
 * No users, roles, audit logs or credentials are read.
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';
import { APPROVED, INDICATORS, PROVINCE_INDICATORS } from './indicators.config.mjs';
import { PROVINCES, matchProvince } from './lib/provinces.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
try {
  process.loadEnvFile(path.join(root, '.env'));
} catch {
  // no .env file: use the shell environment only
}
const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const [k, v] = a.replace(/^--/, '').split('=');
    return [k, v ?? true];
  }),
);
const API = (process.env.NIS_API_URL || '').replace(/\/$/, '');
const TABLE_SOURCE = args.tables || (API ? 'api' : 'db');
const OUT = path.resolve(root, args.out || 'public/data');

if (!process.env.DATABASE_URL) {
  console.error('DATABASE_URL is required. Set it in .env (see .env.example), e.g. postgres://user:pass@localhost:5432/nis');
  process.exit(1);
}

const readJson = async (p) => JSON.parse(await fs.readFile(path.join(here, p), 'utf8'));
const { themes } = await readJson('meta/themes.json');
const categoryMeta = await readJson('meta/categories.json');
const datasetTitlesEn = await readJson('meta/datasets.en.json');

const db = new pg.Client({ connectionString: process.env.DATABASE_URL });
await db.connect();
const q = async (sql, params) => (await db.query(sql.replaceAll('{approved}', APPROVED), params)).rows;

const log = (...m) => console.log('·', ...m);
const ymd = (d) => (d ? new Date(d).toISOString().slice(0, 10) : null);
const quoteIdent = (s) => '"' + String(s).replace(/"/g, '""') + '"';

// ---------------------------------------------------------------------------
// Catalog
// ---------------------------------------------------------------------------
const themeOfCategory = new Map();
for (const t of themes) for (const c of t.categoryIds) themeOfCategory.set(c, t.id);

const categoriesRaw = await q(`SELECT category_id, category_name, description FROM category WHERE status = '01' ORDER BY category_id`);
const datasourcesRaw = await q(`
  SELECT source_id, source_name, category_id, table_name, sort_order
  FROM datasource
  WHERE status = 'Y' AND table_name IS NOT NULL AND table_name <> ''
  ORDER BY category_id, sort_order, source_id`);
const releasesRaw = await q(`
  SELECT source_id, upload_id, upload_file_name, archived_at, last_change_date, last_change_time
  FROM datasource_history WHERE status = '01' ORDER BY archived_at`);
const tableColumns = await q(`
  SELECT table_name, column_name, data_type FROM information_schema.columns
  WHERE table_schema = 'public' ORDER BY table_name, ordinal_position`);

const columnsByTable = new Map();
for (const c of tableColumns) {
  if (!columnsByTable.has(c.table_name)) columnsByTable.set(c.table_name, []);
  columnsByTable.get(c.table_name).push(c);
}

const releasesBySource = new Map();
for (const r of releasesRaw) {
  const approved = r.last_change_date && /^\d{8}$/.test(r.last_change_date)
    ? `${r.last_change_date.slice(0, 4)}-${r.last_change_date.slice(4, 6)}-${r.last_change_date.slice(6, 8)}`
    : ymd(r.archived_at);
  const rel = { uploadId: r.upload_id, date: approved, submitted: ymd(r.archived_at), label: r.upload_file_name || null };
  if (!releasesBySource.has(r.source_id)) releasesBySource.set(r.source_id, []);
  releasesBySource.get(r.source_id).push(rel);
}

const stripCode = (s) => String(s || '').replace(/^\s*[0-9A-Za-z_]+\s*-\s*/, '').replace(/\s+/g, ' ').trim();
const KM_FREQ = [
  [/ប្រចាំខែ|តាមខែ/, 'M'],
  [/ត្រីមាស/, 'Q'],
  [/ឆមាស/, 'S'],
  [/ប្រចាំឆ្នាំ|ឆ្នាំ/, 'A'],
];

function inferFrequency(titleKm, cols) {
  const names = new Set(cols.map((c) => c.column_name));
  if (names.has('month') || [...names].some((n) => /^(jan|january)(_|$)/.test(n))) return 'M';
  if ([...names].some((n) => /^quarter|_q[1-4]$/.test(n))) return 'Q';
  for (const [re, f] of KM_FREQ) if (re.test(titleKm)) return f;
  if (names.has('year') || names.has('year_period')) return 'A';
  return null;
}

const datasets = [];
for (const d of datasourcesRaw) {
  const releases = releasesBySource.get(d.source_id) || [];
  if (!releases.length) continue; // never approved -> not public
  const cols = columnsByTable.get(d.table_name);
  if (!cols) continue;
  const hasUpload = cols.some((c) => c.column_name === 'upload_id');
  const where = hasUpload ? `WHERE ${APPROVED}` : '';
  const [{ n }] = await q(`SELECT count(*)::int AS n FROM ${quoteIdent(d.table_name)} ${where}`);

  let coverage = null;
  const yearCol = cols.find((c) => c.column_name === 'year' || c.column_name === 'year_period');
  if (yearCol && n > 0) {
    const [r] = await q(`
      SELECT min(y)::int AS lo, max(y)::int AS hi FROM (
        SELECT substring(${quoteIdent(yearCol.column_name)}::text from '(19[5-9][0-9]|20[0-9][0-9])')::int AS y
        FROM ${quoteIdent(d.table_name)} ${where}) t
      WHERE y IS NOT NULL AND y <= extract(year from now())::int`);
    if (r.lo) coverage = { from: r.lo, to: r.hi };
  }

  const titleKm = stripCode(d.source_name);
  datasets.push({
    id: d.source_id,
    code: d.source_id.replace(/^nis_/, '').toUpperCase(),
    title: { en: datasetTitlesEn[d.source_id] || titleKm, km: titleKm },
    categoryId: Number(d.category_id),
    themeId: themeOfCategory.get(Number(d.category_id)) || null,
    sortOrder: d.sort_order ?? 0,
    records: n,
    frequency: inferFrequency(titleKm, cols),
    coverage,
    releases: releases.map((r) => ({ ...r })).reverse(), // newest first
    lastUpdated: releases.map((r) => r.date).sort().at(-1),
  });
}
log(`datasets: ${datasets.length} public (of ${datasourcesRaw.length} active)`);

const categories = categoriesRaw.map((c) => {
  const id = Number(c.category_id);
  const own = datasets.filter((d) => d.categoryId === id);
  return {
    id,
    code: String(id).padStart(2, '0'),
    name: { en: categoryMeta[id]?.en || c.category_name, km: c.category_name },
    abbr: categoryMeta[id]?.abbr || null,
    themeId: themeOfCategory.get(id) || null,
    datasetCount: own.length,
    lastUpdated: own.map((d) => d.lastUpdated).sort().at(-1) || null,
  };
});

// ---------------------------------------------------------------------------
// Statistical tables
// ---------------------------------------------------------------------------
const tablesDir = path.join(OUT, 'tables');
await fs.mkdir(tablesDir, { recursive: true });

async function tableFromApi(sourceId) {
  const res = await fetch(`${API}/DTSI003`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ sourceId, upload_id: '', page: 1, size: 0 }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const body = await res.json();
  if (body.code !== '000000' || !body.data) {
    const msg = typeof body.message === 'object' ? body.message?.messageBody : body.message;
    throw new Error(`API ${body.code}: ${msg}`);
  }
  const header = (body.data.header || []).map((row) => row.map((h) => (h && typeof h === 'object' ? h.title ?? '' : String(h ?? ''))));
  return { header, rows: body.data.list || [], source: 'api' };
}

const HIDDEN_COLS = /^(id|upload_id|created_at|updated_at|first_register_.*|last_change_.*|frst_.*|last_chng_.*)$/;
const humanize = (c) => c.replace(/_/g, ' ').replace(/\b\w/, (m) => m.toUpperCase());

async function tableFromDb(ds) {
  const tableName = datasourcesRaw.find((d) => d.source_id === ds.id).table_name;
  const cols = columnsByTable.get(tableName).filter((c) => !HIDDEN_COLS.test(c.column_name));
  const hasUpload = columnsByTable.get(tableName).some((c) => c.column_name === 'upload_id');
  const hasId = columnsByTable.get(tableName).some((c) => c.column_name === 'id');
  const rows = await q(
    `SELECT ${cols.map((c) => quoteIdent(c.column_name)).join(', ')} FROM ${quoteIdent(tableName)}
     ${hasUpload ? `WHERE ${APPROVED}` : ''} ${hasId ? 'ORDER BY id' : ''}`,
  );
  const numeric = new Set(['integer', 'bigint', 'numeric', 'double precision', 'real', 'smallint']);
  return {
    header: [cols.map((c) => humanize(c.column_name))],
    rows: rows.map((r) => cols.map((c) => {
      const v = r[c.column_name];
      if (v === null || v === undefined) return null;
      if (numeric.has(c.data_type)) return Number(v);
      if (v instanceof Date) return ymd(v);
      return String(v);
    })),
    source: 'db',
  };
}

if (TABLE_SOURCE !== 'skip') {
  log(`tables: exporting ${datasets.length} from ${TABLE_SOURCE === 'api' ? API : 'database'}`);
  let ok = 0, fallback = 0, failed = 0;
  const queue = [...datasets];
  const worker = async () => {
    for (let ds = queue.shift(); ds; ds = queue.shift()) {
      let table = null;
      if (TABLE_SOURCE === 'api') {
        try { table = await tableFromApi(ds.id); } catch (e) { log(`  ${ds.id}: API failed (${e.message}); using raw rows`); }
      }
      if (!table) {
        try { table = await tableFromDb(ds); if (TABLE_SOURCE === 'api') fallback++; } catch (e) { log(`  ${ds.id}: ${e.message}`); failed++; continue; }
      }
      const width = Math.max(table.header.at(-1)?.length || 0, ...table.rows.slice(0, 50).map((r) => r.length));
      ds.table = { rows: table.rows.length, columns: width, source: table.source };
      await fs.writeFile(path.join(tablesDir, `${ds.id}.json`), JSON.stringify({ id: ds.id, header: table.header, rows: table.rows }));
      ok++;
    }
  };
  await Promise.all(Array.from({ length: 4 }, worker));
  log(`tables: ${ok} written (${fallback} from raw rows), ${failed} failed`);
} else {
  for (const ds of datasets) {
    try {
      const t = JSON.parse(await fs.readFile(path.join(tablesDir, `${ds.id}.json`), 'utf8'));
      ds.table = { rows: t.rows.length, columns: Math.max(t.header.at(-1)?.length || 0, t.rows[0]?.length || 0), source: 'cached' };
    } catch { /* table not exported yet */ }
  }
}

// ---------------------------------------------------------------------------
// Headline indicators
// ---------------------------------------------------------------------------
const thisYear = new Date().getFullYear();
const pad2 = (n) => String(n).padStart(2, '0');

const indicators = [];
for (const def of INDICATORS) {
  if (!datasets.some((d) => d.id === def.datasetId)) { log(`indicator ${def.id}: dataset not public, skipped`); continue; }
  let rows;
  try { rows = await q(def.sql); } catch (e) { log(`indicator ${def.id}: ${e.message}`); continue; }
  let series = rows
    .filter((r) => r.value !== null && Number.isFinite(Number(r.value)) && Number(r.year) > 1900)
    .map((r) => ({
      period: def.frequency === 'M' ? `${r.year}-${pad2(r.month)}` : String(r.year),
      value: Number(r.value),
    }));
  if (def.completeYearsOnly) series = series.filter((p) => Number(p.period.slice(0, 4)) < thisYear);
  if (def.transform === 'yoy') {
    const byPeriod = new Map(series.map((p) => [p.period, p.value]));
    series = series
      .map((p) => {
        const [y, m] = p.period.split('-');
        const base = byPeriod.get(`${Number(y) - 1}-${m}`);
        return base ? { period: p.period, value: Math.round((p.value / base - 1) * 1000) / 10 } : null;
      })
      .filter(Boolean);
  }
  if (series.length < 2) { log(`indicator ${def.id}: not enough data`); continue; }

  // Compare only with the immediately preceding period (same month last year for
  // monthly levels) so gaps in the source never produce misleading changes.
  const latest = series.at(-1);
  const prevPeriod = (() => {
    if (def.frequency === 'A') return String(Number(latest.period) - 1);
    const [y, m] = latest.period.split('-').map(Number);
    if (def.transform === 'yoy') return m === 1 ? `${y - 1}-12` : `${y}-${pad2(m - 1)}`;
    return `${y - 1}-${pad2(m)}`;
  })();
  const previous = series.find((p) => p.period === prevPeriod) || null;
  const changeType = def.transform === 'yoy' ? 'pp' : 'pct';
  const change = !previous
    ? null
    : changeType === 'pp'
      ? Math.round((latest.value - previous.value) * 10) / 10
      : previous.value ? Math.round((latest.value / previous.value - 1) * 1000) / 10 : null;

  indicators.push({
    id: def.id,
    datasetId: def.datasetId,
    themeId: def.theme,
    title: def.title,
    unit: def.unit,
    frequency: def.frequency,
    format: def.format,
    latest,
    previous,
    change: { value: change, type: changeType },
    series: series.slice(def.frequency === 'M' ? -120 : -40),
  });
  log(`indicator ${def.id}: ${latest.period} = ${latest.value} (${change === null ? 'no comparable period' : `${change > 0 ? '+' : ''}${change}${changeType === 'pp' ? 'pp' : '%'}`})`);
}

// ---------------------------------------------------------------------------
// Province indicators
// ---------------------------------------------------------------------------
const provinceIndicators = [];
for (const def of PROVINCE_INDICATORS) {
  if (!datasets.some((d) => d.id === def.datasetId)) continue;
  let rows;
  try { rows = await q(def.sql); } catch (e) { log(`province ${def.id}: ${e.message}`); continue; }
  const byYear = new Map();
  const unmatched = new Set();
  for (const r of rows) {
    if (r.value === null || !Number.isFinite(Number(r.value))) continue;
    const pid = matchProvince(r.name, r.code);
    if (!pid) { unmatched.add(r.name); continue; }
    if (!byYear.has(r.year)) byYear.set(r.year, new Map());
    const m = byYear.get(r.year);
    m.set(pid, def.aggregate === 'sum' ? (m.get(pid) || 0) + Number(r.value) : Number(r.value));
  }
  const years = [...byYear.keys()].filter((y) => byYear.get(y).size >= 15 && y < thisYear + 1).sort();
  if (!years.length) { log(`province ${def.id}: not enough coverage`); continue; }
  const byYearOut = Object.fromEntries(years.map((y) => [y, Object.fromEntries(byYear.get(y))]));
  provinceIndicators.push({
    id: def.id,
    datasetId: def.datasetId,
    title: def.title,
    unit: def.unit,
    years,
    values: byYearOut,
  });
  log(`province ${def.id}: years ${years[0]}–${years.at(-1)}, ${byYear.get(years.at(-1)).size} provinces` +
    (unmatched.size ? `; ignored rows: ${[...unmatched].slice(0, 6).join(', ')}` : ''));
}

// ---------------------------------------------------------------------------
// Write
// ---------------------------------------------------------------------------
const releases = datasets
  .flatMap((d) => d.releases.map((r) => ({ date: r.date, datasetId: d.id, uploadId: r.uploadId })))
  .sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));

const catalog = {
  generatedAt: new Date().toISOString(),
  stats: {
    datasets: datasets.length,
    agencies: categories.filter((c) => c.datasetCount > 0).length,
    records: datasets.reduce((s, d) => s + d.records, 0),
    releases: releases.length,
    lastRelease: releases[0]?.date || null,
  },
  themes: themes.map((t) => ({ ...t, datasetCount: datasets.filter((d) => d.themeId === t.id).length })),
  categories,
  datasets,
  releases,
};

// Keep the previous timestamp when nothing else changed, so re-running the export
// on unchanged data leaves the files byte-for-byte identical.
async function stableTimestamp(file, body) {
  try {
    const prev = JSON.parse(await fs.readFile(path.join(OUT, file), 'utf8'));
    const strip = (o) => JSON.stringify({ ...o, generatedAt: null });
    if (strip(prev) === strip(body)) return prev.generatedAt;
  } catch {
    // no previous file
  }
  return body.generatedAt;
}

await fs.mkdir(OUT, { recursive: true });
catalog.generatedAt = await stableTimestamp('catalog.json', catalog);
const indicatorsBody = { generatedAt: catalog.generatedAt, indicators };
indicatorsBody.generatedAt = await stableTimestamp('indicators.json', indicatorsBody);
await fs.writeFile(path.join(OUT, 'catalog.json'), JSON.stringify(catalog));
await fs.writeFile(path.join(OUT, 'indicators.json'), JSON.stringify(indicatorsBody));
await fs.writeFile(path.join(OUT, 'provinces.json'), JSON.stringify({ provinces: PROVINCES.map(({ no, id, en, km }) => ({ no, id, name: { en, km } })), indicators: provinceIndicators }));
await db.end();
log(`done → ${path.relative(root, OUT)}`);
