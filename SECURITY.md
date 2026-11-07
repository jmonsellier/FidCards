# Sécurité

> **In English:** please do not open a public issue for a vulnerability. Report it
> privately through GitHub: **Security → Report a vulnerability** on this repository.

## Signaler une faille

N'ouvrez pas d'issue publique. Utilisez le signalement privé de GitHub : onglet
**Security** du dépôt, puis **Report a vulnerability**. Seul le mainteneur voit le
rapport.

Indiquez la version de l'app (Réglages, en bas de l'écran), le téléphone et la version
d'Android, et les étapes pour reproduire le problème.

Vous recevrez une première réponse sous 7 jours. Une fois le correctif publié, la faille
est décrite dans un avis de sécurité, avec votre nom si vous le souhaitez.

## Versions suivies

Seule la dernière version publiée sur Google Play et dans les releases GitHub reçoit des
correctifs.

## Surface d'attaque

FidCards n'a aucun accès réseau : la permission `INTERNET` n'est pas déclarée. Les
risques réalistes se trouvent donc :

- dans l'**import d'une sauvegarde ZIP** (archive malveillante, chemins de fichiers,
  taille démesurée) ;
- dans la **lecture des codes-barres** par la caméra (`zxing-cpp`) ;
- dans les **images choisies** par l'utilisateur (logo, recto, verso) ;
- dans les **raccourcis d'écran d'accueil** et les intents exposés.
