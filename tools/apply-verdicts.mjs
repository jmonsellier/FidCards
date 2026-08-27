#!/usr/bin/env node
// Applique les verdicts de pastille exportés depuis la planche.
//
// Deux règles qui évitent les pièges rencontrés :
//   - un verdict portant sur un logo remplacé depuis l'export est ignoré ;
//   - le verdict du représentant d'un groupe est propagé à tous ses membres.
//
// Usage : node tools/apply-verdicts.mjs [chemin/vers/fidcards-fonds.json]

import { readFileSync, writeFileSync, renameSync, mkdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { homedir } from 'node:os'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const JSON_PATH = join(ROOT, 'brands', 'brands.json')
const REJECTED = join(ROOT, 'brands', '_rejetes')
const EXPORT = process.argv[2] ?? join(homedir(), 'Downloads', 'fidcards-fonds.json')
mkdirSync(REJECTED, { recursive: true })

const { background: V, replacements: U = {}, generated } = JSON.parse(readFileSync(EXPORT, 'utf8'))
// Les URL fournies depuis la planche sont traitées EN PREMIER : le verdict de pastille
// porterait sinon sur l'image que l'utilisateur veut justement remplacer.
// Une URL déjà appliquée est ignorée : la planche la conserve dans son champ, et
// la retélécharger effacerait le verdict de pastille rendu entre-temps.
const current = JSON.parse(readFileSync(JSON_PATH, 'utf8'))
const already = new Map(current.filter(b => b.sourceUrl).map(b => [b.id, b.sourceUrl]))
const urls = Object.entries(U)
  .filter(([, u]) => /^https?:\/\//i.test(u))
  .filter(([id, u]) => already.get(id) !== u)
if (urls.length) {
  console.log(`${urls.length} logo(s) à remplacer depuis la planche\n`)
  execFileSync('node', [join(ROOT, 'tools', 'import-logos-manuels.mjs'),
    ...urls.map(([id, u]) => `${id}=${u}`)], { stdio: 'inherit' })
  console.log()
}
const replaced = new Set(urls.map(([id]) => id))

const brands = JSON.parse(readFileSync(JSON_PATH, 'utf8'))
const byId = Object.fromEntries(brands.map(b => [b.id, b]))

let applied = 0, rejected = 0, stale = 0
for (const [id, v] of Object.entries(V)) {
  const b = byId[id]
  if (!b || !b.logo) continue

  // Un logo remplacé à l'instant : le verdict visait l'image précédente.
  if (replaced.has(id)) { b.background = null; delete b.validated; stale++; continue }
  // Un logo fourni à la main l'a été après coup : même raisonnement.
  if (v === 'reject' && b.source === 'manuel' && !b.background) { stale++; continue }

  if (v === 'reject') {
    try { renameSync(join(ROOT, 'brands', 'logos', b.logo), join(REJECTED, b.logo)) } catch {}
    for (const m of brands.filter(m => m.groupLogoFrom === id || m === b)) {
      m.logo = null; m.source = null; m.license = null; m.background = null
      delete m.validated; delete m.groupLogoFrom; delete m.groupLead
    }
    rejected++
  } else {
    b.background = v
    b.validated = true
    // propagation aux autres enseignes du même groupe
    for (const m of brands.filter(m => m.groupLogoFrom === id)) {
      m.background = v; m.validated = true
    }
    applied++
  }
}

writeFileSync(JSON_PATH, JSON.stringify(brands, null, 2) + '\n')

const withLogo = brands.filter(b => b.logo)
const todo = withLogo.filter(b => !b.background && !b.groupLogoFrom)
console.log(`export du ${(generated ?? '').slice(0, 16)}`)
console.log(`  ${applied} pastille(s) appliquée(s)`)
if (rejected) console.log(`  ${rejected} logo(s) rejeté(s)`)
if (stale) console.log(`  ${stale} verdict(s) périmé(s) ignoré(s) — logo remplacé depuis`)
console.log(`\ncouverture ${withLogo.length}/${brands.length} · reste ${todo.length} à trancher`)
if (todo.length) console.log('  ' + todo.map(b => b.name).join(', '))
