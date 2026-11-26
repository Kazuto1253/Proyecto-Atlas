@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\instalar-todo.ps1 %*
if errorlevel 1 echo La instalacion no termino. Lee el mensaje de arriba y vuelve a intentar.
pause
