/**
 * Maquettes d'ecrans, en HTML/CSS rendu par Chromium : des images d'exemple
 * pour se faire une idee de l'application avant de la compiler sur un
 * appareil. Ce ne sont PAS des captures : le style imite Material 3
 * (Android) et l'interface iOS, les donnees sont inventees.
 *
 *   node tools/branding/mockups.js      ->  tools/branding/out/mockups/*.png
 */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright-core');

const OUT = path.join(__dirname, 'out', 'mockups');
const GREEN = '#1F6F5F', GREEN_SOFT = '#CDEFE4', INK = '#1B1C1A', MUTED = '#5E6460', BG = '#F8F6F2', CARD = '#FFFFFF';

const CSS = `
*{box-sizing:border-box} body{margin:0;font-family:Roboto,"Segoe UI",Helvetica,Arial,sans-serif;color:${INK};-webkit-font-smoothing:antialiased}
.phone{width:390px;height:844px;border-radius:44px;background:${BG};overflow:hidden;position:relative;border:10px solid #111;box-shadow:0 20px 60px #0004}
.status{height:44px;display:flex;justify-content:space-between;align-items:center;padding:0 28px;font-size:14px;font-weight:600}
.h1{font-size:28px;font-weight:600;padding:8px 20px 4px}
.chips{display:flex;gap:8px;padding:8px 20px}
.chip{border:1px solid #BFC9C4;border-radius:10px;padding:6px 12px;font-size:14px;color:${INK}}
.chip.on{background:${GREEN_SOFT};border-color:${GREEN_SOFT};color:#083F34;font-weight:600}
.card{background:${CARD};border-radius:16px;margin:8px 20px;padding:16px;box-shadow:0 1px 2px #0001}
.big{font-size:34px;font-weight:600;color:${GREEN};line-height:1}
.sub{font-size:14px;color:${MUTED};margin-top:6px}
.banner{margin:8px 20px;background:#FFF1D6;color:#6B4A00;border-radius:12px;padding:10px 14px;font-size:14px}
.row{display:flex;align-items:center;padding:12px 20px;border-bottom:1px solid #ECEAE4;gap:12px}
.row .date{width:44px;text-align:center;font-size:12px;color:${MUTED};line-height:1.2}
.row .date b{display:block;font-size:18px;color:${INK}}
.row .t{flex:1} .row .t .l{font-size:16px} .row .t .s{font-size:13px;color:${MUTED};margin-top:2px}
.row .km{font-size:16px;font-weight:600}
.tag{display:inline-block;font-size:11px;padding:2px 8px;border-radius:999px;background:${GREEN_SOFT};color:#083F34;margin-right:6px}
.tag.pro{background:#FFE4B5;color:#6B4A00}.tag.perso{background:#E8E8F6;color:#2E2E6E}
.fab{position:absolute;right:20px;bottom:104px;background:${GREEN_SOFT};color:#083F34;border-radius:16px;padding:16px 20px;font-weight:600;font-size:15px;box-shadow:0 4px 12px #0003}
.nav{position:absolute;left:0;right:0;bottom:0;height:84px;background:#EEF3F0;display:flex;justify-content:space-around;align-items:flex-start;padding-top:12px}
.nav div{font-size:12px;text-align:center;width:90px;color:${MUTED}} .nav .on{color:${INK};font-weight:600}
.nav i{display:block;width:60px;height:28px;border-radius:14px;margin:0 auto 4px;background:transparent} .nav .on i{background:${GREEN_SOFT}}
/* iOS */
.ios{background:#F2F2F7;font-family:-apple-system,"SF Pro Text","Helvetica Neue",Helvetica,Arial,sans-serif}
.ios .h1{font-size:34px;font-weight:700;padding:10px 20px 6px}
.seg{margin:8px 20px;background:#E3E3E8;border-radius:9px;display:flex;padding:2px}
.seg div{flex:1;text-align:center;font-size:13px;padding:6px;border-radius:7px} .seg .on{background:#fff;font-weight:600;box-shadow:0 1px 3px #0002}
.list{background:#fff;border-radius:12px;margin:8px 20px;overflow:hidden}
.list .r{display:flex;justify-content:space-between;padding:11px 16px;border-bottom:1px solid #E5E5EA;font-size:16px}
.list .r:last-child{border-bottom:0} .list .r.total{font-weight:700;color:${GREEN}}
.note{margin:8px 20px;font-size:13px;color:#6E6E73;line-height:1.35}
.pill{background:#fff;border-radius:12px;margin:8px 20px;padding:12px 16px;font-size:15px}
.pill.good{background:#E3F6EC;color:#0B5A3A}
.btn{background:${GREEN};color:#fff;border-radius:12px;padding:14px;text-align:center;font-weight:600;font-size:16px;margin:8px 20px}
.btn.o{background:#fff;color:${GREEN};border:1.5px solid ${GREEN}}
.plan{border:2px solid #D7DDDA;border-radius:16px;margin:8px 20px;padding:14px 16px;display:flex;justify-content:space-between;align-items:center}
.plan.on{border-color:${GREEN};background:#F2FBF7}
.plan .p{font-size:20px;font-weight:700} .plan .d{font-size:13px;color:${MUTED}}
.feat{display:flex;gap:10px;padding:5px 20px;font-size:15px} .feat b{color:${GREEN}}
`;

