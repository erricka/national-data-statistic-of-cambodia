/**
 * Safety check before publishing from a copy of the NIS system: every upload the
 * public website shows must also exist in this copy's database. If one is missing,
 * someone published from a different copy, and publishing from this one would
 * remove their data from the website.
 */
import fs from 'node:fs'
import pg from 'pg'

const catalog = JSON.parse(fs.readFileSync(new URL('../../public/data/catalog.json', import.meta.url), 'utf8'))
const onWebsite = new Set(catalog.datasets.flatMap((d) => (d.releases ?? []).map((r) => r.uploadId)))

const client = new pg.Client({ connectionString: process.env.DATABASE_URL })
await client.connect()
const { rows } = await client.query('SELECT upload_id FROM datasource_history WHERE upload_id = ANY($1)', [[...onWebsite]])
await client.end()

const here = new Set(rows.map((r) => r.upload_id))
const missing = [...onWebsite].filter((id) => !here.has(id))
if (missing.length) {
  const tables = [...new Set(missing.map((id) => id.replace(/_[0-9a-f-]{36}$/, '')))]
  console.error(`\nNot published: the website shows ${missing.length} upload(s) that are not in this copy's database`)
  console.error(`(tables: ${tables.slice(0, 10).join(', ')}${tables.length > 10 ? ', ...' : ''}).`)
  console.error('They were published from another copy. Publishing from here would remove them from the website.')
  console.error('Ask whoever published them for their database file, or publish from their copy instead.')
  process.exit(1)
}
console.log(`Safety check passed: all ${onWebsite.size} uploads on the website are in this copy.`)
