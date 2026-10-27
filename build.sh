#!/bin/sh
# Génère les plugins « addon666 » depuis le modèle commun.
set -e
cd "$(dirname "$0")"
for n in Lumio StreamFusion Wastream; do
  id=$(echo "$n" | tr 'A-Z' 'a-z')666
  sed "s/__NAME__/$n/g" src/addon666.js > "$id.js"
done

# 666TV : les « cas pièges » doivent passer avant toute publication.
node tests/pieges.js
