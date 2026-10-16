/**
 * Icones et visuels de magasin de l'application, dessines en HTML/CSS et
 * rendus par Chromium sans interface. Aucune image source : des formes,
 * donc un rendu net a toutes les tailles et un dessin qu'on modifie en
 * changeant trois nombres. Le dessin est CELUI du vecteur Android
 * (androidApp/src/main/res/drawable/ic_launcher_foreground.xml) : une
 * route qui monte vers l'horizon, en une seule ligne.
 *
 *   npm install --prefix tools/branding          (une fois)
 *   node tools/branding/render.js                ecrit tout
 *   CHROME=/chemin/vers/chrome node tools/branding/render.js
 *
 * Ce que produit le script :
 *   tools/branding/out/icon-512.png                   icone de la fiche Play / AppGallery / GetApps
 *   tools/branding/out/feature-1024x500.png           image de presentation Play
 *   tools/branding/out/store-300x300.png              tuile Microsoft Store
 *   iosApp/Kilometres/Resources/Assets.xcassets/AppIcon.appiconset/icon-1024.png
 *   iosApp/KilometresWatch/Assets.xcassets/AppIcon.appiconset/icon-1024.png
 *   desktopApp/src/main/resources/icon.ico            icone Windows (16..256, PNG dans l'ICO)
 *   desktopApp/src/main/resources/icon-256.png        icone de la fenetre
 *
 * Android n'a besoin d'AUCUN PNG : l'icone adaptative est un vecteur, et le
 * minSdk 26 n'a pas de repli a fournir.
 *
 * Deux exigences d'Apple pour icon-1024.png, verifiees ici : PAS DE CANAL
 * ALPHA (App Store Connect refuse l'envoi) et PAS DE COINS ARRONDIS (iOS
 * applique son propre masque). Le PNG est reecrit en RGB pur, et un pixel
 * non opaque fait echouer le script.
 */
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

const HERE = __dirname;
const ROOT = path.join(HERE, '..', '..');
const OUT = path.join(HERE, 'out');

/* -------------------------------------------------------------- Couleurs */

const BACKGROUND = '#1F6F5F';     // le vert profond de ic_launcher_background
const BACKGROUND_DEEP = '#15534A';
const ROAD = '#FFFFFF';
const DASH = '#F4EFE6';

/* ------------------------------------------------------------------ Dessin */

/**
 * La route, dans un carre de 108 unites (la zone de premier plan adaptative
 * Android) : memes coordonnees que le vecteur ic_launcher_foreground.xml,
 * pour que tous les rendus restent superposables.
 */
function road(u, { scale = 1 } = {}) {
	const p = (pts) => pts.map(([x, y]) => `${(54 + (x - 54) * scale) * u},${(54 + (y - 54) * scale) * u}`).join(' ');
	return `<svg xmlns="http://www.w3.org/2000/svg" width="${108 * u}" height="${108 * u}" viewBox="0 0 ${108 * u} ${108 * u}" style="position:absolute;left:0;top:0">
		<polygon points="${p([[30, 84], [47, 30], [61, 30], [78, 84], [68, 84], [56, 44], [52, 44], [40, 84]])}" fill="${ROAD}" fill-opacity="0.92"/>
		<polygon points="${p([[52.5, 52], [55.5, 52], [56.5, 60], [51.5, 60]])}" fill="${DASH}"/>
		<polygon points="${p([[50.5, 66], [57.5, 66], [58.5, 74], [49.5, 74]])}" fill="${DASH}"/>
	</svg>`;
}

function page(w, h, body, background) {
	return `<!doctype html><html><head><meta charset="utf-8"><style>
		html,body{margin:0;padding:0;width:${w}px;height:${h}px;overflow:hidden;background:${background}}
		</style></head><body>${body}</body></html>`;
}

/** Icone carree pleine : fond vert, route agrandie (les magasins et iOS masquent eux-memes les coins). */
function squareIcon(size, scale = 1.25) {
	const u = size / 108;
	const bg = `radial-gradient(circle at 50% 35%, #2A8A76 0%, ${BACKGROUND} 55%, ${BACKGROUND_DEEP} 100%)`;
	return page(size, size, road(u, { scale }), bg);
}

