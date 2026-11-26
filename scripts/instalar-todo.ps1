param([switch]$SinWhisper, [switch]$SinCompilar)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Raiz = Split-Path -Parent $PSScriptRoot
$Herramientas = Join-Path $Raiz 'herramientas'
$Descargas = Join-Path $Herramientas 'descargas'
$UrlJdk = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse'
$UrlOllama = 'https://github.com/ollama/ollama/releases/latest/download/ollama-windows-amd64.zip'
$UrlWhisper = 'https://github.com/ggml-org/whisper.cpp/releases/download/b5907/whisper-bin-x64.zip'
$HashWhisper = 'ECCA07B277D943E9B9F4712856A144B857271A6B333104E511E1988E80DB582B'
$UrlModeloWhisper = 'https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin'
$HashModeloWhisper = '60ED5BC3DD14EEA856493D334349B405782DDCAF0028D4B5DF4088345FBA2EFE'

function Paso($texto) { Write-Host ''; Write-Host "== $texto" -ForegroundColor Cyan }

function Descargar($url, $destino, $hash) {
    if (Test-Path $destino) { return }
    New-Item -ItemType Directory -Force (Split-Path -Parent $destino) | Out-Null
    $parcial = "$destino.part"
    Write-Host "[..] Descargando $(Split-Path -Leaf $destino)"
    & curl.exe -L --fail --retry 5 --retry-delay 5 -C - -o $parcial $url
    if ($LASTEXITCODE -ne 0) { throw "No se pudo descargar $url. Revisa tu conexion y vuelve a ejecutar INSTALAR.bat (continua donde quedo)." }
    if ($hash -and (Get-FileHash $parcial -Algorithm SHA256).Hash -ne $hash) {
        Remove-Item $parcial
        throw "El archivo descargado de $url esta danado. Vuelve a ejecutar INSTALAR.bat."
    }
    Move-Item $parcial $destino
}

function VersionJava($exe) {
    $salida = cmd /c "`"$exe`" -version 2>&1"
    if ("$salida" -match 'version "(\d+)') { return [int]$Matches[1] }
    return 0
}

function BuscarJdk {
    $candidatos = @()
    if (Test-Path "$Herramientas\jdk") { $candidatos += Get-ChildItem "$Herramientas\jdk" -Directory | ForEach-Object { $_.FullName } }
    if ($env:JAVA_HOME) { $candidatos += $env:JAVA_HOME }
    $enPath = Get-Command java -ErrorAction SilentlyContinue
    if ($enPath) {
        $propiedades = cmd /c "`"$($enPath.Source)`" -XshowSettings:properties -version 2>&1"
        $linea = $propiedades | Where-Object { $_ -match 'java\.home = (.+)$' } | Select-Object -First 1
        if ($linea -match 'java\.home = (.+)$') { $candidatos += $Matches[1].Trim() }
    }
    if (Test-Path 'C:\Program Files\Java') { $candidatos += Get-ChildItem 'C:\Program Files\Java' -Directory | ForEach-Object { $_.FullName } }
    foreach ($c in $candidatos) {
        if ((Test-Path "$c\bin\javac.exe") -and (VersionJava "$c\bin\java.exe") -ge 17) { return $c }
    }
    return $null
}

function OllamaResponde {
    try { Invoke-RestMethod 'http://localhost:11434/api/tags' -TimeoutSec 3 | Out-Null; return $true } catch { return $false }
}

Set-Location $Raiz
$libre = [math]::Round((Get-PSDrive (Split-Path -Qualifier $Raiz).TrimEnd(':')).Free / 1GB, 1)
Write-Host "Instalacion de Atlas en $Raiz  (espacio libre: $libre GB)"
if ($libre -lt 8) { Write-Host '[!!] Se recomiendan al menos 8 GB libres si es la primera instalacion.' -ForegroundColor Yellow }

Paso '1/5 Java'
$jdk = BuscarJdk
if (-not $jdk) {
    Descargar $UrlJdk "$Descargas\jdk21.zip" $null
    Expand-Archive "$Descargas\jdk21.zip" -DestinationPath "$Herramientas\jdk" -Force
    $jdk = BuscarJdk
    if (-not $jdk) { throw 'No se pudo preparar Java.' }
}
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
Write-Host "[ok] Java: $jdk"

