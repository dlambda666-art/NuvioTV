# Plugins 666TV

Dépôt de plugins pour 666TV. Adresse à coller dans 666TV (Paramètres → Plugins → Ajouter un dépôt) :

https://raw.githubusercontent.com/dlambda666-art/NuvioTV/plugins/manifest.json

- **Comet666** : torrents en VF, lus via TorBox (intégration debrid de 666TV). Jamais de P2P.

## Lumio666, Frenchio666, StreamFusion666, Wastream666

Générés par `./build.sh` depuis `src/addon666.js` (ne pas éditer les fichiers générés).
Chacun lit l'adresse de **ta** config dans `SCRAPER_SETTINGS.url`, que 666TV stocke
uniquement sur l'appareil (Plugins → « Comet666 – mes sources »). Aucune adresse ni clé
n'est dans ce dépôt. Ils gardent la VF en 1080p/4K, déjà en cache sur le debrid.
