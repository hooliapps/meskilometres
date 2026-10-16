# Publicité : avis et réglage

## Ce qui est en place
Même discipline que QuanGame, portée dans `shared/.../ads/AdPolicy.kt` et
les deux `AdsManager` (Android, iOS) :

- **Jamais pour Pro.** Dès qu'un abonnement est confirmé, le SDK est éteint.
- **Deux formats seulement.** Un interstitiel à la fin d'une saisie, au plus
  un toutes les 3 minutes ; une annonce récompensée **choisie** par
  l'utilisateur, qui débloque une fonction Pro pour un temps :
  - l'estimation pendant 24 h,
  - un export dans l'heure,
  - cinq trajets de plus ce mois-ci, **une fois par mois** (plafond ajouté :
    la version gratuite plafonne donc à 10 trajets/mois, Pro reste le seul
    moyen d'aller au-delà).
- **Pas de bannière** : un formulaire qu'on touche sans arrêt au-dessus d'une
  bannière, c'est le clic accidentel assuré, que la régie sanctionne.
- **Consentement d'abord** : UMP (RGPD / UK) avant toute requête ; iOS,
  ATT demandé seulement après un consentement UMP favorable. Lien « Options
  de confidentialité des annonces » dans les réglages.
- **Signaux d'âge** : Android, API Play Age Signals (saveur `google`) ; iOS,
  `DeclaredAgeRange` avec seuil 18. Un compte signalé jeune : aucune annonce,
  aucun consentement demandé, aucun identifiant lu, et un écran qui le dit.
- **Classification « T »** imposée par le SDK, au-dessus de la console.
- Rien sur la montre ni dans les widgets, qui sont Pro de toute façon.

## Mon avis
Oui à la publicité, mais pas pour les mêmes raisons que Planningify.

Planningify fait 2 000 €/mois de publicité avec plus de 100 000 utilisateurs
actifs par jour : l'application s'ouvre plusieurs fois par jour. Un carnet de
kilométrage s'ouvre quelques fois par semaine, le temps de saisir un trajet,
et la version gratuite est plafonnée à 5 (ou 10) trajets par mois. Même
avec 20 000 utilisateurs gratuits, on parle de quelques centaines d'euros par
mois d'interstitiels (eCPM France 2 à 5 € pour un interstitiel, 10 à 20 €
pour une récompensée). **La publicité ne sera pas le revenu principal : Pro
l'est.**

Ce qu'elle fait de vraiment utile ici :
1. **Elle fait goûter Pro.** La récompensée « voir l'estimation 24 h » montre
   la valeur concrète (le montant de frais réels) ; c'est le meilleur argument
   de vente possible, et il est payé par l'annonceur.
2. **Elle monétise ceux qui ne paieront jamais** sans dégrader l'application
   pour ceux qui paient.
3. **Elle donne une sortie au plafond** sans frustration : « 5 trajets de plus
   contre une annonce », une fois par mois.

Risques et comment ils sont tenus :
- **Cannibalisation de Pro** : plafonds (24 h, 1 h, une fois par mois) et
  aucune récompense pour l'import d'historique, la restauration d'une
  sauvegarde, le widget ou la montre, qui restent strictement Pro. La
  sauvegarde elle-même est gratuite : on ne fait pas regarder une annonce
  pour ne pas perdre ses données.
- **Conformité** : UMP + ATT + signaux d'âge + classification T, et un outil
  déclaré 18+ (la Play Console demande une audience ; « adultes » évite
  toute obligation de contenu enfant).
- **Huawei / Chine** : pas de services Google, donc pas d'annonces ; la
  saveur `huawei` masque tout. Brancher HMS Ads Kit n'a d'intérêt qu'avec
  un volume réel sur AppGallery.

Réglages à faire dans la console AdMob : une unité interstitielle, une
unité récompensée, classification maximale T, et activer la médiation
plus tard si le remplissage France est faible.

## Quand couper
Si, après trois mois, le revenu publicitaire est inférieur à 10 % du revenu
Pro et que les avis mentionnent les annonces, passer `Features.ADS` à
`false` : tout le reste (quota, récompenses) s'efface de lui-même, aucune
chaîne ni écran à retirer.