function phone(inner, { ios = false, dark = false } = {}) {
	return `<!doctype html><html><head><meta charset="utf-8"><style>${CSS}body{background:transparent}</style></head><body>
	<div class="phone ${ios ? 'ios' : ''}"><div class="status"><span>9:41</span><span>●●● ▲ ▮</span></div>${inner}</div></body></html>`;
}

const trips = [
	['15', 'oct', 'Domicile → Bureau', 'Aller-retour · Clio 5 CV', '58,0 km', 'commute'],
	['14', 'oct', 'Domicile → Bureau', 'Aller-retour · Clio 5 CV', '58,0 km', 'commute'],
	['13', 'oct', 'Bureau → Client Lyon', 'Aller simple · Clio 5 CV', '64,5 km', 'pro'],
	['13', 'oct', 'Client Lyon → Domicile', 'Aller simple · Clio 5 CV', '71,0 km', 'pro'],
	['10', 'oct', 'Domicile → Bureau', 'Aller-retour · Clio 5 CV', '58,0 km', 'commute'],
	['8', 'oct', 'Domicile → Gare', 'Aller-retour · Clio 5 CV', '12,0 km', 'perso']
];
const tagLabel = { commute: 'Domicile-travail', pro: 'Professionnel', perso: 'Personnel' };

const androidTrips = phone(`
	<div class="h1">Trajets</div>
	<div class="chips"><div class="chip on">Ce mois</div><div class="chip">Cette année</div><div class="chip">Favoris ⚡</div></div>
	<div class="card"><div class="big">412,5 km</div><div class="sub">Trajets : 9 · octobre 2026</div></div>
	<div class="banner">Trajets gratuits restants ce mois-ci : 2 · <b>Passer à Pro</b></div>
	${trips.map(([d, m, l, s, km, k]) => `<div class="row"><div class="date"><b>${d}</b>${m}</div><div class="t"><div class="l">${l}</div><div class="s"><span class="tag ${k}">${tagLabel[k]}</span>${s}</div></div><div class="km">${km}</div></div>`).join('')}
	<div class="fab">＋ Nouveau trajet</div>
	<div class="nav"><div class="on"><i></i>Trajets</div><div><i></i>Estimation</div><div><i></i>Plus</div></div>`);

