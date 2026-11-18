#!/bin/sh
# Génère les plugins « addon666 » depuis le modèle commun.
set -e
cd "$(dirname "$0")"
# 18/11 : Wastream666 s'affiche « Wascustom666 » (même fichier et même id : le lien collé sur les Shield reste)
for pair in Lumio:lumio666 StreamFusion:streamfusion666 Wascustom:wastream666; do
  n=${pair%%:*}; id=${pair##*:}
  sed "s/__NAME__/$n/g" src/addon666.js > "$id.js"
done

# 666TV : les « cas pièges » doivent passer avant toute publication.
node tests/pieges.js
