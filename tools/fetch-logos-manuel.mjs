#!/usr/bin/env node
// Passe manuelle assistée : pour les enseignes que la recherche automatique ne
// résout pas, on fournit le titre exact de la page Wikipédia. Le script ne fait
// plus que la mécanique — trouver le fichier logo et le télécharger.
//
// Les filtres de titre du script automatique sont volontairement absents ici :
// le jugement a déjà été fait en amont, en écrivant la table ci-dessous.
//
// Usage : node tools/fetch-logos-manuel.mjs [--dry]

import { readFileSync, writeFileSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const LOGOS = join(ROOT, 'brands', 'logos')
const JSON_PATH = join(ROOT, 'brands', 'brands.json')
const DRY = process.argv.includes('--dry')

const UA = { 'User-Agent': 'FidCards/1.0 (projet personnel; contact@example.invalid)' }
const sleep = ms => new Promise(r => setTimeout(r, ms))

// id de l'enseigne -> titre exact de la page Wikipédia FR.
// Les enseignes d'un même groupe pointent vers la même page : elles partagent
// leur identité visuelle, et le catalogue gère déjà le partage de logo.
const PAGES = {
  superu:            'Système U',
  hyperu:            'Système U',
  uexpress:          'Système U',
  marcheu:           'Système U',
  utile:             'Système U',
  casinosupermarche: 'Groupe Casino',
  casinoshopping:    'Groupe Casino',
  midas:             'Midas (entreprise)',
  points:            'Point S',
  okaidi:            'Okaïdi',
  bessonchaussures:  'Besson Chaussures',
  otacos:            "O'Tacos",
  // Écartés : leur page renvoie le logo de la société mère, pas celui de l'enseigne.
  //   pointp    -> 'Point.P'     donne Logo_Saint-Gobain.png
  //   sport2000 -> 'Sport 2000'  donne Logo Céraclès Coopérative.png
  leondebruxelles:   'Léon de Bruxelles',
  generaledoptique:  "Générale d'Optique",
  maxitoys:          'Maxi Toys',
  pathegaumont:      'Les Cinémas Gaumont Pathé',
  parashop:          'Parashop',
  lapanetiere:       'La Panetière',
  boulangerielouise: 'Boulangerie Louise',
  basicfit:          'Basic-Fit',
  lorangebleue:      "L'Orange bleue",
  longlerie:         "L'Onglerie",
  leonidas:          'Leonidas (chocolaterie)',
  deneuville:        'De Neuville',
  amorino:           'Amorino',
}

const api = async (host, params) => {
  const url = `https://${host}/w/api.php?${new URLSearchParams({ ...params, format: 'json', origin: '*' })}`
  const r = await fetch(url, { headers: UA })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

async function fileUrl(file) {
  for (const host of ['fr.wikipedia.org', 'commons.wikimedia.org']) {
    try {
      const j = await api(host, { action: 'query', titles: `File:${file}`, prop: 'imageinfo', iiprop: 'url' })
      const u = Object.values(j.query?.pages ?? {})[0]?.imageinfo?.[0]?.url
      if (u) return u
    } catch {}
  }
  return null
}

async function logoOfPage(title) {
  const w = await api('fr.wikipedia.org', {
    action: 'query', titles: title, prop: 'revisions', rvprop: 'content', rvslots: 'main', redirects: 1,
  })
  const page = Object.values(w.query?.pages ?? {})[0]
  if (!page || page.missing !== undefined) return { error: 'page inexistante' }
  const text = page.revisions?.[0]?.slots?.main?.['*'] ?? ''

  const m = text.match(/\|\s*logo\s*=\s*([^\n|}\[\]]+)/i)
  let file = m?.[1]?.trim().replace(/^(File|Fichier|Image)\s*:\s*/i, '')
  if (file && /\.(svg|png|jpe?g|webp|gif)$/i.test(file)) {
    const url = await fileUrl(file)
    if (url) return { url, file, from: 'infobox' }
  }

  // Repli : première image de la page, si elle s'annonce comme un logo.
  const j = await api('fr.wikipedia.org', {
    action: 'query', titles: title, prop: 'pageimages', piprop: 'original', redirects: 1,
  })
  const src = Object.values(j.query?.pages ?? {})[0]?.original?.source
  if (src) {
    const f = decodeURIComponent(src.split('/').pop().split('?')[0])
    if (/logo/i.test(f)) return { url: src, file: f, from: 'pageimages' }
  }
  return { error: 'aucun logo sur la page' }
}

const brands = JSON.parse(readFileSync(JSON_PATH, 'utf8'))
const byId = Object.fromEntries(brands.map(b => [b.id, b]))
const cache = new Map()                    // une page peut servir plusieurs enseignes

let found = 0
for (const [id, title] of Object.entries(PAGES)) {
  const b = byId[id]
  if (!b) { console.log(`  ${id.padEnd(20)} inconnu du catalogue`); continue }
  if (b.logo) { console.log(`  ${b.name.padEnd(22)} déjà pourvu`); continue }

  try {
    if (!cache.has(title)) {
      cache.set(title, await logoOfPage(title))
      await sleep(300)
    }
    const r = cache.get(title)
    if (r.error) { console.log(`  ${b.name.padEnd(22)} ✗ ${title} — ${r.error}`); continue }

    if (DRY) {
      console.log(`  ${b.name.padEnd(22)} ✓ ${r.file.slice(0, 44)}`)
      found++
      continue
    }
    if (!r.buf) {
      const res = await fetch(r.url, { headers: UA })
      if (!res.ok) { console.log(`  ${b.name.padEnd(22)} ✗ téléchargement ${res.status}`); continue }
      r.buf = Buffer.from(await res.arrayBuffer())
      r.ext = r.file.split('.').pop().toLowerCase().replace('jpeg', 'jpg')
    }
    writeFileSync(join(LOGOS, `${b.id}.${r.ext}`), r.buf)
    b.logo = `${b.id}.${r.ext}`
    b.source = `fr-wikipedia:${title}`
    b.license = 'non-libre'
    b.w = null; b.h = null
    console.log(`  ${b.name.padEnd(22)} ✓ ${r.file.slice(0, 44)}`)
    found++
  } catch (e) {
    console.log(`  ${b.name.padEnd(22)} ✗ ${String(e.message).slice(0, 40)}`)
  }
}

if (!DRY) writeFileSync(JSON_PATH, JSON.stringify(brands, null, 2) + '\n')
console.log(`\nRécupérés : ${found} · sans logo : ${brands.filter(b => !b.logo).length}/${brands.length}`)
