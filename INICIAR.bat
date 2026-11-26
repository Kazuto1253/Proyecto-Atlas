@echo off
rem Autores: K (Kevin) y Y (Yenifher)
cd /d "%~dp0"
if not exist "target\atlas-0.1.0.jar" call "%~dp0INSTALAR.bat"
if not exist "target\atlas-0.1.0.jar" exit /b 1
set "JAVAW=javaw"
for /d %%D in ("herramientas\jdk\*") do set "JAVAW=%%~fD\bin\javaw.exe"
start "" "%JAVAW%" -jar "target\atlas-0.1.0.jar" %*
