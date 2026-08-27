#!/usr/bin/env node
// Import des logos fournis à la main, par fichier local ou par URL.
//
// Ces logos priment sur tout ce que le sourcing automatique a trouvé : ils sont
// marqués `validated` et `source: "manuel"`, et ne seront jamais re-scrapés.
// L'ancien logo est archivé dans brands/_remplaces/ plutôt que supprimé.
//
// Usage : node tools/import-logos-manuels.mjs

import { readFileSync, writeFileSync, copyFileSync, renameSync, mkdirSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { homedir } from 'node:os'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const LOGOS = join(ROOT, 'brands', 'logos')
const ARCHIVE = join(ROOT, 'brands', '_remplaces')
const JSON_PATH = join(ROOT, 'brands', 'brands.json')
mkdirSync(ARCHIVE, { recursive: true })

const DL = join(homedir(), 'Downloads')

// id -> source (chemin local ou URL)
const SOURCES = {
  lidl:       join(DL, 'lidl-shop-fr-lidl-france-256207.svg'),
  picard:     join(DL, 'logo-picard-desktop.svg'),
  darty:      join(DL, 'darty.svg'),
  shell:      join(DL, 'shell.svg'),
  maxizoo:    join(DL, 'maxizoo.jpeg'),
  etam:       'https://www.etam.com/on/demandware.static/-/Sites/default/dw8d6ed06f/Etam_Logo_BLACK.png',
  decathlon:  'https://upload.wikimedia.org/wikipedia/commons/thumb/b/bc/Decathlon_-_logo_(France%2C_2024).svg/3840px-Decathlon_-_logo_(France%2C_2024).svg.png',
  micromania: 'https://www.micromania.fr/on/demandware.static/-/Sites-Micromania-Library/default/logo-micromania-cmp.png',
  burgerking: 'https://upload.wikimedia.org/wikipedia/commons/thumb/c/cc/Burger_King_2020.svg/1920px-Burger_King_2020.svg.png',
  ikea:       'https://www.ikea.com/global/assets/logos/brand/ikea.svg',
  metro:      'https://upload.wikimedia.org/wikipedia/commons/f/fe/Logo_METRO_France.jpg',
}

const UA = { 'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36' }

const extOf = src => {
  const clean = src.split('?')[0].split('#')[0]
  const e = clean.split('.').pop().toLowerCase()
  return e === 'jpeg' ? 'jpg' : ['svg', 'png', 'jpg', 'webp', 'gif'].includes(e) ? e : 'png'
}

// Arguments en ligne de commande : id=source (URL ou chemin local). Ils s'ajoutent
// à la table ci-dessus, et sont seuls traités s'il y en a — pour ne pas retélécharger
// tout le lot à chaque nouveau logo.
const cli = Object.fromEntries(process.argv.slice(2)
  .filter(a => a.includes('='))
  .map(a => { const i = a.indexOf('='); return [a.slice(0, i), a.slice(i + 1)] }))
const TARGETS = Object.keys(cli).length ? cli : SOURCES

// Reconnaissance du format par les octets de tête, plus fiable que l'extension d'URL.
const sniff = buf => {
  if (buf.length > 12 && buf.readUInt32BE(0) === 0x89504e47) return 'png'
  if (buf[0] === 0xff && buf[1] === 0xd8) return 'jpg'
  if (buf.subarray(0, 4).toString() === 'RIFF' && buf.subarray(8, 12).toString() === 'WEBP') return 'webp'
  if (buf.subarray(0, 600).toString('latin1').includes('<svg')) return 'svg'
  if (buf.subarray(0, 6).toString() === 'GIF89a' || buf.subarray(0, 6).toString() === 'GIF87a') return 'gif'
  return null
}

const brands = JSON.parse(readFileSync(JSON_PATH, 'utf8'))
const byId = Object.fromEntries(brands.map(b => [b.id, b]))

let ok = 0
for (const [id, src] of Object.entries(TARGETS)) {
  const b = byId[id]
  if (!b) { console.log(`  ${id.padEnd(12)} ✗ inconnu du catalogue`); continue }

  let ext = extOf(src)
  let dest = join(LOGOS, `${id}.${ext}`)
  try {
    if (src.startsWith('http')) {
      const res = await fetch(src, { headers: UA, redirect: 'follow' })
      if (!res.ok) { console.log(`  ${b.name.padEnd(12)} ✗ HTTP ${res.status}`); continue }
      const buf = Buffer.from(await res.arrayBuffer())
      if (buf.length < 200) { console.log(`  ${b.name.padEnd(12)} ✗ réponse vide`); continue }
      // Un site protégé renvoie une page HTML avec le bon Content-Type : Micromania a
      // ainsi produit un « PNG » de 0,2 Ko contenant <html><head>.
      const head = buf.subarray(0, 200).toString('latin1').toLowerCase()
      if (head.includes('<html') || head.includes('<!doctype')) {
        console.log(`  ${b.name.padEnd(12)} ✗ page HTML renvoyée, pas une image`); continue
      }
      // L'extension vient de l'URL, qui ment régulièrement : des logos servis en .png
      // contiennent du WebP. On se fie aux octets de tête.
      const real = sniff(buf)
      if (real && real !== ext) {
        console.log(`  ${''.padEnd(12)}   contenu ${real}, pas ${ext} — extension corrigée`)
        ext = real
        dest = join(LOGOS, `${id}.${ext}`)
      }
      writeFileSync(dest, buf)
    } else {
      if (!existsSync(src)) { console.log(`  ${b.name.padEnd(12)} ✗ fichier introuvable`); continue }
      copyFileSync(src, dest)
    }

    // archiver l'ancien s'il portait une autre extension
    if (b.logo && b.logo !== `${id}.${ext}`) {
      try { renameSync(join(LOGOS, b.logo), join(ARCHIVE, b.logo)) } catch {}
    }

    // Un SVG sans viewBox ne se met pas à l'échelle : il reste figé à sa taille
    // intrinsèque. Fréquent sur les logos exportés depuis un site.
    if (ext === 'svg') {
      let svg = readFileSync(dest, 'utf8')
      const tag = svg.match(/<svg[^>]*>/)?.[0]
      if (tag && !/viewbox/i.test(tag)) {
        const w = tag.match(/\bwidth="([\d.]+)/)?.[1]
        const h = tag.match(/\bheight="([\d.]+)/)?.[1]
        if (w && h) {
          svg = svg.replace(tag, tag.slice(0, -1) + ` viewBox="0 0 ${w} ${h}">`)
          writeFileSync(dest, svg)
          console.log(`  ${''.padEnd(12)}   viewBox 0 0 ${w} ${h} ajouté`)
        }
      }
    }

    b.logo = `${id}.${ext}`
    b.source = 'manuel'
    b.sourceUrl = src        // permet d'ignorer un ré-import identique
    b.license = 'unknown'
    b.validated = true
    b.w = null; b.h = null
    delete b.rejected; delete b.exhausted

    const size = Math.round(readFileSync(dest).length / 102.4) / 10
    console.log(`  ${b.name.padEnd(12)} ✓ ${b.logo.padEnd(18)} ${size} Ko`)
    ok++
  } catch (e) {
    console.log(`  ${b.name.padEnd(12)} ✗ ${String(e.message).slice(0, 44)}`)
  }
}

writeFileSync(JSON_PATH, JSON.stringify(brands, null, 2) + '\n')
console.log(`\n${ok}/${Object.keys(TARGETS).length} importés · couverture ${brands.filter(b => b.logo).length}/${brands.length}`)
