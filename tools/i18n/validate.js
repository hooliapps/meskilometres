'use strict';
/**
 * Verifie une ou plusieurs langues sans rien ecrire :
 *   node tools/i18n/validate.js it de es
 * Memes regles que generate.js : memes cles que en.js, memes parametres
 * (%1$s, %1$d) dans chaque chaine, aucune cle inconnue.
 */
const path = require('node:path');
const DIR = path.join(__dirname, 'strings');
const EN = require(path.join(DIR, 'en.js'));
const { FORBIDDEN, mentions } = require(path.join(__dirname, '..', '..', 'stores', 'rules.js'));
const KEYS = Object.keys(EN);
const langs = process.argv.slice(2);
if (!langs.length) { console.error('usage : node tools/i18n/validate.js <lang>...'); process.exit(2); }
let failed = false;
for (const lang of langs) {
	const errors = [];
	let table;
	try { table = require(path.join(DIR, `${lang}.js`)); } catch (e) { errors.push(`fichier illisible : ${e.message}`); }
	if (table) {
		const missing = KEYS.filter((k) => typeof table[k] !== 'string');
		const extra = Object.keys(table).filter((k) => !KEYS.includes(k));
		if (missing.length) errors.push(`cles manquantes : ${missing.join(', ')}`);
		if (extra.length) errors.push(`cles inconnues : ${extra.join(', ')}`);
		for (const k of KEYS) {
			if (typeof table[k] !== 'string') continue;
			const want = (EN[k].match(/%\d\$[sd]/g) || []).sort().join(' ');
			const have = (table[k].match(/%\d\$[sd]/g) || []).sort().join(' ');
			if (want !== have) errors.push(`${k} : parametres ${have || '(aucun)'} au lieu de ${want || '(aucun)'}`);
			if (!table[k].trim()) errors.push(`${k} : vide`);
			for (const w of FORBIDDEN) if (mentions(table[k], w)) errors.push(`${k} : cite « ${w} »`);
			if (have && /%(?!\d\$[sd])/.test(table[k])) errors.push(`${k} : un « % » litteral dans une chaine a parametres (ecrire « pour cent » ou %%)`);
			if (lang !== 'en' && k !== 'app_name' && table[k] === EN[k] && EN[k].length > 12 && !/^%/.test(EN[k])) errors.push(`${k} : identique a l'anglais (non traduit ?)`);
		}
	}
	if (errors.length) { failed = true; console.error(`${lang} : KO\n  ${errors.join('\n  ')}`); }
	else console.log(`${lang} : OK (${KEYS.length} cles)`);
}
process.exit(failed ? 1 : 0);