const iosEstimate = phone(`
	<div class="h1">Estimation</div>
	<div class="seg"><div>2024</div><div>2025</div><div class="on">2026</div></div>
	<div class="note" style="margin-top:4px">Année de revenus 2026 · Clio 5 CV</div>
	<div class="list">
		<div class="r"><span>Km domicile-travail saisis</span><span>4 120</span></div>
		<div class="r"><span>Km domicile-travail retenus</span><span>4 120</span></div>
		<div class="r"><span>Km professionnels</span><span>1 850</span></div>
		<div class="r"><span>Indemnité kilométrique</span><span>3 512 €</span></div>
		<div class="r"><span>Péages et parkings</span><span>214 €</span></div>
		<div class="r total"><span>Total estimé</span><span>3 726 €</span></div>
	</div>
	<div class="pill good">✓ Les frais réels semblent plus avantageux que la déduction forfaitaire de 10 % (2 860 €).</div>
	<div class="note">Le barème de cette année n'est pas encore publié : le dernier barème connu est appliqué.</div>
	<div class="note"><b>Estimation indicative, non garantie.</b> Ne constitue pas un conseil fiscal et ne remplace ni les textes officiels, ni l'administration, ni un professionnel.</div>
	<div class="list"><div class="r"><span>Justificatifs à conserver</span><span>›</span></div><div class="r"><span>Exporter le rapport PDF</span><span>›</span></div></div>
	<div class="nav" style="background:#F9F9F9;border-top:1px solid #E5E5EA"><div><i></i>Trajets</div><div class="on"><i style="background:transparent"></i>Estimation</div><div><i></i>Plus</div></div>`, { ios: true });

const androidPro = phone(`
	<div class="h1">Version Pro</div>
	<div class="note" style="font-size:15px;color:${INK}">Tout ce qu'il faut pour vos frais réels, et aucune publicité.</div>
	<div style="height:6px"></div>
	${['Trajets illimités.', 'Estimation des frais réels et comparaison avec la déduction forfaitaire.', 'Export complet et certifié (PDF propre, tableur prêt pour le comptable ou les impôts).', 'Import de l\'historique des trajets de votre téléphone.', 'Péages, parkings et carburant ajoutés à l\'estimation.', 'Restauration d\'une sauvegarde et synchronisation multi-appareils.', 'Widget d\'écran d\'accueil et application montre.', 'Aucune publicité.'].map((f) => `<div class="feat"><b>✓</b><span>${f}</span></div>`).join('')}
	<div style="height:10px"></div>
	<div class="plan"><div><div class="p">4,99 € par mois</div><div class="d">Résiliable à tout moment</div></div><div>○</div></div>
	<div class="plan on"><div><div class="p">49,99 € par an</div><div class="d">Deux mois offerts par rapport au mensuel</div></div><div style="color:${GREEN};font-size:22px">●</div></div>
	<div class="btn">S'abonner</div>
	<div class="btn o">Restaurer les achats</div>
	<div class="note" style="font-size:11px">Abonnement à renouvellement automatique, facturé sur votre compte du magasin. Résiliable à tout moment dans les réglages du magasin.</div>`);

const androidForm = phone(`
	<div class="h1">Nouveau trajet</div>
	<div class="card" style="padding:12px 16px"><div class="sub">Trajets favoris</div><div class="chips" style="padding:8px 0 0"><div class="chip on">⚡ Domicile → Bureau</div><div class="chip">⚡ Bureau → Client Lyon</div></div></div>
	${[['Date', 'mercredi 15 octobre 2026'], ['Départ', 'Domicile'], ['Arrivée', 'Bureau'], ['Distance aller simple (km)', '29,0'], ['Motif', 'Domicile-travail'], ['Véhicule', 'Clio · 5 CV']].map(([l, v]) => `<div class="card" style="padding:10px 16px;margin:6px 20px"><div class="sub" style="margin:0;font-size:12px">${l}</div><div style="font-size:17px;margin-top:2px">${v}</div></div>`).join('')}
	<div class="card" style="margin:6px 20px;padding:10px 16px;display:flex;justify-content:space-between;align-items:center"><span style="font-size:16px">Aller-retour</span><span style="width:46px;height:26px;border-radius:13px;background:${GREEN};position:relative;display:inline-block"><i style="position:absolute;right:3px;top:3px;width:20px;height:20px;border-radius:10px;background:#fff"></i></span></div>
	<div class="btn" style="position:absolute;left:0;right:0;bottom:24px">Enregistrer</div>`);

