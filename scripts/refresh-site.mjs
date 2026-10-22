#!/usr/bin/env node
/**
 * One command to put newly approved data on the website:
 *   1. export the data from the database in .env (npm run export:data)
 *   2. save the new snapshot to GitHub (commit + push public/data)
 *   3. republish the website (npm run deploy:pages)
 *
 *   npm run refresh
 *
 * Safe to schedule (cron, Windows Task Scheduler) on a computer that can reach the
 * database and push to GitHub: when no approved data changed, nothing is committed
 * or published. `--force` republishes anyway; other arguments go to the export,
 * e.g. `npm run refresh -- --tables=db`.
 */
import { execSync } from 'node:child_process'

const run = (cmd) => execSync(cmd, { stdio: 'inherit' })
const read = (cmd) => execSync(cmd, { encoding: 'utf8' }).trim()
const force = process.argv.includes('--force')
const exportArgs = process.argv.slice(2).filter((a) => a !== '--force').join(' ')

run(`npm run export:data${exportArgs ? ` -- ${exportArgs}` : ''}`)

run('git add public/data')
const changed = read('git diff --cached --name-only -- public/data')
if (changed) {
  const day = new Date().toISOString().slice(0, 10)
  run(`git commit -q -m "Refresh data ${day}" -- public/data`)
  run('git push -q')
  console.log(`Saved ${changed.split('\n').length} updated data files to GitHub.`)
} else if (!force) {
  console.log('\nNo new approved data since the last refresh; the website is already up to date.')
  process.exit(0)
}

run('npm run deploy:pages')
console.log('\nWebsite refreshed with the latest approved data.')
