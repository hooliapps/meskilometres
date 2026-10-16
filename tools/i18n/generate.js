'use strict';
/**
 * Genere les ressources de chaines de TOUTES les cibles depuis UNE source :
 * tools/i18n/strings/<langue>.js, un fichier par langue, l'anglais faisant
 * reference.
 *
 *   node tools/i18n/generate.js            ecrit tout
 *   node tools/i18n/generate.js --check    verifie sans ecrire
 *
 * Sorties :
 *   androidApp/src/main/res/values[-xx]/strings.xml       (Android, en = values)
 *   wearApp/src/main/res/values[-xx]/strings.xml          (sous-ensemble montre)
 *   iosApp/Kilometres/Resources/<xx>.lproj/Localizable.strings
 *   iosApp/Kilometres/Resources/<xx>.lproj/InfoPlist.strings   (nom sous l'icone)
 *   desktopApp/src/main/resources/i18n/strings_<xx>.properties
 *
 * Conversions :
 *   - dossiers Android : iw (hebreu), in (indonesien), zh-rTW ; iOS suit BCP 47
 *     (he, id, zh-Hans, zh-Hant), le bureau suit les codes iOS ;
 *   - parametres : %1$s -> %1$@ et %1$d -> %1$ld pour iOS, inchanges ailleurs.
 *
 * Une langue a qui il manque une cle fait echouer le script : la parite est
 * garantie par construction.
 */
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..', '..');
const CHECK = process.argv.includes('--check');
const DIR = path.join(__dirname, 'strings');

/** Codes de langue, dans l'ordre des fiches Play. en = valeurs par defaut Android. */
const LANGS = ['en', 'fr', 'it', 'de', 'es', 'pt', 'ru', 'zh', 'ja', 'hi', 'ar', 'vi', 'hu',
	'ca', 'cs', 'da', 'el', 'fi', 'iw', 'hr', 'in', 'ko', 'ms', 'nb', 'nl', 'pl', 'ro', 'sk', 'sv', 'th', 'tr', 'uk', 'zh-TW',
	'af', 'am', 'az', 'be', 'bg', 'bn', 'et', 'eu', 'fa', 'fil', 'gl', 'gu', 'hy', 'is', 'ka', 'kk', 'km', 'kn',
	'ky', 'lo', 'lt', 'lv', 'mk', 'ml', 'mn', 'mr', 'my', 'ne', 'pa', 'rm', 'si', 'sl', 'sr', 'sw', 'ta', 'te', 'ur', 'zu'];

/** Cles de la montre : un sous-ensemble, pour ne pas gonfler l'application Wear OS. */
const WEAR_KEYS = ['app_name', 'watch_quick_add', 'watch_no_favorites', 'watch_added', 'watch_open_phone', 'km_value', 'this_month', 'today', 'pro_badge', 'widget_pro_needed'];

const androidFolder = (l) => l === 'en' ? 'values' : `values-${l.replace('-', '-r')}`;
const iosCode = (l) => ({ iw: 'he', in: 'id', zh: 'zh-Hans', 'zh-TW': 'zh-Hant' }[l] || l);

function load(lang) {
	const file = path.join(DIR, `${lang}.js`);
	if (!fs.existsSync(file)) throw new Error(`langue manquante : ${lang} (${file})`);
	delete require.cache[require.resolve(file)];
	return require(file);
}

const { FORBIDDEN, mentions } = require(path.join(ROOT, 'stores', 'rules.js'));
const EN = load('en');
const KEYS = Object.keys(EN);

function androidEscape(v) {
	return v.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '\\"').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '\\n');
}

function androidXml(table, keys) {
	const lines = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- GENERE par tools/i18n/generate.js : ne pas modifier a la main. -->', '<resources>'];
	for (const k of keys) {
		const v = table[k];
		const attrs = /%\d\$[sd]/.test(v) ? '' : ' formatted="false"';
		lines.push(`    <string name="${k}"${attrs}>${androidEscape(v)}</string>`);
	}
	lines.push('</resources>', '');
	return lines.join('\n');
}

