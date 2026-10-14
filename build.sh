#!/bin/sh
# Génère les plugins « addon666 » depuis le modèle commun.
set -e
cd "$(dirname "$0")"
for n in Lumio Frenchio StreamFusion Wastream; do
  id=$(echo "$n" | tr 'A-Z' 'a-z')666
  sed "s/__NAME__/$n/g" src/addon666.js > "$id.js"
done
