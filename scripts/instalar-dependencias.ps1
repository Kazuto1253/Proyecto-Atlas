# Autores: K (Kevin) y Y (Yenifher)
param([switch]$Vision)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Destino = if ($env:ATLAS_MODELOS) { $env:ATLAS_MODELOS } else { Join-Path $HOME '.atlas\modelos' }
$VoskModelo = 'vosk-model-small-es-0.42'
$PiperVersion = '2023.11.14-2'
$Voz = 'es_ES-davefx-medium'
$VozUrl = 'https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/es/es_ES/davefx/medium'
$Llm = if ($env:ATLAS_LLM) { $env:ATLAS_LLM } else { 'qwen2.5:3b' }
$Embeddings = 'nomic-embed-text'
$ModeloVision = if ($env:ATLAS_MODELO_VISION) { $env:ATLAS_MODELO_VISION } else { 'qwen2.5vl:3b' }
$TessdataUrl = 'https://github.com/tesseract-ocr/tessdata_fast/raw/main'

New-Item -ItemType Directory -Force (Join-Path $Destino 'vosk'), (Join-Path $Destino 'voces'), (Join-Path $Destino 'tessdata') | Out-Null
Write-Host "Carpeta de modelos: $Destino"

if (Test-Path (Join-Path $Destino "vosk\$VoskModelo")) {
    Write-Host '[ok] Vosk ya instalado'
} else {
    Write-Host "[..] Descargando Vosk ($VoskModelo)"
    $zip = Join-Path $env:TEMP "$VoskModelo.zip"
    Invoke-WebRequest "https://alphacephei.com/vosk/models/$VoskModelo.zip" -OutFile $zip
    Expand-Archive $zip -DestinationPath (Join-Path $Destino 'vosk') -Force
    Remove-Item $zip
}

if (Test-Path (Join-Path $Destino 'piper\piper.exe')) {
    Write-Host '[ok] Piper ya instalado'
} else {
    Write-Host '[..] Descargando Piper (windows amd64)'
    $zip = Join-Path $env:TEMP 'piper.zip'
    Invoke-WebRequest "https://github.com/rhasspy/piper/releases/download/$PiperVersion/piper_windows_amd64.zip" -OutFile $zip
    Expand-Archive $zip -DestinationPath $Destino -Force
    Remove-Item $zip
}

foreach ($ext in 'onnx', 'onnx.json') {
    $archivo = Join-Path $Destino "voces\$Voz.$ext"
    if (Test-Path $archivo) {
        Write-Host "[ok] $Voz.$ext ya existe"
    } else {
        Write-Host "[..] Descargando $Voz.$ext"
        Invoke-WebRequest "$VozUrl/$Voz.$ext" -OutFile $archivo
    }
}

$Tess = Join-Path $Destino 'tessdata\spa.traineddata'
if (Test-Path $Tess) {
    Write-Host '[ok] spa.traineddata ya existe'
} else {
    Write-Host '[..] Descargando spa.traineddata'
    Invoke-WebRequest "$TessdataUrl/spa.traineddata" -OutFile $Tess
}

if (Get-Command ollama -ErrorAction SilentlyContinue) {
    Write-Host "[..] Descargando modelos de Ollama ($Llm, $Embeddings)"
    ollama pull $Llm
    ollama pull $Embeddings
    if ($Vision) {
        Write-Host "[..] Descargando modelo de vision ($ModeloVision, ~3 GB)"
        ollama pull $ModeloVision
    } else {
        Write-Host '[--] Modelo de vision omitido (opcional: agrega -Vision)'
    }
} else {
    Write-Host '[!!] Ollama no esta instalado. Descargalo de https://ollama.com/download'
    Write-Host '     y vuelve a ejecutar este script.'
}

Write-Host 'Listo. Atlas ya puede funcionar sin internet.'
