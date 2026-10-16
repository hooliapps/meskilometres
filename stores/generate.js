'use strict';
/**
 * Ecrit les fiches de TOUS les magasins depuis stores/listings/<lang>.js.
 *
 *   node stores/generate.js            ecrit tout
 *   node stores/generate.js --check    verifie sans ecrire
 *   STORES_PARTIAL=1                   n'exige pas toutes les langues (developpement)
 *
 * Sorties :
 *   fastlane/metadata/android/<locale>/title.txt, short_description.txt,
 *       full_description.txt, changelogs/<versionCode>.txt       (fastlane supply)
 *   fastlane/metadata/ios/<locale>/name.txt, subtitle.txt, description.txt,
 *       keywords.txt, promotional_text.txt, release_notes.txt,
 *       privacy_url.txt, support_url.txt                       (fastlane deliver)
 *   stores/out/microsoft/<lang>.md, appgallery/<lang>.md, getapps/<lang>.md
 *       texte a coller dans Partner Center, AppGallery Connect et la console
 *       GetApps, qui n'ont pas d'outil de depot en masse.
 *
 * Le numero de version Android est LU dans androidApp/build.gradle.kts : un
 * journal des modifications d'une version qui n'existe pas est rejete par
 * l'API Play.
 */
const fs = require('node:fs');
const path = require('node:path');
const { APP_NAME, listings } = require('./listing.js');
const { LIMITS, fill, check } = require('./rules.js');
const PRICES = require('./prices.js');

const ROOT = path.join(__dirname, '..');
const CHECK = process.argv.includes('--check');
const PARTIAL = process.env.STORES_PARTIAL === '1';
const EN = listings.en;

/** Adresses de la politique de confidentialite et du support (a mettre en ligne avant l'envoi). */
const PRIVACY_URL = 'https://hooliapps.com/kilometres/privacy';
const SUPPORT_URL = 'https://hooliapps.com/kilometres';
const COPYRIGHT = '2026 HooliApps';

/** Locale Play Console -> langue de stores/listings. Liste des locales de fiche acceptees par la console. */
const PLAY = {
	af: 'af', am: 'am', ar: 'ar', 'hy-AM': 'hy', 'az-AZ': 'az',
	'bn-BD': 'bn', 'eu-ES': 'eu', be: 'be', bg: 'bg', 'my-MM': 'my',
	ca: 'ca', 'zh-HK': 'zh-TW', 'zh-CN': 'zh', 'zh-TW': 'zh-TW', hr: 'hr',
	'cs-CZ': 'cs', 'da-DK': 'da', 'nl-NL': 'nl',
	'en-AU': 'en', 'en-CA': 'en', 'en-GB': 'en', 'en-IN': 'en', 'en-SG': 'en', 'en-US': 'en', 'en-ZA': 'en',
	et: 'et', fil: 'fil', 'fi-FI': 'fi', 'fr-CA': 'fr', 'fr-FR': 'fr',
	'gl-ES': 'gl', 'ka-GE': 'ka', 'de-DE': 'de', 'el-GR': 'el', gu: 'gu',
	'iw-IL': 'iw', 'hi-IN': 'hi', 'hu-HU': 'hu', 'is-IS': 'is', id: 'in',
	'it-IT': 'it', 'ja-JP': 'ja', 'kn-IN': 'kn', kk: 'kk', 'km-KH': 'km',
	'ko-KR': 'ko', 'ky-KG': 'ky', 'lo-LA': 'lo', lv: 'lv', lt: 'lt',
	'mk-MK': 'mk', ms: 'ms', 'ms-MY': 'ms', 'ml-IN': 'ml', 'mr-IN': 'mr',
	'mn-MN': 'mn', 'ne-NP': 'ne', 'no-NO': 'nb',
	fa: 'fa', 'fa-AE': 'fa', 'fa-AF': 'fa', 'fa-IR': 'fa',
	'pl-PL': 'pl', 'pt-BR': 'pt', 'pt-PT': 'pt', pa: 'pa', ro: 'ro',
	rm: 'rm', 'ru-RU': 'ru', sr: 'sr', 'si-LK': 'si', sk: 'sk', sl: 'sl',
	'es-419': 'es', 'es-ES': 'es', 'es-US': 'es',
	sw: 'sw', 'sv-SE': 'sv', 'ta-IN': 'ta', 'te-IN': 'te', th: 'th',
	'tr-TR': 'tr', uk: 'uk', ur: 'ur', vi: 'vi', zu: 'zu'
};

