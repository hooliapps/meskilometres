'use strict';
/**
 * Prix affiches dans les fiches, par locale de magasin.
 *
 * Les magasins affichent le VRAI prix local dans la feuille d'achat ; la
 * fiche, elle, est du texte libre, et un prix en euros lu depuis New York
 * est faux. Une locale absente d'ici recoit le prix en euros : completer la
 * table au moment de fixer les paliers dans chaque console (le palier
 * 4,99 € n'est pas 4,99 $ partout).
 */
const EUR = { monthly: '4,99 €', yearly: '49,99 €' };
const USD = { monthly: '$4.99', yearly: '$49.99' };
const GBP = { monthly: '£4.99', yearly: '£49.99' };
module.exports = {
	DEFAULT: EUR,
	byLocale: {
		'en-US': USD, 'es-US': USD, 'es-419': USD, 'es-MX': USD,
		'en-GB': GBP
	}
};
