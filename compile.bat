@echo off
REM ============================================================
REM  Sistema de Gestao Bancaria - compilacao (Windows)
REM  Requisito: JDK 21 instalado (sem Maven, Docker ou autres)
REM ============================================================
cd /d "%~dp0"
mkdir out 2>nul
javac -encoding UTF-8 -d out ^
  src\Main.java ^
  src\exceptions\*.java ^
  src\models\*.java ^
  src\persistence\*.java ^
  src\repositories\*.java ^
  src\services\*.java ^
  src\utils\*.java ^
  src\controllers\*.java ^
  src\views\*.java
if errorlevel 1 (
  echo.
  echo [ERRO] Compilacao falhou. Verifique se o JDK 21 esta instalado (javac -version^).
  pause
  exit /b 1
)
echo.
echo [OK] Projeto compilado em "out". Para executar: run.bat
