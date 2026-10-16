'use strict';
/**
 * Les textes des fiches de magasin, par langue de l'application, pour TOUS
 * les magasins a la fois (Play, App Store, Microsoft Store, AppGallery,
 * GetApps). Une seule verite, un fichier par langue dans stores/listings/.
 *
 * Champs, et limites verifiees par stores/generate.js :
 *
 *   title       30   nom de l'application (Play title, App Store name)
 *   subtitle    30   sous-titre (App Store ; Play n'en a pas)
 *   short       80   phrase d'accroche (Play short description, et
 *                    promotional_text App Store, limite 170 la-bas)
 *   full      4000   description complete (tous magasins)
 *   keywords   100   mots-cles separes par des virgules (App Store)
 *   whatsNew   500   notes de version (Play changelog, App Store release notes)
 *
 * Le nom de l'application n'est JAMAIS ecrit en dur dans les textes : le
 * jeton {app} est remplace par APP_NAME a la generation, pour que le
 * choix definitif du nom soit une seule ligne a changer.
 *
 * Interdits dans chaque langue, parce qu'Apple (directive 2.3.10) et nous
 * les refusons : le nom d'un autre magasin, d'une autre plateforme ou d'un
 * concurrent. La liste INTERDITS de stores/generate.js est verifiee sur
 * chaque champ, graphies locales comprises.
 */
const fs = require('node:fs');
const path = require('node:path');

/** Nom affiche de l'application, un seul endroit a changer. */
const APP_NAME = 'Kilomètres';

const DIR = path.join(__dirname, 'listings');
const L = {};
for (const file of fs.readdirSync(DIR)) {
	if (!file.endsWith('.js')) continue;
	L[path.basename(file, '.js')] = require(path.join(DIR, file));
}

module.exports = { APP_NAME, listings: L };
