#!/usr/bin/env node
// Quatrième source : l'infobox de Wikipédia FR.
//
// Complémentaire de Wikidata/Commons, et pas redondante : les logos figuratifs NON
// libres sont refusés par Commons, donc uploadés localement sur fr.wikipedia sous
// exception de courte citation. C'est précisément la catégorie qui manquait
// (Boulangerie Ange, enseignes de proximité…).
//
// Licence : ces fichiers ne sont PAS redistribuables. Ils sont marqués
// « fr-wikipedia:non-libre » dans brands.json — ce qui permettrait de les purger
// d'une requête si le projet devenait public un jour.
//
// Usage : node tools/fetch-logos-wikipedia.mjs [--limit=N] [--dry]

import { readFileSync, writeFileSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const LOGOS = join(ROOT, 'brands', 'logos')
const JSON_PATH = join(ROOT, 'brands', 'brands.json')

const args = process.argv.slice(2)
const LIMIT = Number(args.find(a => a.startsWith('--limit='))?.split('=')[1] ?? Infinity)
const DRY = args.includes('--dry')

const UA = { 'User-Agent': 'FidCards/1.0 (projet personnel; contact@example.invalid)' }
const API = 'https://fr.wikipedia.org/w/api.php'
const sleep = ms => new Promise(r => setTimeout(r, ms))
const norm = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

async function api(params) {
  const url = `${API}?${new URLSearchParams({ ...params, format: 'json', origin: '*' })}`
  const r = await fetch(url, { headers: UA })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

// Les pages d'homonymie et de personnes polluent la recherche ("Ange").
const BAD_TITLE = /\(homonymie\)|\(prénom\)|\(film\)|\(album\)|\(chanson\)/i

async function findPage(name, sector) {
  // On cherche l'enseigne, en s'aidant du secteur pour départager.
  const queries = [`${name} enseigne`, `${name} ${sector}`, name]
  for (const q of queries) {
    const j = await api({ action: 'query', list: 'search', srsearch: q, srlimit: 4 })
    for (const hit of j.query?.search ?? []) {
      if (BAD_TITLE.test(hit.title)) continue
      // Comparaison par MOTS ENTIERS, et sur TOUS les mots significatifs du nom.
      // Un seul mot suffisait à faire tomber « Sport 2000 » sur « Go Sport » et
      // « Boulangerie Louise » sur « Boulangerie Ange » : le mot commun trompe.
      const words = new Set(norm(hit.title).split(/[^a-z0-9]+/).filter(Boolean))
      const keys = norm(name).split(/[^a-z0-9]+/).filter(w => w.length > 2)
      if (keys.length && !keys.every(k => words.has(k))) continue

      // Contrôle en sens inverse : le titre ne doit pas introduire de mot étranger.
      // « Point S » n'a qu'un mot comparable (« S » fait un caractère), et « Point Vert »
      // le contenait — d'où un logo de la mauvaise enseigne.
      const own = new Set(norm(name).split(/[^a-z0-9]+/).filter(Boolean))
      const GENERIC = new Set(['groupe', 'entreprise', 'societe', 'france', 'sa', 'sas',
        'magasin', 'magasins', 'enseigne', 'distribution', 'international', 'holding'])
      for (const w of words) {
        if (w.length > 2 && !own.has(w) && !GENERIC.has(w)) { hit.title = null; break }
      }
      if (!hit.title) continue
      return hit.title
    }
    await sleep(150)
  }
  return null
}

// Résout un nom de fichier Commons/local vers son URL de téléchargement.
async function fileUrl(file) {
  const j = await api({ action: 'query', titles: `File:${file}`, prop: 'imageinfo', iiprop: 'url' })
  return Object.values(j.query?.pages ?? {})[0]?.imageinfo?.[0]?.url ?? null
}

async function imageOf(title) {
  // 1. Le wikicode de l'infobox nomme le logo explicitement. C'est la source fiable :
  //    pageimages renvoie la première image de la page, souvent une photo de devanture.
  const w = await api({ action: 'query', titles: title, prop: 'revisions', rvprop: 'content', rvslots: 'main' })
  const page = Object.values(w.query?.pages ?? {})[0]
  const text = page?.revisions?.[0]?.slots?.main?.['*'] ?? ''

  // Le type d'infobox renseigne sans être bloquant : beaucoup de pages d'enseignes
  // n'en portent pas (Léon de Bruxelles, Basic-Fit, Amorino) tout en affichant un
  // fichier « Logo… ». C'est le contrôle bidirectionnel du titre, plus haut, qui écarte
  // les homonymes ; ici on se contente de savoir à quel point faire confiance.
  const isCompany = /\{\{\s*Infobox\s+(Soci[ée]t[ée]|Entreprise|Marque|Enseigne)/i.test(text)

  // Uniquement « logo » : le paramètre « image » d'une infobox est très souvent une
  // photo de devanture (Okaïdi renvoyait « Magasin Okaidi - Plaisir.jpg »).
  for (const key of ['logo']) {
    const m = text.match(new RegExp(`\\|\\s*${key}\\s*=\\s*([^\\n|}\\[\\]]+)`, 'i'))
    let file = m?.[1]?.trim()
    if (!file || file.length < 4) continue
    file = file.replace(/^(File|Fichier|Image)\s*:\s*/i, '')
    if (!/\.(svg|png|jpe?g|webp|gif)$/i.test(file)) continue
    // Hors page d'entreprise avérée, on exige que le fichier s'annonce comme un logo.
    if (!isCompany && !/logo/i.test(file)) continue
    const url = await fileUrl(file)
    if (url) return { url, file, from: `infobox:${key}` }
  }

  // 2. Repli : pageimages, mais seulement si le fichier s'annonce comme un logo.
  const j = await api({ action: 'query', titles: title, prop: 'pageimages', piprop: 'original' })
  const src = Object.values(j.query?.pages ?? {})[0]?.original?.source
  if (!src) return null
  const file = decodeURIComponent(src.split('/').pop().split('?')[0])
  if (!/logo/i.test(file)) return null        // une photo de devanture n'est pas un logo
  return { url: src, file, from: 'pageimages' }
}

const brands = JSON.parse(readFileSync(JSON_PATH, 'utf8'))
const todo = brands.filter(b => !b.logo).slice(0, LIMIT)
console.log(`${todo.length} enseignes sans logo à chercher sur Wikipédia FR\n`)

let found = 0
for (const [i, b] of todo.entries()) {
  let status = '—'
  try {
    const title = await findPage(b.name, b.sector)
    if (!title) status = 'aucune page'
    else {
      const img = await imageOf(title)
      if (!img) status = `« ${title.slice(0, 26)} » sans logo`
      else {
        const looksLogo = img.from.startsWith('infobox') || /logo/i.test(img.file)
        if (DRY) {
          status = `${looksLogo ? '✓' : '~'} ${img.from.padEnd(14)} ${img.file.slice(0, 42)}`
        } else {
          const res = await fetch(img.url, { headers: UA })
          if (!res.ok) status = `✗ téléchargement HTTP ${res.status}`
          else {
            const buf = Buffer.from(await res.arrayBuffer())
            const ext = img.file.split('.').pop().toLowerCase().replace('jpeg', 'jpg')
            writeFileSync(join(LOGOS, `${b.id}.${ext}`), buf)
            b.logo = `${b.id}.${ext}`
            b.source = `fr-wikipedia:${title}`
            b.license = 'non-libre'          // uploadé localement = non redistribuable
            b.w = null; b.h = null
            status = `${looksLogo ? '✓' : '~'} ${img.file.slice(0, 44)}`
            found++
          }
        }
      }
    }
  } catch (e) {
    status = `✗ ${String(e.message).slice(0, 34)}`
  }
  console.log(`  [${String(i + 1).padStart(2)}/${todo.length}] ${b.name.padEnd(24)} ${status}`)
  await sleep(300)
}

if (!DRY) writeFileSync(JSON_PATH, JSON.stringify(brands, null, 2) + '\n')
console.log(`\nRécupérés : ${found} · sans logo : ${brands.filter(b => !b.logo).length}/${brands.length}`)
console.log('~ = le nom du fichier n\'évoque pas un logo : à vérifier sur la planche')
