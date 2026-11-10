#!/usr/bin/env bash
# ============================================================
#  Sistema de Gestão Bancária - compilação (Linux/macOS)
#  Requisito: JDK 21 instalado. Sem Maven, Gradle ou Docker.
# ============================================================
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out \
  src/Main.java \
  src/exceptions/*.java \
  src/models/*.java \
  src/persistence/*.java \
  src/repositories/*.java \
  src/services/*.java \
  src/utils/*.java \
  src/controllers/*.java \
  src/views/*.java
if [ $? -ne 0 ]; then
  echo ""
  echo "[ERRO] Compilação falhou. Verifique o JDK 21 (javac -version)."
  exit 1
fi
echo ""
echo "[OK] Projeto compilado em \"out\". Para executar: ./run.sh"
