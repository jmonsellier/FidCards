#!/usr/bin/env node
// Passe de sourcing « facile » des logos d'enseignes.
//
// Deux sources, par ordre de qualité décroissante :
//   1. Simple Icons  — SVG vectoriel, qualité parfaite, ~15 % du catalogue
//   2. favicon Google — PNG, conservé seulement au-dessus de MIN_PX
//
// Tout le reste est listé dans MISSING.md pour une reprise ultérieure
// (navigateur headless ou récupération manuelle).
//
// Usage : node tools/fetch-logos.mjs [chemin/vers/seed_brands.csv]

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT_DIR = join(ROOT, 'brands', 'logos')
const CSV = process.argv[2] ?? '/Users/jerome/Downloads/seed_brands_1.csv'

const MIN_PX = 128        // en deçà, une image est inutilisable dans la grille
const CONCURRENCY = 6
const UA = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120 Safari/537.36'

mkdirSync(OUT_DIR, { recursive: true })

// --- CSV : parseur minimal, gère les champs entre guillemets ---
function parseCsv(text) {
  const rows = []
  let field = '', row = [], inQuotes = false
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (inQuotes) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++ }
      else if (c === '"') inQuotes = false
      else field += c
    } else if (c === '"') inQuotes = true
    else if (c === ',') { row.push(field); field = '' }
    else if (c === '\n') { row.push(field); rows.push(row); row = []; field = '' }
    else if (c !== '\r') field += c
  }
  if (field || row.length) { row.push(field); rows.push(row) }
  const header = rows.shift()
  return rows.filter(r => r.some(v => v.trim()))
             .map(r => Object.fromEntries(header.map((h, i) => [h, (r[i] ?? '').trim()])))
}

const slugify = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '')
                      .toLowerCase().replace(/[^a-z0-9]/g, '')

const pngWidth = buf =>
  buf.length > 24 && buf.readUInt32BE(0) === 0x89504e47 ? buf.readUInt32BE(16) : 0

async function get(url, timeout = 12000) {
  const ctrl = new AbortController()
  const t = setTimeout(() => ctrl.abort(), timeout)
  try {
    const res = await fetch(url, { headers: { 'User-Agent': UA }, signal: ctrl.signal })
    if (!res.ok) return null
    return Buffer.from(await res.arrayBuffer())
  } catch { return null }
  finally { clearTimeout(t) }
}

// --- sources ---
async function trySimpleIcons(name) {
  const slug = slugify(name)
  if (!slug) return null
  const buf = await get(`https://cdn.simpleicons.org/${slug}`)
  if (!buf || !buf.slice(0, 400).includes('<svg')) return null
  return { buf, ext: 'svg', source: 'simple-icons', license: 'CC0-1.0', detail: slug }
}

async function tryFavicon(domain) {
  if (!domain) return null
  const buf = await get(`https://www.google.com/s2/favicons?domain=${encodeURIComponent(domain)}&sz=256`)
  if (!buf) return null
  const w = pngWidth(buf)
  if (w < MIN_PX) return { tooSmall: w }
  return { buf, ext: 'png', source: 'favicon', license: 'unknown', detail: `${w}px` }
}

// --- exécution, dédupliquée par domaine ---
const brands = parseCsv(readFileSync(CSV, 'utf8'))
const byDomain = new Map()          // domaine -> résultat partagé
const results = []

async function handle(brand) {
  const id = slugify(brand.name)
  const domain = brand.official_website

  // Une seule récupération par domaine : Carrefour / Market / City partagent leur logo.
  if (domain && byDomain.has(domain)) {
    const shared = byDomain.get(domain)
    results.push({ ...brand, id, ...shared, shared: true })
    return
  }

  let hit = await trySimpleIcons(brand.name)
  let tooSmall = null
  if (!hit) {
    const fav = await tryFavicon(domain)
    if (fav?.buf) hit = fav
    else if (fav?.tooSmall) tooSmall = fav.tooSmall
  }

  let entry
  if (hit) {
    const file = `${id}.${hit.ext}`
    writeFileSync(join(OUT_DIR, file), hit.buf)
    entry = { file, source: hit.source, license: hit.license, detail: hit.detail }
  } else {
    entry = { file: null, source: null, license: null,
              detail: tooSmall ? `favicon ${tooSmall}px, trop petit` : 'aucune source' }
  }
  if (domain) byDomain.set(domain, entry)
  results.push({ ...brand, id, ...entry })
}

// pool de concurrence
let cursor = 0
await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
  while (cursor < brands.length) await handle(brands[cursor++])
}))

results.sort((a, b) => brands.findIndex(x => x.name === a.name) - brands.findIndex(x => x.name === b.name))

// --- sorties ---
const found = results.filter(r => r.file)
const missing = results.filter(r => !r.file)

writeFileSync(join(ROOT, 'brands', 'brands.json'), JSON.stringify(
  results.map(r => ({
    id: r.id,
    name: r.name,
    sector: r.sector,
    website: r.official_website || null,
    cardGroup: r.card_group || null,
    logo: r.file,
    source: r.source,
    license: r.license,
  })), null, 2) + '\n')

writeFileSync(join(ROOT, 'brands', 'MISSING.md'),
  `# Logos manquants — ${missing.length} sur ${results.length}\n\n` +
  `Généré par \`tools/fetch-logos.mjs\`. Les sources automatiques (Simple Icons, favicons\n` +
  `au-dessus de ${MIN_PX} px) n'ont rien donné d'exploitable pour ces enseignes.\n\n` +
  `| Enseigne | Secteur | Site | Raison |\n|---|---|---|---|\n` +
  missing.map(r => `| ${r.name} | ${r.sector} | ${r.official_website || '—'} | ${r.detail} |`).join('\n') +
  '\n')

const bySource = found.reduce((a, r) => (a[r.source] = (a[r.source] ?? 0) + 1, a), {})
console.log(`\nCatalogue      : ${results.length} enseignes`)
console.log(`Récupérés      : ${found.length}  (${Math.round(100 * found.length / results.length)} %)`)
for (const [k, v] of Object.entries(bySource)) console.log(`  ${k.padEnd(14)} ${v}`)
console.log(`Manquants      : ${missing.length}  -> brands/MISSING.md`)
console.log(`Fichiers       : brands/logos/  ·  brands/brands.json\n`)
