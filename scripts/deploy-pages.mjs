#!/usr/bin/env node
/**
 * Builds the portal for GitHub Pages and publishes dist/ to the gh-pages branch.
 *
 *   npm run deploy:pages                 # served at https://<owner>.github.io/<repo>/
 *   BASE_PATH=/ npm run deploy:pages     # custom domain or <owner>.github.io repository
 *
 * Then, once: GitHub → Settings → Pages → Source "Deploy from a branch" → gh-pages / (root).
 */
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const run = (cmd, opts = {}) => execSync(cmd, { stdio: 'inherit', ...opts })
const read = (cmd) => execSync(cmd, { encoding: 'utf8' }).trim()

const remote = read('git remote get-url origin')
const repo = remote.replace(/\.git$/, '').split(/[/:]/).pop()
const base = process.env.BASE_PATH ?? `/${repo}/`
const source = read('git rev-parse --short HEAD')

run('npm run build', { env: { ...process.env, BASE_PATH: base } })

const dist = path.resolve('dist')
fs.writeFileSync(path.join(dist, '.nojekyll'), '') // serve files as-is, no Jekyll processing
fs.rmSync(path.join(dist, '.git'), { recursive: true, force: true })

const message = process.env.DEPLOY_MESSAGE || `Deploy ${source} to GitHub Pages`
run('git init -q -b gh-pages', { cwd: dist })
run('git add -A', { cwd: dist })
run('git commit -q -F -', { cwd: dist, input: message, stdio: ['pipe', 'inherit', 'inherit'] })
run(`git push -f "${remote}" gh-pages`, { cwd: dist })
fs.rmSync(path.join(dist, '.git'), { recursive: true, force: true })

console.log(`\nPublished ${source} to gh-pages (base path ${base}).`)
