#!/bin/sh
# Génère les plugins « addon666 » depuis le modèle commun.
set -e
cd "$(dirname "$0")"
# 20/11 : Wastream666 est de retour sous son vrai nom (même fichier et même id : le lien collé reste)
for pair in Lumio:lumio666 StreamFusion:streamfusion666 Wastream:wastream666; do
  n=${pair%%:*}; id=${pair##*:}
  sed "s/__NAME__/$n/g" src/addon666.js > "$id.js"
done

# 666TV : les « cas pièges » doivent passer avant toute publication.
node tests/pieges.js