function iosStrings(table) {
	const lines = ['/* GENERE par tools/i18n/generate.js : ne pas modifier a la main. */'];
	for (const k of KEYS) {
		const v = table[k].replace(/%(\d)\$s/g, '%$1$@').replace(/%(\d)\$d/g, '%$1$ld').replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n');
		lines.push(`"${k}" = "${v}";`);
	}
	lines.push('');
	return lines.join('\n');
}

/** Le nom affiche sous l'icone, par langue : iOS le lit dans InfoPlist.strings, pas dans Localizable.strings. */
function infoPlistStrings(table) {
	const name = table.app_name.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
	return ['/* GENERE par tools/i18n/generate.js : ne pas modifier a la main. */', `"CFBundleDisplayName" = "${name}";`, `"CFBundleName" = "${name}";`, ''].join('\n');
}

function properties(table) {
	const lines = ['# GENERE par tools/i18n/generate.js : ne pas modifier a la main.'];
	for (const k of KEYS) {
		const v = table[k].replace(/\\/g, '\\\\').replace(/\n/g, '\\n').replace(/^\s/, '\\ ');
		lines.push(`${k}=${v}`);
	}
	lines.push('');
	return lines.join('\n');
}

function emit(file, content) {
	if (CHECK) {
		const current = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null;
		if (current !== content) {
			console.error(`obsolete : ${path.relative(ROOT, file)}`);
			process.exitCode = 1;
		}
		return;
	}
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, content);
}

// I18N_PARTIAL=1 : pendant le developpement, n'ecrit que les langues deja
// traduites. La verification complete reste la regle avant toute publication.
const PARTIAL = process.env.I18N_PARTIAL === '1';
let count = 0;
for (const lang of LANGS) {
	if (PARTIAL && !fs.existsSync(path.join(DIR, `${lang}.js`))) continue;
	const table = load(lang);
	const missing = KEYS.filter((k) => typeof table[k] !== 'string');
	const extra = Object.keys(table).filter((k) => !KEYS.includes(k));
	if (missing.length) throw new Error(`${lang} : cles manquantes : ${missing.join(', ')}`);
	if (extra.length) throw new Error(`${lang} : cles inconnues : ${extra.join(', ')}`);
	for (const k of KEYS) {
		const want = (EN[k].match(/%\d\$[sd]/g) || []).sort().join(' ');
		const have = (table[k].match(/%\d\$[sd]/g) || []).sort().join(' ');
		if (want !== have) throw new Error(`${lang}.${k} : parametres ${have || '(aucun)'} au lieu de ${want || '(aucun)'}`);
		if (have && /%(?!\d\$[sd])/.test(table[k])) throw new Error(`${lang}.${k} : un « % » litteral dans une chaine a parametres`);
		for (const w of FORBIDDEN) if (mentions(table[k], w)) throw new Error(`${lang}.${k} : cite « ${w} » (plateforme, magasin ou concurrent)`);
	}
	emit(path.join(ROOT, 'androidApp', 'src', 'main', 'res', androidFolder(lang), 'strings.xml'), androidXml(table, KEYS));
	emit(path.join(ROOT, 'wearApp', 'src', 'main', 'res', androidFolder(lang), 'strings.xml'), androidXml(table, WEAR_KEYS));
	emit(path.join(ROOT, 'iosApp', 'Kilometres', 'Resources', `${iosCode(lang)}.lproj`, 'Localizable.strings'), iosStrings(table));
	emit(path.join(ROOT, 'iosApp', 'Kilometres', 'Resources', `${iosCode(lang)}.lproj`, 'InfoPlist.strings'), infoPlistStrings(table));
	emit(path.join(ROOT, 'desktopApp', 'src', 'main', 'resources', 'i18n', `strings_${iosCode(lang)}.properties`), properties(table));
	count++;
}
console.log(`${count} langues, ${KEYS.length} cles${CHECK ? ' verifiees' : ' ecrites'}`);