function widgetBoard() {
	return `<!doctype html><html><head><meta charset="utf-8"><style>${CSS}
	body{width:1100px;height:620px;background:linear-gradient(135deg,#dfe9e4,#f4efe6);display:flex;align-items:center;justify-content:space-around;padding:30px}
	.home{width:390px;height:560px;border-radius:40px;background:url() #263238;position:relative;overflow:hidden;border:10px solid #111}
	.home .wall{position:absolute;inset:0;background:linear-gradient(160deg,#2b4f5f,#1f6f5f 60%,#c7d9a2)}
	.icons{position:absolute;left:24px;right:24px;top:40px;display:grid;grid-template-columns:repeat(4,1fr);gap:18px}
	.icons i{display:block;width:60px;height:60px;border-radius:16px;background:#ffffff55;margin:0 auto}
	.widget{position:absolute;left:20px;right:20px;top:230px;background:#F8F6F2ee;border-radius:24px;padding:16px 18px}
	.widget .k{font-size:13px;color:${MUTED}} .widget .v{font-size:26px;font-weight:700;color:${GREEN}}
	.widget .f{display:flex;gap:8px;margin-top:12px} .widget .f div{flex:1;background:${GREEN_SOFT};border-radius:12px;padding:8px;font-size:12px;text-align:center;color:#083F34;font-weight:600}
	.watch{width:260px;height:300px;border-radius:70px;background:#000;position:relative;border:12px solid #1c1c1e;box-shadow:0 20px 60px #0005}
	.watch .scr{position:absolute;inset:18px;border-radius:50px;background:#000;color:#fff;padding:22px 16px;font-family:-apple-system,Helvetica,Arial,sans-serif}
	.watch .t{font-size:13px;color:#8e8e93;text-align:center} .watch .b{background:#1c1c1e;border-radius:14px;padding:10px 12px;margin-top:8px;font-size:14px}
	.watch .b.on{background:${GREEN}} .watch .km{text-align:center;font-size:24px;font-weight:700;margin:4px 0 2px;color:#8CE0C4}
	.cap{margin-top:14px;text-align:center;font-size:14px;color:${MUTED};max-width:420px}
	</style></head><body>
	<div><div class="home"><div class="wall"></div><div class="icons">${'<i></i>'.repeat(8)}</div>
		<div class="widget"><div style="display:flex;justify-content:space-between"><div><div class="k">Aujourd'hui</div><div class="v">58 km</div></div><div><div class="k">Ce mois</div><div class="v">412 km</div></div></div>
		<div class="f"><div>⚡ Domicile → Bureau</div><div>⚡ Bureau → Lyon</div><div>＋</div></div></div></div><div class="cap">Widget d'écran d'accueil (Pro) : les km du jour et les favoris en un geste</div></div>
	<div style="display:flex;flex-direction:column;align-items:center"><div class="watch"><div class="scr"><div class="t">Ce mois</div><div class="km">412 km</div><div class="t">Trajet rapide</div><div class="b on">⚡ Domicile → Bureau</div><div class="b">⚡ Bureau → Lyon</div><div class="b">⚡ Domicile → Gare</div></div></div><div class="cap">Montre (Pro) : un trajet favori, un geste</div></div>
	</body></html>`;
}

function namesBoard(names) {
	const tile = (n) => `<div class="tile"><div class="ic"><svg width="64" height="64" viewBox="0 0 108 108"><rect width="108" height="108" rx="24" fill="${GREEN}"/><polygon points="30,84 47,30 61,30 78,84 68,84 56,44 52,44 40,84" fill="#fff" fill-opacity=".92"/><polygon points="52.5,52 55.5,52 56.5,60 51.5,60" fill="#F4EFE6"/><polygon points="50.5,66 57.5,66 58.5,74 49.5,74" fill="#F4EFE6"/></svg></div>
		<div class="nm">${n.name}</div><div class="why">${n.why}</div><div class="dom">${n.dom}</div></div>`;
	return `<!doctype html><html><head><meta charset="utf-8"><style>${CSS}
	body{width:1200px;height:780px;background:${BG};padding:28px}
	h1{font-size:26px;margin:0 0 4px} .lead{color:${MUTED};font-size:14px;margin-bottom:18px}
	.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}
	.tile{background:#fff;border-radius:18px;padding:18px;box-shadow:0 1px 3px #0001;min-height:300px}
	.ic{margin-bottom:12px} .nm{font-size:26px;font-weight:700;letter-spacing:-.01em} .why{font-size:13px;color:${MUTED};margin:6px 0 10px;line-height:1.35;min-height:52px}
	.dom{font-size:12px;line-height:1.6} .ok{color:#0B5A3A;font-weight:600} .ko{color:#9B2C2C}
	</style></head><body><h1>Idées de nom · exemples de rendu sous l'icône</h1><div class="lead">Disponibilité des domaines vérifiée par RDAP le 15 octobre 2026. Recherche de marque formelle (INPI, EUIPO, OMPI) encore à faire.</div>
	<div class="grid">${names.map(tile).join('')}</div></body></html>`;
}

