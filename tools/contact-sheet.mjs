#!/usr/bin/env node
// Planche de tri des logos : choix de la pastille d'affichage.
//
// Un logo noir sur fond transparent disparaît en thème sombre, un logo blanc
// disparaît en thème clair. Chaque logo doit donc porter la couleur de fond sur
// laquelle l'app le posera, indépendamment du thème choisi par l'utilisateur.
//
// La page analyse chaque image au chargement (luminosité moyenne des pixels
// opaques, taux de transparence) et PRÉ-REMPLIT une suggestion. Il ne reste qu'à
// corriger les cas douteux.
//
// L'analyse par canvas exige que les images soient servies en HTTP : ouvrir la page
// en file:// déclenche une SecurityError sur getImageData.
//
// Usage : node tools/contact-sheet.mjs puis servir brands/ en HTTP.

import { readFileSync, writeFileSync, statSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const brands = JSON.parse(readFileSync(join(ROOT, 'brands', 'brands.json'), 'utf8'))

const withLogo = brands.filter(b => b.logo && !b.groupLogoFrom)
const groupSize = brands.reduce((a, b) => {
  if (b.cardGroup) a[b.cardGroup] = (a[b.cardGroup] ?? 0) + 1
  return a
}, {})
const missing = brands.filter(b => !b.logo)
const sizeOf = b => {
  try { return Math.round(statSync(join(ROOT, 'brands', 'logos', b.logo)).size / 102.4) / 10 } catch { return 0 }
}
const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
const srcLabel = b => (b.source ?? '?').split(':')[0]

const VERDICTS = [
  ['light', 'Fond clair', 'Logo sombre : à poser sur une pastille claire'],
  ['dark', 'Fond sombre', 'Logo clair : à poser sur une pastille sombre'],
  ['any', 'Indifférent', 'Lisible sur les deux, ou fond déjà inclus dans l\'image'],
  ['reject', 'Rejeter', 'Mauvais logo : à remplacer ou à laisser en pastille'],
]

const card = b => `
  <figure class="card" data-id="${esc(b.id)}" data-file="${esc(b.logo)}" data-name="${esc(b.name.toLowerCase())}" data-src="${esc(srcLabel(b))}">
    <div class="swatches">
      <div class="sw light"><img src="logos/${esc(b.logo)}" alt="" crossorigin="anonymous"></div>
      <div class="sw dark"><img src="logos/${esc(b.logo)}" alt="" crossorigin="anonymous"></div>
    </div>
    <figcaption>
      <strong>${esc(b.name)}</strong>
      <span class="meta">${esc(srcLabel(b))} · ${sizeOf(b)} Ko · <span class="an">…</span></span>
      ${b.cardGroup && groupSize[b.cardGroup] > 1
        ? `<span class="grp">vaut pour les ${groupSize[b.cardGroup]} enseignes « ${esc(b.cardGroup)} »</span>`
        : ''}
    </figcaption>
    <div class="verdicts">
      ${VERDICTS.map(([v, lab, title]) =>
        `<button class="v v-${v}" data-v="${v}" title="${esc(title)}">${lab}</button>`).join('')}
    </div>
    <input class="url" type="url" spellcheck="false"
           placeholder="URL d'un meilleur logo…"
           title="Colle l'adresse directe d'une image (png, svg, jpg, webp). Elle sera téléchargée et remplacera ce logo.">
  </figure>`

// Aperçu de ce que l'app affichera à défaut de logo : initiales sur pastille colorée,
// teinte dérivée du nom pour rester stable d'une génération à l'autre.
const initials = n => n.replace(/[^A-Za-zÀ-ÿ ]/g, ' ').trim().split(/\s+/)
  .slice(0, 2).map(w => w[0]).join('').toUpperCase()
const hue = n => { let h = 0; for (const c of n) h = (h * 31 + c.charCodeAt(0)) % 360; return h }

const missingCard = b => `
  <figure class="card miss" data-id="${esc(b.id)}" data-name="${esc(b.name.toLowerCase())}">
    <div class="pastille" style="--h:${hue(b.name)}">${esc(initials(b.name))}</div>
    <figcaption>
      <strong>${esc(b.name)}</strong>
      <span class="meta">${esc(b.sector)}</span>
      ${b.website ? `<a class="site" href="https://${esc(b.website)}" target="_blank" rel="noreferrer">${esc(b.website)} ↗</a>` : '<span class="meta">pas de site</span>'}
    </figcaption>
    <input class="url" type="url" spellcheck="false"
           placeholder="URL du logo…"
           title="Colle l'adresse directe d'une image (png, svg, jpg, webp).">
  </figure>`

const html = `<!doctype html>
<html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>FidCards — pastille d'affichage des logos</title>
<style>
  :root{--bg:#faf9f7;--fg:#1a1a1a;--muted:#6b6b6b;--line:#e2e0dc;--card:#fff;
        --light:#1f7a4d;--dark:#5b3fa8;--any:#5c5c66;--reject:#a33b1f}
  @media (prefers-color-scheme:dark){:root{--bg:#16151a;--fg:#ececf0;--muted:#9a97a3;--line:#2e2c35;--card:#1e1d24}}
  *{box-sizing:border-box}
  body{margin:0;padding:1.5rem 2rem 4rem;background:var(--bg);color:var(--fg);
       font:15px/1.5 ui-sans-serif,-apple-system,system-ui,sans-serif}
  h1{font-size:1.4rem;margin:0 0 .2rem}
  .sub{color:var(--muted);margin:0 0 1rem;font-size:.9rem}
  .hint{background:var(--card);border:1px solid var(--line);border-left:3px solid var(--dark);
        border-radius:8px;padding:.8rem 1rem;margin-bottom:1.2rem;font-size:.86rem;max-width:70ch}
  .warn{border-left-color:var(--reject);display:none}
  .bar{position:sticky;top:0;z-index:5;background:var(--bg);padding:.7rem 0;margin-bottom:1rem;
       border-bottom:1px solid var(--line);display:flex;flex-wrap:wrap;gap:.45rem;align-items:center}
  button{font:inherit;padding:.35rem .8rem;border:1px solid var(--line);border-radius:99px;
         background:var(--card);color:var(--fg);cursor:pointer;font-size:.85rem}
  button.on{background:var(--fg);color:var(--bg);border-color:var(--fg)}
  input{font:inherit;padding:.35rem .8rem;border:1px solid var(--line);border-radius:99px;
        background:var(--card);color:var(--fg);min-width:150px}
  .spacer{flex:1}
  .prog{font-size:.85rem;color:var(--muted);white-space:nowrap}
  .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:1rem}
  .card{margin:0;background:var(--card);border:2px solid var(--line);border-radius:12px;overflow:hidden}
  .card[data-verdict=light]{border-color:var(--light)}
  .card[data-verdict=dark]{border-color:var(--dark)}
  .card[data-verdict=any]{border-color:var(--any)}
  .card[data-verdict=reject]{border-color:var(--reject);opacity:.55}
  .swatches{display:grid;grid-template-columns:1fr 1fr;height:120px}
  .sw{display:flex;align-items:center;justify-content:center;padding:.7rem;position:relative}
  .sw.light{background:#fff}
  .sw.dark{background:#17171c}
  .sw img{max-width:100%;max-height:100%;object-fit:contain}
  .sw::after{position:absolute;bottom:2px;right:5px;font-size:.6rem;opacity:.35}
  .sw.light::after{content:'clair';color:#000}
  .sw.dark::after{content:'sombre';color:#fff}
  figcaption{padding:.5rem .6rem .4rem;border-top:1px solid var(--line)}
  figcaption strong{display:block;font-size:.9rem;line-height:1.3}
  .meta{color:var(--muted);font-size:.72rem}
  .grp{display:block;margin-top:.15rem;font-size:.68rem;color:var(--dark);font-style:italic}
  .verdicts{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;background:var(--line);
            border-top:1px solid var(--line)}
  .v{border:0;border-radius:0;padding:.45rem .1rem;font-size:.67rem;background:var(--card);color:var(--muted)}
  .v:hover{background:var(--bg)}
  .v.sel{color:#fff;font-weight:600}
  .v-light.sel{background:var(--light)}.v-dark.sel{background:var(--dark)}
  .v-any.sel{background:var(--any)}.v-reject.sel{background:var(--reject)}
  .sug{outline:2px dashed var(--muted);outline-offset:-2px}
  .url{width:100%;border:0;border-top:1px solid var(--line);border-radius:0;
       padding:.4rem .6rem;font-size:.72rem;background:var(--card);color:var(--fg);min-width:0}
  .url::placeholder{color:var(--muted);opacity:.7}
  .url:focus{outline:2px solid var(--dark);outline-offset:-2px}
  .card.hasurl{border-color:#8a6d1f}
  .card.hasurl .url{background:#8a6d1f18;font-weight:600}
  .missing{margin-top:2.5rem;padding-top:1.2rem;border-top:1px solid var(--line)}
  .card.miss{border-style:dashed}
  .card.miss.hasurl{border-style:solid}
  .pastille{height:120px;display:flex;align-items:center;justify-content:center;
            font-size:2.4rem;font-weight:700;letter-spacing:.02em;
            background:hsl(var(--h) 45% 42%);color:#fff}
  .site{display:block;margin-top:.2rem;font-size:.72rem;color:var(--dark);text-decoration:none}
  .site:hover{text-decoration:underline}
</style></head><body>
<h1>Pastille d'affichage des logos</h1>
<p class="sub">${withLogo.length} logos · ${new Date().toISOString().slice(0, 10)}</p>

<div class="hint warn" id="warn">
<strong>Page ouverte en <code>file://</code></strong> — l'analyse automatique des couleurs est
bloquée par la sécurité du navigateur. Sers le dossier <code>brands/</code> en HTTP pour
obtenir les suggestions pré-remplies.
</div>

<div class="hint">
Chaque logo est posé sur une pastille <strong>claire</strong> (gauche) puis <strong>sombre</strong>
(droite). Indique celle sur laquelle l'app doit l'afficher — ce choix vaut quel que soit le
thème de l'utilisateur, sinon un logo noir disparaît en thème sombre.
<br><br>
Les suggestions sont <strong>pré-remplies</strong> d'après la luminosité de chaque image et
signalées par un liseré pointillé. Elles ne sont pas enregistrées tant que tu ne cliques pas :
parcours la grille, corrige ce qui est faux, et clique sur ce qui est juste pour le confirmer.
<br><br>
<button id="acceptAll" style="margin-top:.4rem">Accepter toutes les suggestions</button>
<br><br>
<strong>Pour remplacer un logo</strong>, colle l'adresse directe d'une image dans le champ au
bas de sa carte (clic droit sur l'image → « Copier l'adresse de l'image »). Elle sera
téléchargée à l'import. Le filtre <em>À remplacer</em> liste ce que tu as fourni.
</div>

<div class="bar">
  <button class="f on" data-f="all">Tout (${withLogo.length})</button>
  <button class="f" data-f="todo">À trancher</button>
  <button class="f" data-f="light">Fond clair</button>
  <button class="f" data-f="dark">Fond sombre</button>
  <button class="f" data-f="any">Indifférent</button>
  <button class="f" data-f="reject">Rejetés</button>
  <button class="f" data-f="url">À remplacer</button>
  <input id="q" type="search" placeholder="filtrer…">
  <span class="spacer"></span>
  <span class="prog" id="prog"></span>
  <button id="export">⬇ Exporter</button>
  <button id="reset">Réinitialiser</button>
</div>

<div class="grid">${withLogo.map(card).join('')}</div>

<section class="missing">
  <h2>Sans logo — ${missing.length}</h2>
  <p class="sub">Voici ce que l'app affichera par défaut. Colle l'URL d'un logo pour en fournir un :
  le lien ouvre le site officiel de l'enseigne.</p>
  <div class="grid">${missing.map(missingCard).join('')}</div>
</section>

<script>
const KEY='fidcards-fond'
const cards=[...document.querySelectorAll('.card:not(.miss)')]
const missCards=[...document.querySelectorAll('.card.miss')]
let V={}, U={}, SUG={}, kind='all', q=''
// État initial repris du catalogue, pas du navigateur : un logo remplacé depuis le
// dernier export doit repartir sans verdict, pas hériter de celui de l'ancienne image.
const SAVED=__SAVED__
const FILES=Object.fromEntries(cards.map(c=>[c.dataset.id, c.dataset.file]))
// Le catalogue fait foi. Le stockage local ne conserve un verdict que si l'image
// est toujours la même : un logo remplacé depuis doit repartir sans jugement.
try {
  const local=JSON.parse(localStorage.getItem(KEY)||'{}')
  for(const [id, rec] of Object.entries(local)){
    if(SAVED[id] || !FILES[id]) continue
    if(rec && typeof rec==='object' && rec.f===FILES[id]) V[id]=rec.v
  }
  U=JSON.parse(localStorage.getItem(KEY+'-url')||'{}')
} catch {}
Object.assign(V, SAVED)
const persist=()=>{
  const out={}
  for(const [id,v] of Object.entries(V)) out[id]={v, f:FILES[id]}
  try{
    localStorage.setItem(KEY, JSON.stringify(out))
    localStorage.setItem(KEY+'-url', JSON.stringify(U))
  }catch{}
}
persist()

function paint(){
  let done=0
  for(const c of cards){
    const v=V[c.dataset.id]
    if(v){done++; c.dataset.verdict=v} else delete c.dataset.verdict
    const s=SUG[c.dataset.id]
    for(const b of c.querySelectorAll('.v')){
      b.classList.toggle('sel', b.dataset.v===v)
      b.classList.toggle('sug', !v && !!s && b.dataset.v===s)
    }
    const u=U[c.dataset.id]
    c.classList.toggle('hasurl', !!u)
    const inp=c.querySelector('.url')
    if(inp && document.activeElement!==inp) inp.value = u ?? ''
    const okK = kind==='all' ? true : kind==='todo' ? !v : kind==='url' ? !!u : v===kind
    const okQ = !q || c.dataset.name.includes(q)
    c.style.display = okK && okQ ? '' : 'none'
  }
  for(const c of missCards){
    const u=U[c.dataset.id]
    c.classList.toggle('hasurl', !!u)
    const inp=c.querySelector('.url')
    if(inp && document.activeElement!==inp) inp.value = u ?? ''
    // Ces cartes n'ont pas de verdict de pastille : elles ne concernent que « Tout »
    // et « À remplacer ».
    const okK = kind==='all' ? true : kind==='url' ? !!u : false
    const okQ = !q || c.dataset.name.includes(q)
    c.style.display = okK && okQ ? '' : 'none'
  }
  // masquer l'intitulé de la section quand aucune de ses cartes n'est visible
  const sec=document.querySelector('.missing')
  if(sec) sec.style.display = missCards.some(c=>c.style.display!=='none') ? '' : 'none'
  const by={}
  for(const v of Object.values(V)) by[v]=(by[v]||0)+1
  document.getElementById('prog').textContent =
    done+'/'+cards.length+' tranchés'+
    (by.light?' · '+by.light+' clair':'')+(by.dark?' · '+by.dark+' sombre':'')+
    (by.any?' · '+by.any+' indiff.':'')+(by.reject?' · '+by.reject+' rejetés':'')+
    (Object.keys(U).length?' · '+Object.keys(U).length+' URL fournies':'')
}
const save=persist

// --- analyse : luminosité moyenne des pixels opaques + taux de transparence ---
function analyse(img){
  const S=64, cv=document.createElement('canvas'); cv.width=cv.height=S
  const cx=cv.getContext('2d',{willReadFrequently:true})
  cx.clearRect(0,0,S,S); cx.drawImage(img,0,0,S,S)
  let d
  try { d=cx.getImageData(0,0,S,S).data } catch { return null }   // file:// -> SecurityError
  let sum=0, n=0, opaque=0
  for(let i=0;i<d.length;i+=4){
    const a=d[i+3]
    if(a>200) opaque++
    if(a<128) continue
    sum += 0.2126*d[i] + 0.7152*d[i+1] + 0.0722*d[i+2]
    n++
  }
  if(!n) return null
  return { lum: sum/n/255, fill: opaque/(S*S) }
}

function suggest(a){
  if(!a) return null
  if(a.fill>0.92) return 'any'        // fond opaque : la pastille ne se voit pas
  if(a.lum<0.45)  return 'light'      // logo sombre -> pastille claire
  if(a.lum>0.62)  return 'dark'       // logo clair  -> pastille sombre
  return 'any'                         // logo coloré, contrasté des deux côtés
}

let blocked=false
async function analyseAll(){
  for(const c of cards){
    const img=c.querySelector('.sw.light img')
    const done = img.complete && img.naturalWidth
      ? Promise.resolve()
      : new Promise(r=>{ img.onload=r; img.onerror=r })
    await done
    const a = img.naturalWidth ? analyse(img) : null
    if(a===null && img.naturalWidth) blocked=true
    const s=suggest(a)
    if(s) SUG[c.dataset.id]=s
    c.querySelector('.an').textContent = a
      ? 'lum '+Math.round(a.lum*100)+'% · rempli '+Math.round(a.fill*100)+'%'
      : 'non analysé'
  }
  if(blocked) document.getElementById('warn').style.display='block'
  paint()
}

document.addEventListener('click', e=>{
  const btn=e.target.closest('.v'); if(!btn) return
  const id=btn.closest('.card').dataset.id
  V[id] = V[id]===btn.dataset.v ? undefined : btn.dataset.v
  if(!V[id]) delete V[id]
  save(); paint()
})
document.getElementById('acceptAll').onclick=()=>{
  let n=0
  for(const c of cards){ const id=c.dataset.id; if(!V[id] && SUG[id]){ V[id]=SUG[id]; n++ } }
  save(); paint()
  alert(n+' suggestions acceptées. Corrige ensuite ce qui est faux.')
}
document.addEventListener('input', e=>{
  const inp=e.target.closest('.url'); if(!inp) return
  const id=inp.closest('.card').dataset.id
  const val=inp.value.trim()
  if(val) U[id]=val; else delete U[id]
  inp.closest('.card').classList.toggle('hasurl', !!val)
  persist()
})
document.querySelectorAll('.f').forEach(b=>b.onclick=()=>{
  document.querySelectorAll('.f').forEach(x=>x.classList.remove('on'))
  b.classList.add('on'); kind=b.dataset.f; paint()
})
document.getElementById('q').oninput=e=>{q=e.target.value.toLowerCase().trim(); paint()}
document.getElementById('reset').onclick=()=>{ if(confirm('Effacer tous les choix ?')){ V={}; U={}; save(); paint() } }
document.getElementById('export').onclick=()=>{
  const a=document.createElement('a')
  a.href=URL.createObjectURL(new Blob([JSON.stringify({generated:new Date().toISOString(),background:V,replacements:U},null,2)],{type:'application/json'}))
  a.download='fidcards-fonds.json'; a.click()
}
paint(); analyseAll()
</script>
</body></html>`

const saved = Object.fromEntries(
  withLogo.filter(b => b.background).map(b => [b.id, b.background]))

writeFileSync(join(ROOT, 'brands', 'planche.html'), html.replace('__SAVED__', JSON.stringify(saved)))
console.log(`planche.html : ${withLogo.length} logos, ${missing.length} sans logo`)
console.log('À servir en HTTP pour que l\'analyse automatique fonctionne.')