/** Locale App Store Connect (reconnue par fastlane deliver) -> langue. */
const APPSTORE = {
	'ar-SA': 'ar', ca: 'ca', cs: 'cs', da: 'da', 'de-DE': 'de', el: 'el',
	'en-AU': 'en', 'en-CA': 'en', 'en-GB': 'en', 'en-US': 'en',
	'es-ES': 'es', 'es-MX': 'es', fi: 'fi', 'fr-CA': 'fr', 'fr-FR': 'fr',
	he: 'iw', hi: 'hi', hr: 'hr', hu: 'hu', id: 'in', it: 'it', ja: 'ja',
	ko: 'ko', ms: 'ms', 'nl-NL': 'nl', no: 'nb', pl: 'pl',
	'pt-BR': 'pt', 'pt-PT': 'pt', ro: 'ro', ru: 'ru', sk: 'sk', sv: 'sv',
	th: 'th', tr: 'tr', uk: 'uk', vi: 'vi', 'zh-Hans': 'zh', 'zh-Hant': 'zh-TW'
};

function read(file, re, what) {
	const m = re.exec(fs.readFileSync(path.join(ROOT, file), 'utf8'));
	if (!m) throw new Error(`${what} introuvable dans ${file}`);
	return m[1];
}
const VERSION_CODE = read('androidApp/build.gradle.kts', /versionCode\s*=\s*(\d+)/, 'versionCode');

let failed = false;
function emit(file, text) {
	const content = text.trimEnd() + '\n';
	if (CHECK) {
		const current = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null;
		if (current !== content) { console.error(`obsolete : ${path.relative(ROOT, file)}`); failed = true; }
		return;
	}
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, content);
}

function texts(lang, locale) {
	const t = listings[lang];
	if (!t) return null;
	const p = PRICES.byLocale[locale] || PRICES.DEFAULT;
	const values = { '{app}': APP_NAME, '{monthly}': p.monthly, '{yearly}': p.yearly };
	const out = {};
	for (const f of Object.keys(LIMITS)) out[f] = fill(t[f], values);
	return out;
}

const errors = [];
for (const [lang, table] of Object.entries(listings)) {
	for (const e of check(lang, table, EN)) errors.push(`${lang} : ${e}`);
}
const wanted = new Set([...Object.values(PLAY), ...Object.values(APPSTORE)]);
for (const lang of wanted) if (!listings[lang] && !PARTIAL) errors.push(`langue manquante : stores/listings/${lang}.js`);
if (errors.length) { console.error('Fiches refusees :\n  ' + errors.join('\n  ')); process.exit(1); }

let play = 0, ios = 0, md = 0;
for (const [locale, lang] of Object.entries(PLAY)) {
	const t = texts(lang, locale); if (!t) continue;
	const dir = path.join(ROOT, 'fastlane', 'metadata', 'android', locale);
	emit(path.join(dir, 'title.txt'), t.title);
	emit(path.join(dir, 'short_description.txt'), t.short);
	emit(path.join(dir, 'full_description.txt'), t.full);
	emit(path.join(dir, 'changelogs', `${VERSION_CODE}.txt`), t.whatsNew);
	play++;
}
emit(path.join(ROOT, 'fastlane', 'metadata', 'ios', 'copyright.txt'), COPYRIGHT);
for (const [locale, lang] of Object.entries(APPSTORE)) {
	const t = texts(lang, locale); if (!t) continue;
	const dir = path.join(ROOT, 'fastlane', 'metadata', 'ios', locale);
	emit(path.join(dir, 'name.txt'), t.title);
	emit(path.join(dir, 'subtitle.txt'), t.subtitle);
	emit(path.join(dir, 'description.txt'), t.full);
	emit(path.join(dir, 'keywords.txt'), t.keywords);
	emit(path.join(dir, 'promotional_text.txt'), t.short);
	emit(path.join(dir, 'release_notes.txt'), t.whatsNew);
	emit(path.join(dir, 'privacy_url.txt'), PRIVACY_URL);
	emit(path.join(dir, 'support_url.txt'), SUPPORT_URL);
	ios++;
}
// Microsoft Store (Partner Center), AppGallery Connect, GetApps : saisie manuelle, un fichier par langue a coller.
for (const lang of Object.keys(listings)) {
	const t = texts(lang, 'x'); if (!t) continue;
	const body = `# ${t.title}\n\n## Sous-titre / phrase courte\n${t.subtitle}\n\n${t.short}\n\n## Description\n${t.full}\n\n## Mots-cles\n${t.keywords}\n\n## Nouveautes\n${t.whatsNew}\n`;
	for (const store of ['microsoft', 'appgallery', 'getapps']) emit(path.join(ROOT, 'stores', 'out', store, `${lang}.md`), body);
	md++;
}
if (failed) process.exit(1);
console.log(`fiches ${CHECK ? 'verifiees' : 'ecrites'} : Play ${play} locales, App Store ${ios} locales, ${md} langues en texte a coller (version Android ${VERSION_CODE})`);
