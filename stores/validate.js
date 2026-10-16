'use strict';
/**
 * Verifie une ou plusieurs fiches sans rien ecrire :
 *   node stores/validate.js it de es
 * Memes champs que en.js, memes jetons {app} {monthly} {yearly}, limites de
 * longueur des magasins, et aucun nom de plateforme, de magasin ou de
 * concurrent (liste INTERDITS de stores/generate.js).
 */
const path = require('node:path');
const { LIMITS, FORBIDDEN, check } = require('./rules.js');
const DIR = path.join(__dirname, 'listings');
const EN = require(path.join(DIR, 'en.js'));
const langs = process.argv.slice(2);
if (!langs.length) { console.error('usage : node stores/validate.js <lang>...'); process.exit(2); }
let failed = false;
for (const lang of langs) {
	let errors = [];
	try { errors = check(lang, require(path.join(DIR, `${lang}.js`)), EN); } catch (e) { errors.push(`fichier illisible : ${e.message}`); }
	if (errors.length) { failed = true; console.error(`${lang} : KO\n  ${errors.join('\n  ')}`); }
	else console.log(`${lang} : OK`);
}
void LIMITS; void FORBIDDEN;
process.exit(failed ? 1 : 0);
