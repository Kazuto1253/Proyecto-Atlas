param([string]$Iscc = 'D:\Atlas-modelos\instalador\InnoSetup\ISCC.exe')
$ErrorActionPreference = 'Stop'
$Raiz = Split-Path -Parent $PSScriptRoot
Set-Location $Raiz
if (-not (Test-Path $Iscc)) { throw "No se encontro Inno Setup (ISCC.exe) en $Iscc. Instalelo desde https://jrsoftware.org y pase la ruta con -Iscc." }
if (-not $env:JAVA_HOME) { throw 'Defina JAVA_HOME con la ruta del JDK.' }

$maven = Join-Path $Raiz 'mvnw.cmd'
& $maven -B -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'La compilacion fallo.' }

$base = Join-Path $Raiz 'target\instalador'
if (Test-Path $base) { Remove-Item $base -Recurse -Force }
New-Item -ItemType Directory -Force "$base\entrada" | Out-Null
Copy-Item 'target\atlas-0.1.0.jar' "$base\entrada"

& "$env:JAVA_HOME\bin\jpackage.exe" --type app-image --name Atlas --app-version 1.0.0 --input "$base\entrada" `
    --main-jar atlas-0.1.0.jar --dest "$base\imagen" --add-modules ALL-MODULE-PATH
if ($LASTEXITCODE -ne 0) { throw 'jpackage fallo.' }

& $Iscc /Q (Join-Path $PSScriptRoot 'atlas.iss')
if ($LASTEXITCODE -ne 0) { throw 'Inno Setup fallo.' }
Get-ChildItem "$base\salida\*.exe" | ForEach-Object { "Instalador creado: $($_.FullName)  ($([math]::Round($_.Length / 1MB)) MB)" }
