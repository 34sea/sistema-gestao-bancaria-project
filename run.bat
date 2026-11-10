@echo off
REM =====================================================================
REM  Sistema de Gestao Bancaria - execucao (Windows)
REM  Compilar primeiro com compile.bat. Requisito: JDK 21 (JRE >= 21).
REM =====================================================================
cd /d "%~dp0"
if not exist out\controllers\AuthController.class (
  echo A aplicacao ainda nao foi compilada. A compilar...
  call compile.bat
)
java -cp out Main
pause
