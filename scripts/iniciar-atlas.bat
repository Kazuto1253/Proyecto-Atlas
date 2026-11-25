@echo off
rem Autores: K (Kevin) y Y (Yenifher)
rem Inicia Atlas con doble clic. Requiere Java 17+ y haber compilado el proyecto (mvnw.cmd -DskipTests package).
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

rem Para pasar opciones, por ejemplo: iniciar-atlas.bat --app.atlas.modo-solo-lectura=true
start "" javaw -jar "target\atlas-0.1.0.jar" %*
