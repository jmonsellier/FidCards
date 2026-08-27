#!/usr/bin/env node
// Troisième source : Wikidata, propriété P154 « image de logo ».
//
// Bien plus précise que la recherche plein texte de Commons, qui ramenait du bruit
// (« logo Ange » renvoyait un homme politique). Ici on résout d'abord l'entité de
// l'enseigne, puis on lit son logo déclaré. Les fichiers viennent de Commons, donc
// sous licence libre, et l'API n'impose aucune restriction d'usage programmatique.
//
// Usage : node tools/fetch-logos-wikidata.mjs [--limit=N]

import { readFileSync, writeFileSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const LOGOS = join(ROOT, 'brands', 'logos')
const JSON_PATH = join(ROOT, 'brands', 'brands.json')
const LIMIT = Number(process.argv.find(a => a.startsWith('--limit='))?.split('=')[1] ?? Infinity)

const UA = { 'User-Agent': 'FidCards/1.0 (projet personnel; contact@example.invalid)' }
const sleep = ms => new Promise(r => setTimeout(r, ms))

const norm = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

// Entités à écarter : personnes, communes, œuvres… tout ce qui n'est pas une marque.
const BAD_DESC = /(homme|femme|personnalit|politique|commune|village|prénom|film|album|chanson|roman|footballeur|acteur|actrice|peintre|écrivain)/i

async function api(url) {
  const r = await fetch(url, { headers: UA })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

async function findLogo(name) {
  const search = await api(`https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${
    encodeURIComponent(name)}&language=fr&uselang=fr&format=json&limit=5&origin=*`)

  const key = norm(name).split(/\s+/)[0]          // premier mot significatif de l'enseigne
  for (const hit of search.search ?? []) {
    // Garde-fou anti-faux-positif : le label doit rappeler l'enseigne, et la description
    // ne doit pas désigner une personne ou un lieu.
    if (!norm(hit.label).includes(key) && !norm(hit.match?.text ?? '').includes(key)) continue
    if (BAD_DESC.test(hit.description ?? '')) continue

    const claims = await api(`https://www.wikidata.org/w/api.php?action=wbgetclaims&entity=${
      hit.id}&property=P154&format=json`)
    const file = claims.claims?.P154?.[0]?.mainsnak?.datavalue?.value
    if (file) return { entity: hit.id, label: hit.label, file }
    await sleep(200)
  }
  return null
}

async function fileInfo(file) {
  const j = await api(`https://commons.wikimedia.org/w/api.php?action=query&titles=${
    encodeURIComponent('File:' + file)}&prop=imageinfo&iiprop=url|extmetadata&format=json&origin=*`)
  const info = Object.values(j.query?.pages ?? {})[0]?.imageinfo?.[0]
  return {
    url: info?.url ?? null,
    license: info?.extmetadata?.LicenseShortName?.value?.replace(/<[^>]+>/g, '') ?? 'inconnue',
  }
}

const brands = JSON.parse(readFileSync(JSON_PATH, 'utf8'))
const todo = brands.filter(b => !b.logo).slice(0, LIMIT)
console.log(`${todo.length} enseignes sans logo à chercher sur Wikidata\n`)

let found = 0
for (const [i, b] of todo.entries()) {
  let status = '—'
  try {
    const hit = await findLogo(b.name)
    if (hit) {
      const { url, license } = await fileInfo(hit.file)
      if (!url) { status = '✗ fichier introuvable sur Commons'; }
      else {
        const res = await fetch(url, { headers: UA, redirect: 'follow' })
        if (res.ok) {
          const buf = Buffer.from(await res.arrayBuffer())
          const ext = hit.file.split('.').pop().toLowerCase().replace('jpeg', 'jpg')
          writeFileSync(join(LOGOS, `${b.id}.${ext}`), buf)
          b.logo = `${b.id}.${ext}`
          b.source = `wikidata:${hit.entity}`
          b.license = license
          b.w = null; b.h = null
          status = `✓ ${hit.file.slice(0, 30)} · ${license.slice(0, 18)}`
          found++
        } else status = `✗ téléchargement HTTP ${res.status}`
      }
    } else status = 'pas de P154'
  } catch (e) {
    status = `✗ ${String(e.message).slice(0, 34)}`
  }
  console.log(`  [${String(i + 1).padStart(3)}/${todo.length}] ${b.name.padEnd(24)} ${status}`)
  await sleep(400)                                  // cadence respectueuse de l'API
}

writeFileSync(JSON_PATH, JSON.stringify(brands, null, 2) + '\n')
console.log(`\nRécupérés : ${found} · sans logo : ${brands.filter(b => !b.logo).length}/${brands.length}`)
