#!/usr/bin/env node
// Fusionne la colonne primary_color d'un seed CSV dans brands/brands.json
// (champ `color`, hex #RRGGBB), par correspondance de nom normalisé.
// Usage : node tools/apply-primary-colors.mjs <seed.csv>
// Rejouable ; pense ensuite à recopier brands.json vers app/src/main/assets/.

import { readFileSync, writeFileSync } from "node:fs";

const csvPath = process.argv[2];
if (!csvPath) {
  console.error("Usage : node tools/apply-primary-colors.mjs <seed.csv>");
  process.exit(1);
}

function parseCsv(text) {
  const rows = [];
  let row = [], field = "", inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') { field += '"'; i++; } else inQuotes = false;
      } else field += c;
    } else if (c === '"') inQuotes = true;
    else if (c === ",") { row.push(field); field = ""; }
    else if (c === "\n" || c === "\r") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      row.push(field); field = "";
      if (row.some((f) => f !== "")) rows.push(row);
      row = [];
    } else field += c;
  }
  if (field !== "" || row.length) { row.push(field); rows.push(row); }
  return rows;
}

const normalize = (s) =>
  s.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().replace(/[^a-z0-9]/g, "");

const rows = parseCsv(readFileSync(csvPath, "utf8"));
const header = rows.shift();
const nameIdx = header.indexOf("name");
const colorIdx = header.indexOf("primary_color");
if (nameIdx < 0 || colorIdx < 0) {
  console.error("Colonnes name/primary_color introuvables dans", header);
  process.exit(1);
}

const colors = new Map();
for (const r of rows) {
  const color = (r[colorIdx] ?? "").trim();
  if (/^#[0-9a-fA-F]{6}$/.test(color)) colors.set(normalize(r[nameIdx]), color.toUpperCase());
}

const brandsPath = new URL("../brands/brands.json", import.meta.url).pathname;
const brands = JSON.parse(readFileSync(brandsPath, "utf8"));

let applied = 0;
const unmatched = [];
for (const b of brands) {
  const color = colors.get(normalize(b.name));
  if (color) { b.color = color; applied++; } else unmatched.push(b.name);
}

writeFileSync(brandsPath, JSON.stringify(brands, null, 2) + "\n");
console.log(`${applied}/${brands.length} enseignes colorées (CSV : ${colors.size} couleurs).`);
if (unmatched.length) console.log("Sans couleur :", unmatched.join(", "));