const NAMES = [
	{ name: 'Kilotrail', why: 'Kilo + trail : la trace de vos kilomètres. Se dit pareil en anglais et en français.', dom: '<span class="ok">kilotrail.com libre</span><br><span class="ok">kilotrail.fr libre</span>' },
	{ name: 'Kilotally', why: 'Tally = le compte, le décompte. Court, international, sobre.', dom: '<span class="ok">kilotally.com libre</span><br><span class="ok">kilotally.fr libre</span>' },
	{ name: 'Viakilo', why: 'Via (la route) + kilo. Latin, donc lisible partout.', dom: '<span class="ok">viakilo.com libre</span><br><span class="ok">viakilo.fr libre</span>' },
	{ name: 'Kilokeeper', why: 'Celui qui garde vos kilomètres. Rassurant, parle à l\'anglophone.', dom: '<span class="ok">kilokeeper.com libre</span><br><span class="ok">kilokeeper.fr libre</span>' },
	{ name: 'Kilotrajet', why: 'Le plus français : kilo + trajet. Évident pour l\'usage impôts.', dom: '<span class="ok">kilotrajet.com libre</span><br><span class="ok">kilotrajet.fr libre</span>' },
	{ name: 'Kilodéclic', why: 'Un déclic, un trajet : le geste unique de l\'app. Très français.', dom: '<span class="ok">kilodeclic.com libre</span><br><span class="ok">kilodeclic.fr libre</span>' },
	{ name: 'Trajetbook', why: 'Le carnet de trajets, dit avec un mot que tout le monde connaît.', dom: '<span class="ok">trajetbook.com libre</span><br><span class="ok">trajetbook.fr libre</span>' },
	{ name: 'Kilotrip', why: 'Le plus simple en anglais. Le .com est pris, le .fr libre.', dom: '<span class="ko">kilotrip.com pris</span><br><span class="ok">kilotrip.fr libre</span>' }
];

(async () => {
	fs.mkdirSync(OUT, { recursive: true });
	const executablePath = process.env.CHROME || (fs.existsSync('/opt/pw-browsers/chromium') ? '/opt/pw-browsers/chromium' : undefined);
	const browser = await chromium.launch(executablePath ? { executablePath, args: ['--no-sandbox', '--disable-gpu'] } : { channel: 'chrome' });
	const shots = [
		['android-trajets.png', androidTrips, 430, 884, 2],
		['android-nouveau-trajet.png', androidForm, 430, 884, 2],
		['ios-estimation.png', iosEstimate, 430, 884, 2],
		['android-pro.png', androidPro, 430, 884, 2],
		['widget-et-montre.png', widgetBoard(), 1100, 620, 2],
		['noms.png', namesBoard(NAMES), 1200, 780, 2]
	];
	for (const [file, html, w, h, scale] of shots) {
		const page = await browser.newPage({ deviceScaleFactor: scale, viewport: { width: w, height: h } });
		await page.setContent(html, { waitUntil: 'load' });
		await page.screenshot({ path: path.join(OUT, file), omitBackground: true, fullPage: false });
		await page.close();
	}
	await browser.close();
	console.log('maquettes ecrites : ' + shots.map((s) => s[0]).join(', '));
})().catch((e) => { console.error(e); process.exit(1); });
