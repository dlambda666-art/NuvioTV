#!/bin/sh
# Génère les plugins « addon666 » depuis le modèle commun.
set -e
cd "$(dirname "$0")"
# 20/11 : Wascustom666 (wastream666) retiré : instance fermée (HTTP 502, disparu du Discord)
for pair in Lumio:lumio666 StreamFusion:streamfusion666; do
  n=${pair%%:*}; id=${pair##*:}
  sed "s/__NAME__/$n/g" src/addon666.js > "$id.js"
done

# 666TV : les « cas pièges » doivent passer avant toute publication.
node tests/pieges.js
