#!/usr/bin/env node
// Seconde passe de sourcing : navigateur réel pour les sites qui bloquent
// un client HTTP simple (403 Cloudflare, timeouts, balises injectées en JS).
//
// Réutilise l'installation Playwright de ~/Projects/csm-scraper.
// Réglages repris de la note « Scraper CSM » : domcontentloaded, mono-thread,
// throttle 1000-1800 ms, et surtout : un échec n'est JAMAIS mémorisé comme « vu ».
//
// Usage : node tools/fetch-logos-browser.mjs [--limit=N] [--headed]

import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const require = createRequire('/Users/jerome/Projects/csm-scraper/')
const { chromium } = require('playwright')

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const LOGOS = join(ROOT, 'brands', 'logos')
const JSON_PATH = join(ROOT, 'brands', 'brands.json')

const MIN_PX = 128
const args = process.argv.slice(2)
const LIMIT = Number(args.find(a => a.startsWith('--limit='))?.split('=')[1] ?? Infinity)
const HEADED = args.includes('--headed')
const UPGRADE = args.includes('--upgrade')   // rechercher un vrai logo pour les entrées en favicon

const sleep = ms => new Promise(r => setTimeout(r, ms))
const throttle = () => sleep(1000 + Math.random() * 800)   // 1000-1800 ms

// --- dimensions, tous formats ---
function dimsOf(buf) {
  if (buf.length > 24 && buf.readUInt32BE(0) === 0x89504e47) return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) }
  if (buf.slice(0, 300).includes('<svg')) return { w: 9999, h: 9999, svg: true }
  if (buf[0] === 0xff && buf[1] === 0xd8) {                                               // JPEG
    let i = 2
    while (i < buf.length - 9) {
      if (buf[i] !== 0xff) { i++; continue }
      const m = buf[i + 1]
      if (m >= 0xc0 && m <= 0xc2) return { w: buf.readUInt16BE(i + 7), h: buf.readUInt16BE(i + 5) }
      if (m === 0xd8 || m === 0xd9 || (m >= 0xd0 && m <= 0xd7)) i += 2
      else i += 2 + buf.readUInt16BE(i + 2)
    }
  }
  if (buf.slice(0, 4).toString() === 'RIFF' && buf.slice(8, 12).toString() === 'WEBP') {
    const f = buf.slice(12, 16).toString()
    try {
      if (f === 'VP8X') return { w: ((buf[24] | (buf[25] << 8) | (buf[26] << 16)) & 0xffffff) + 1, h: 0 }
      if (f === 'VP8 ') return { w: buf.readUInt16LE(26) & 0x3fff, h: buf.readUInt16LE(28) & 0x3fff }
      if (f === 'VP8L') return { w: (buf.readUInt32LE(21) & 0x3fff) + 1, h: 0 }
    } catch { return { w: 0, h: 0 } }
  }
  if (buf.readUInt32LE(0) === 0x00010000) return { w: buf[6] || 256, h: buf[7] || 256 }
  return { w: 0, h: 0 }
}

const extOf = buf =>
  buf.slice(0, 300).includes('<svg') ? 'svg'
  : buf.readUInt32BE?.(0) === 0x89504e47 ? 'png'
  : buf[0] === 0xff && buf[1] === 0xd8 ? 'jpg'
  : buf.slice(8, 12).toString() === 'WEBP' ? 'webp' : 'png'

