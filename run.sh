#!/usr/bin/env bash
# ============================================================
#  Sistema de Gestão Bancária - execução (Linux/macOS)
# ============================================================
cd "$(dirname "$0")"
if [ ! -f out/controllers/AuthController.class ]; then
  echo "A aplicação ainda não foi compilada. A compilar..."
  bash compile.sh || exit 1
fi
java -cp out Main