/** Image de presentation Play, 1024 x 500 : la route a droite, le nom a gauche. Rien d'essentiel pres des bords. */
function feature(w, h, name, tagline) {
	const u = h / 108;
	return page(w, h, `
		<div style="position:absolute;right:${-0.02 * w}px;top:0;width:${h}px;height:${h}px">${road(u, { scale: 1.15 })}</div>
		<div style="position:absolute;left:${0.07 * w}px;top:${0.30 * h}px;color:#fff;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif">
			<div style="font-size:${0.19 * h}px;font-weight:700;line-height:1;letter-spacing:-0.01em">${name}</div>
			<div style="width:${0.08 * w}px;height:${0.012 * h}px;background:#fff;opacity:.6;margin:${0.05 * h}px 0"></div>
			<div style="font-size:${0.07 * h}px;opacity:.92;line-height:1.3;max-width:${0.5 * w}px">${tagline}</div>
		</div>`,
	`linear-gradient(120deg, #2A8A76 0%, ${BACKGROUND} 50%, ${BACKGROUND_DEEP} 100%)`);
}

/* -------------------------------------------------------- PNG sans alpha */

function chunk(type, data) {
	const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
	const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
	const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body) >>> 0);
	return Buffer.concat([len, body, crc]);
}
const CRC_TABLE = (() => { const t = new Int32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c; } return t; })();
function crc32(buf) { let c = -1; for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8); return ~c; }
function predict(filter, a, b, c) {
	switch (filter) {
		case 0: return 0; case 1: return a; case 2: return b; case 3: return (a + b) >> 1;
		case 4: { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); return pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
		default: throw new Error(`filtre PNG ${filter} inconnu`);
	}
}