Paso '2/5 Ollama (motor de inteligencia artificial)'
$ollamaLocal = "$Herramientas\ollama\ollama.exe"
$ollamaInstalado = "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe"
$enPath = Get-Command ollama -ErrorAction SilentlyContinue
if (Test-Path $ollamaLocal) { $ollama = $ollamaLocal }
elseif ($enPath) { $ollama = $enPath.Source }
elseif (Test-Path $ollamaInstalado) { $ollama = $ollamaInstalado }
else {
    Write-Host 'No hay Ollama en este equipo: se descarga una copia dentro del proyecto (cerca de 1,6 GB).'
    Descargar $UrlOllama "$Descargas\ollama.zip" $null
    Expand-Archive "$Descargas\ollama.zip" -DestinationPath "$Herramientas\ollama" -Force
    Remove-Item "$Descargas\ollama.zip"
    $ollama = $ollamaLocal
}
$env:Path = "$(Split-Path -Parent $ollama);$env:Path"
if ($ollama -eq $ollamaLocal) { $env:OLLAMA_MODELS = "$Herramientas\ollama-modelos" }
Write-Host "[ok] Ollama: $ollama"
if (-not (OllamaResponde)) {
    Start-Process $ollama -ArgumentList 'serve' -WindowStyle Hidden
    $intentos = 0
    while (-not (OllamaResponde) -and $intentos -lt 30) { Start-Sleep 2; $intentos++ }
    if (-not (OllamaResponde)) { throw 'Ollama no respondio.' }
}

Paso '3/5 Modelos de voz, lectura de imagenes e inteligencia artificial'
& "$PSScriptRoot\instalar-dependencias.ps1"

Paso '4/5 Whisper (reconocimiento de voz mas preciso)'
$config = "$Raiz\config\application.properties"
$yaConfigurado = $false
if (Test-Path $config) {
    $linea = Get-Content $config | Where-Object { $_ -match '^app\.atlas\.voz\.whisper-ejecutable=(.+)$' } | Select-Object -First 1
    if ($linea -match '=(.+)$') { $yaConfigurado = Test-Path $Matches[1].Trim() }
}
if ($SinWhisper) { Write-Host '[--] Whisper omitido' }
elseif ($yaConfigurado) { Write-Host '[ok] Whisper ya configurado' }
else {
    $carpeta = "$Herramientas\whisper"
    if (-not (Test-Path "$carpeta\bin\Release\whisper-cli.exe")) {
        Descargar $UrlWhisper "$Descargas\whisper.zip" $HashWhisper
        Expand-Archive "$Descargas\whisper.zip" -DestinationPath "$carpeta\bin" -Force
        if (-not (Test-Path "$carpeta\bin\Release\whisper-cli.exe")) {
            $exe = Get-ChildItem "$carpeta\bin" -Recurse -Filter whisper-cli.exe | Select-Object -First 1
            New-Item -ItemType Directory -Force "$carpeta\bin\Release" | Out-Null
            Copy-Item "$($exe.DirectoryName)\*" "$carpeta\bin\Release" -Recurse -Force
        }
    }
    Descargar $UrlModeloWhisper "$carpeta\ggml-base.bin" $HashModeloWhisper
    New-Item -ItemType Directory -Force "$Raiz\config" | Out-Null
    $anteriores = @()
    if (Test-Path $config) { $anteriores = @(Get-Content $config | Where-Object { $_ -notmatch '^app\.atlas\.voz\.(whisper-|usar-whisper)' }) }
    $nuevas = @('app.atlas.voz.usar-whisper=true',
        'app.atlas.voz.whisper-ejecutable=herramientas/whisper/bin/Release/whisper-cli.exe',
        'app.atlas.voz.whisper-modelo=herramientas/whisper/ggml-base.bin')
    Set-Content $config -Value ($anteriores + $nuevas) -Encoding ASCII
    Write-Host '[ok] Whisper instalado'
}

Paso '5/5 Compilar Atlas'
if ($SinCompilar) { Write-Host '[--] Compilacion omitida' }
else {
    $maven = Join-Path $Raiz 'mvnw.cmd'
    & $maven -B -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'La compilacion fallo. Cierra Atlas si esta abierto y vuelve a intentar.' }
    Write-Host '[ok] target\atlas-0.1.0.jar'
}

Write-Host ''
Write-Host 'Instalacion terminada. Abre Atlas con doble clic en INICIAR.bat' -ForegroundColor Green