// --- extraction des candidats depuis la page rendue ---
const COLLECT = () => {
  const out = []
  const abs = u => { try { return new URL(u, location.href).href } catch { return null } }

  // apple-touch-icon retiré : carré par nature, donc monogramme comme le favicon.
  const og = document.querySelector('meta[property="og:image"], meta[name="og:image"]')
  if (og?.content) out.push({ url: abs(og.content), hint: 600, kind: 'og:image' })

  // link[rel=icon] retiré : 75 % de rejet. C'est un favicon sous un autre nom.
  // <img> du header dont le nom, la classe ou l'alt évoque un logo
  const header = document.querySelector('header, [class*="header" i], [id*="header" i]')
  const scan = (root, kind, bonus) => {
    for (const img of root.querySelectorAll('img')) {
      const sig = `${img.src} ${img.className} ${img.alt} ${img.id}`.toLowerCase()
      if (!sig.includes('logo')) continue
      const src = img.getAttribute('src') || img.getAttribute('data-src') || img.getAttribute('data-lazy-src')
      if (src) out.push({ url: abs(src), hint: (img.naturalWidth || 200) + bonus, kind })
    }
  }
  if (header) scan(header, 'header-img', 100)
  // Le scan de tout le <body> a été retiré : 56 % de rejet mesuré. Il ramassait
  // des logos de partenaires et de moyens de paiement. Repli restreint au lien racine.
  const home = document.querySelector('a[href="/"], a[href="' + location.origin + '/"]')
  if (home && !header?.contains(home)) scan(home, 'home-link-img', 50)

  // SVG inline : très répandu sur les sites modernes, invisible à un scan d'<img>.
  const roots = [header, document.querySelector('a[href="/"]'), document.body].filter(Boolean)
  for (const root of roots) {
    for (const svg of root.querySelectorAll('svg')) {
      const sig = `${svg.getAttribute('class') || ''} ${svg.id} ${svg.getAttribute('aria-label') || ''}`.toLowerCase()
      const inLink = svg.closest('a')?.getAttribute('href')
      const looksLogo = sig.includes('logo') || inLink === '/' || inLink === location.origin + '/'
      if (!looksLogo) continue
      if (svg.querySelector('use')) continue          // référence un sprite externe : inexploitable seul
      const box = svg.getBoundingClientRect()
      if (box.width < 40) continue
      out.push({ svgMarkup: svg.outerHTML, hint: 5000, kind: 'svg-inline' })
      break
    }
    if (out.some(c => c.kind === 'svg-inline')) break
  }

  // background-image CSS sur un élément dont la classe évoque un logo
  for (const el of document.querySelectorAll('[class*="logo" i], [id*="logo" i]')) {
    const bg = getComputedStyle(el).backgroundImage
    const m = bg && bg.match(/url\(["']?([^"')]+)/)
    if (m) out.push({ url: abs(m[1]), hint: 300, kind: 'css-bg' })
  }

  // Ordre issu des taux de rejet mesurés sur 111 verdicts humains :
  // svg-inline 0 % · og:image 17 % · header-img 28 % · css-bg non mesuré
  const PRIO = { 'svg-inline': 5, 'og:image': 4, 'header-img': 3, 'home-link-img': 2, 'css-bg': 1 }
  return out.filter(c => c.url || c.svgMarkup)
            .sort((a, b) => (PRIO[b.kind] ?? 0) - (PRIO[a.kind] ?? 0) || b.hint - a.hint)
}

// --- exécution ---
const brands = JSON.parse(readFileSync(JSON_PATH, 'utf8'))
const todo = brands
  .filter(b => b.website && (UPGRADE ? b.source === 'favicon' : !b.logo))
  .slice(0, LIMIT)
console.log(UPGRADE
  ? `${todo.length} favicons à remplacer par un vrai logo de site\n`
  : `${todo.length} enseignes à traiter au navigateur\n`)

const browser = await chromium.launch({ headless: !HEADED })
const ctx = await browser.newContext({
  viewport: { width: 1280, height: 900 },
  userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
  locale: 'fr-FR',
  ignoreHTTPSErrors: true,
})

let found = 0, failed = 0
for (const [i, b] of todo.entries()) {
  const page = await ctx.newPage()
  let status = 'échec'
  try {
    const base = b.website.replace(/^https?:\/\//, '')
    const tries = base.startsWith('www.') ? [base] : [base, `www.${base}`]
    let loaded = false, lastErr
    for (const host of tries) {
      try {
        await page.goto(`https://${host}`, { waitUntil: 'domcontentloaded', timeout: 22000 })
        loaded = true
        break
      } catch (e) { lastErr = e }
    }
    if (!loaded) throw lastErr
    await sleep(600)                                  // laisse le header s'hydrater
    const cands = await page.evaluate(COLLECT)

    for (const c of cands.slice(0, 8)) {
      try {
        if (c.svgMarkup) {
          let markup = c.svgMarkup
          if (!/xmlns=/.test(markup)) markup = markup.replace('<svg', '<svg xmlns="http://www.w3.org/2000/svg"')
          writeFileSync(join(LOGOS, `${b.id}.svg`), markup)
          b.logo = `${b.id}.svg`; b.source = 'site:svg-inline'; b.license = 'unknown'
          b.w = null; b.h = null
          status = '✓ svg-inline'; found++
          break
        }
        const res = await ctx.request.get(c.url, { timeout: 15000 })
        if (!res.ok()) continue
        const buf = Buffer.from(await res.body())
        const { w, h, svg } = dimsOf(buf)
        if (w < MIN_PX) continue
        // Un logo de marque est large ; un monogramme est carré. En remplacement
        // d'un favicon, exiger un format nettement horizontal, sinon on troque
        // un monogramme contre un autre.
        const ratio = h > 0 ? w / h : 0
        if (UPGRADE && !svg && ratio && ratio < 1.6) continue
        // 1200x630 (à quelques pixels près) est le gabarit Open Graph : c'est une
        // image de partage social, pas un logo. Fréquent et trompeur.
        if (UPGRADE && c.kind === 'og:image' && w >= 1180 && w <= 1220 && h >= 600 && h <= 640) continue
        const ext = extOf(buf)
        writeFileSync(join(LOGOS, `${b.id}.${ext}`), buf)
        b.logo = `${b.id}.${ext}`
        b.source = `site:${c.kind}`
        b.license = 'unknown'
        b.w = svg ? null : w
        b.h = svg ? null : h
        status = `✓ ${c.kind} ${svg ? 'svg' : `${w}x${h}`}`
        found++
        break
      } catch { /* candidat suivant */ }
    }
    if (status === 'échec') { status = cands.length ? (UPGRADE ? 'rien de mieux' : `aucun ≥${MIN_PX}px`) : 'aucun candidat'; failed++ }
  } catch (e) {
    status = `✗ ${String(e.message).split('\n')[0].slice(0, 40)}`
    failed++
  } finally {
    await page.close()
  }
  console.log(`  [${String(i + 1).padStart(3)}/${todo.length}] ${b.name.padEnd(22)} ${status}`)
  await throttle()
}

await browser.close()

// --- sorties : on réécrit toujours, même partiellement ---
writeFileSync(JSON_PATH, JSON.stringify(brands, null, 2) + '\n')
const missing = brands.filter(b => !b.logo)
writeFileSync(join(ROOT, 'brands', 'MISSING.md'),
  `# Logos manquants — ${missing.length} sur ${brands.length}\n\n` +
  `Après passe HTTP (\`fetch-logos.mjs\`) puis passe navigateur (\`fetch-logos-browser.mjs\`).\n` +
  `Ces enseignes n'exposent aucune image exploitable au-dessus de ${MIN_PX} px : reprise manuelle.\n\n` +
  `| Enseigne | Secteur | Site |\n|---|---|---|\n` +
  missing.map(b => `| ${b.name} | ${b.sector} | ${b.website ?? '—'} |`).join('\n') + '\n')

console.log(`\nRécupérés : ${found}   ·   échecs : ${failed}`)
console.log(`Total avec logo : ${brands.filter(b => b.logo).length}/${brands.length}`)
console.log(`Manquants : ${missing.length} -> brands/MISSING.md`)