/** Reecrit un PNG RGBA 8 bits en RGB 8 bits, et refuse tout pixel non opaque. */
function withoutAlpha(png) {
	const SIG = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
	if (!png.subarray(0, 8).equals(SIG)) throw new Error('capture : pas un PNG');
	let ihdr = null, trns = false; const idat = [];
	for (let pos = 8; pos < png.length;) {
		const len = png.readUInt32BE(pos), type = png.toString('ascii', pos + 4, pos + 8), data = png.subarray(pos + 8, pos + 8 + len);
		if (type === 'IHDR') ihdr = data; else if (type === 'IDAT') idat.push(data); else if (type === 'tRNS') trns = true; else if (type === 'IEND') break;
		pos += 12 + len;
	}
	if (!ihdr) throw new Error('capture : IHDR absent');
	const w = ihdr.readUInt32BE(0), h = ihdr.readUInt32BE(4), depth = ihdr[8], color = ihdr[9], interlace = ihdr[12];
	if (depth !== 8 || interlace !== 0) throw new Error(`capture : PNG ${depth} bits / entrelacement ${interlace} non pris en charge`);
	if (color === 2) { if (trns) throw new Error('capture RGB avec tRNS'); return png; }
	if (color !== 6) throw new Error(`capture : type de couleur ${color} non pris en charge`);
	const raw = zlib.inflateSync(Buffer.concat(idat));
	const bpp = 4, stride = w * bpp, rgba = Buffer.alloc(h * stride);
	for (let y = 0; y < h; y++) {
		const f = raw[y * (stride + 1)], line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1)), o = y * stride;
		for (let x = 0; x < stride; x++) {
			const a = x >= bpp ? rgba[o + x - bpp] : 0, b = y > 0 ? rgba[o - stride + x] : 0, c = x >= bpp && y > 0 ? rgba[o - stride + x - bpp] : 0;
			rgba[o + x] = (line[x] + predict(f, a, b, c)) & 0xff;
		}
	}
	const rs = w * 3, rgb = Buffer.alloc(h * rs);
	for (let i = 0, j = 0; i < rgba.length; i += 4, j += 3) {
		if (rgba[i + 3] !== 255) { const p = i / 4; throw new Error(`pixel (${p % w}, ${Math.floor(p / w)}) non opaque : le fond n'est pas plein`); }
		rgb[j] = rgba[i]; rgb[j + 1] = rgba[i + 1]; rgb[j + 2] = rgba[i + 2];
	}
	const filtered = Buffer.alloc(h * (rs + 1)), trial = Buffer.alloc(rs);
	for (let y = 0; y < h; y++) {
		const o = y * rs; let best = 0, bestCost = Infinity, bestLine = null;
		for (let f = 0; f <= 4; f++) {
			let cost = 0;
			for (let x = 0; x < rs; x++) {
				const a = x >= 3 ? rgb[o + x - 3] : 0, b = y > 0 ? rgb[o - rs + x] : 0, c = x >= 3 && y > 0 ? rgb[o - rs + x - 3] : 0;
				const v = (rgb[o + x] - predict(f, a, b, c)) & 0xff; trial[x] = v; cost += v < 128 ? v : 256 - v;
			}
			if (cost < bestCost) { best = f; bestCost = cost; bestLine = Buffer.from(trial); }
		}
		filtered[y * (rs + 1)] = best; bestLine.copy(filtered, y * (rs + 1) + 1);
	}
	const header = Buffer.from(ihdr); header[9] = 2;
	return Buffer.concat([SIG, chunk('IHDR', header), chunk('IDAT', zlib.deflateSync(filtered, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

/* ------------------------------------------------------------------ ICO */

/** Un fichier ICO Windows dont chaque image est un PNG (lu depuis Vista). */
function ico(pngs) {
	const header = Buffer.alloc(6); header.writeUInt16LE(0, 0); header.writeUInt16LE(1, 2); header.writeUInt16LE(pngs.length, 4);
	const entries = []; let offset = 6 + 16 * pngs.length;
	for (const { size, data } of pngs) {
		const e = Buffer.alloc(16);
		e[0] = size >= 256 ? 0 : size; e[1] = size >= 256 ? 0 : size; e[2] = 0; e[3] = 0;
		e.writeUInt16LE(1, 4); e.writeUInt16LE(32, 6); e.writeUInt32LE(data.length, 8); e.writeUInt32LE(offset, 12);
		entries.push(e); offset += data.length;
	}
	return Buffer.concat([header, ...entries, ...pngs.map((p) => p.data)]);
}

/* ------------------------------------------------------------------- Rendu */

const { chromium } = require('playwright-core');

function write(file, content) { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, content); }

async function shot(page, html, w, h) {
	await page.setViewportSize({ width: w, height: h });
	await page.setContent(html, { waitUntil: 'load' });
	return page.screenshot({ type: 'png', clip: { x: 0, y: 0, width: w, height: h } });
}

(async () => {
	const executablePath = process.env.CHROME || (fs.existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined);
	const browser = await chromium.launch(executablePath ? { executablePath, args: ['--no-sandbox', '--disable-gpu'] } : { channel: 'chrome' });
	const page = await browser.newPage({ deviceScaleFactor: 1 });

	// Magasins (Play, AppGallery, GetApps) : 512 x 512, fond plein.
	write(path.join(OUT, 'icon-512.png'), await shot(page, squareIcon(512), 512, 512));
	// Microsoft Store : tuile 300 x 300.
	write(path.join(OUT, 'store-300x300.png'), await shot(page, squareIcon(300), 300, 300));
	// Image de presentation Play.
	write(path.join(OUT, 'feature-1024x500.png'), await shot(page, feature(1024, 500, 'Kilomètres', 'Carnet de kilométrage<br>et frais réels'), 1024, 500));

	// iOS et watchOS : 1024 x 1024, RGB sans alpha, sans coins arrondis.
	const ios = withoutAlpha(await shot(page, squareIcon(1024), 1024, 1024));
	for (const target of [['Kilometres', 'Resources'], ['KilometresWatch']]) {
		const set = path.join(ROOT, 'iosApp', ...target, 'Assets.xcassets', 'AppIcon.appiconset');
		write(path.join(set, 'icon-1024.png'), ios);
		write(path.join(set, 'Contents.json'), JSON.stringify({
			images: [{ filename: 'icon-1024.png', idiom: 'universal', platform: target[0] === 'KilometresWatch' ? 'watchos' : 'ios', size: '1024x1024' }],
			info: { author: 'xcode', version: 1 }
		}, null, 2) + '\n');
		const catalog = path.join(ROOT, 'iosApp', ...target, 'Assets.xcassets', 'Contents.json');
		if (!fs.existsSync(catalog)) write(catalog, JSON.stringify({ info: { author: 'xcode', version: 1 } }, null, 2) + '\n');
	}

	// Windows : ICO multi-tailles + PNG de fenetre.
	const sizes = [256, 128, 64, 48, 32, 16];
	const pngs = [];
	for (const s of sizes) pngs.push({ size: s, data: await shot(page, squareIcon(s, 1.35), s, s) });
	write(path.join(ROOT, 'desktopApp', 'src', 'main', 'resources', 'icon.ico'), ico(pngs));
	write(path.join(ROOT, 'desktopApp', 'src', 'main', 'resources', 'icon-256.png'), pngs[0].data);

	await browser.close();
	console.log('icones ecrites : Play 512, Microsoft 300, presentation 1024x500, iOS/watchOS 1024 (RGB), Windows ICO');
})().catch((e) => { console.error(e); process.exit(1); });
