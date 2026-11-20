#!/bin/sh
# Génère les plugins « addon666 » depuis le modèle commun.
set -e
cd "$(dirname "$0")"
# 20/11 : Wastream666 est de retour sous son vrai nom (même fichier et même id : le lien collé reste)
# 20/11 : cases de cavalier sans nom : case4666 et case5666 (le nom affiché vient de l'addon collé)
for pair in Lumio:lumio666 StreamFusion:streamfusion666 Wastream:wastream666 Case4:case4666 Case5:case5666; do
  n=${pair%%:*}; id=${pair##*:}
  sed "s/__NAME__/$n/g" src/addon666.js > "$id.js"
done

# 666TV : les « cas pièges » doivent passer avant toute publication.
node tests/pieges.js
