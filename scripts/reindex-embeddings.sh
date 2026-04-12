#!/usr/bin/env bash
set -euo pipefail

export SPRING_MAIN_WEB_APPLICATION_TYPE=none

if [[ "${1:-}" == "--help" ]]; then
  echo "Usage: APP_EMBEDDING_PROVIDER=onnx APP_ONNX_MODEL_PATH=/abs/model.onnx ./scripts/reindex-embeddings.sh"
  exit 0
fi

mvn -q -DskipTests spring-boot:run -Dspring-boot.run.main-class=org.avromanov.yesihave.cli.ReindexEmbeddingsCli
