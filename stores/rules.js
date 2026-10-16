'use strict';
/** Regles communes a stores/validate.js et stores/generate.js. */

/** Limites, en caracteres, apres remplacement des jetons (le nom fait 10 lettres). */
const LIMITS = { title: 30, subtitle: 30, short: 80, full: 4000, keywords: 100, whatsNew: 500 };

/**
 * Noms qu'aucune fiche ne doit citer : autres plateformes, magasins,
 * services de localisation et concurrents, graphies locales comprises.
 * Verifie sans tenir compte de la casse.
 */
const FORBIDDEN = [
	'android', 'androida', 'androidu', 'iphone', 'ipad', 'ios', 'apple', 'google', 'play store', 'app store', 'appstore',
	'windows', 'microsoft', 'huawei', 'xiaomi', 'samsung', 'appgallery', 'getapps',
	'driversnote', 'mileiq', 'everlance', 'triplog', 'hurdlr', 'stride', 'fuelio', 'timeline', 'maps',
	'アンドロイド', '安卓', '苹果', '谷歌', '微软', 'андроид', 'айфон', 'эппл', 'гугл', 'майкрософт',
	'안드로이드', '아이폰', '애플', '구글', 'แอนดรอยด์', 'ไอโฟน', 'แอปเปิล', 'กูเกิล',
	'अँड्रॉइड', 'एंड्रॉइड', 'आईफोन', 'ऐपल', 'गूगल', 'أندرويد', 'آيفون', 'أبل', 'جوجل', 'غوغل',
	'אנדרואיד', 'אייפון', 'אפל', 'גוגל'
];

const TOKENS = ['{app}', '{monthly}', '{yearly}'];
const SAMPLE = { '{app}': 'Kilomètres', '{monthly}': '4,99 €', '{yearly}': '49,99 €' };

function fill(text, values = SAMPLE) {
	return text.replace(/\{(app|monthly|yearly)\}/g, (m) => values[m] ?? m);
}

/**
 * Un terme doit etre un MOT entier (« ios » ne doit pas se lire dans
 * « dispositivos », ni « אפל » dans « אפליקציה ») ; dans une ecriture sans
 * espaces entre les mots, il est cherche tel quel.
 */
function mentions(text, term) {
	const low = text.toLowerCase();
	// Ecritures sans espace entre les mots (chinois, japonais, thai, lao, khmer, birman) : recherche telle quelle.
	if (/[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Thai}\p{Script=Lao}\p{Script=Khmer}\p{Script=Myanmar}]/u.test(term)) return low.includes(term);
	const re = new RegExp(`(^|[^\\p{L}])${term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?=$|[^\\p{L}])`, 'u');
	return re.test(low);
}

function check(lang, table, en) {
	const errors = [];
	for (const field of Object.keys(LIMITS)) {
		const v = table[field];
		if (typeof v !== 'string' || !v.trim()) { errors.push(`${field} : vide`); continue; }
		const len = fill(v).length;
		if (len > LIMITS[field]) errors.push(`${field} : ${len} caracteres, maximum ${LIMITS[field]}`);
		for (const t of TOKENS) {
			const want = (en[field].match(new RegExp(t.replace(/[{}]/g, '\\$&'), 'g')) || []).length;
			const have = (v.match(new RegExp(t.replace(/[{}]/g, '\\$&'), 'g')) || []).length;
			if (want !== have) errors.push(`${field} : jeton ${t} present ${have} fois au lieu de ${want}`);
		}
		for (const f of FORBIDDEN) if (mentions(v, f)) errors.push(`${field} : cite « ${f} »`);
		if (field !== 'keywords' && lang !== 'en' && v === en[field]) errors.push(`${field} : identique a l'anglais (non traduit ?)`);
	}
	for (const k of Object.keys(table)) if (!(k in LIMITS)) errors.push(`champ inconnu : ${k}`);
	if (table.keywords && table.keywords.split(',').some((k) => k !== k.trim())) errors.push('keywords : pas d\'espace autour des virgules');
	return errors;
}

module.exports = { LIMITS, FORBIDDEN, TOKENS, fill, check, mentions };
