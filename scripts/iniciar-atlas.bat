@echo off
rem Autores: K (Kevin) y Y (Yenifher)
cd /d "%~dp0.."

if not exist "target\atlas-0.1.0.jar" (
    echo No se encontro target\atlas-0.1.0.jar.
    echo Compila primero el proyecto:  mvnw.cmd -DskipTests package
    pause
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo No se encontro Java en el PATH. Instala Java 17 o superior.
    pause
    exit /b 1
)

start "" javaw -jar "target\atlas-0.1.0.jar" %*
