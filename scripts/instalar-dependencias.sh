#!/usr/bin/env bash
set -euo pipefail

DESTINO="${ATLAS_MODELOS:-$HOME/.atlas/modelos}"
VOSK_MODELO="vosk-model-small-es-0.42"
PIPER_VERSION="2023.11.14-2"
VOZ="es_ES-davefx-medium"
VOZ_URL="https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/es/es_ES/davefx/medium"
LLM="${ATLAS_LLM:-qwen2.5:3b}"
EMBEDDINGS="nomic-embed-text"
VISION="${ATLAS_MODELO_VISION:-qwen2.5vl:3b}"
TESSDATA_URL="https://github.com/tesseract-ocr/tessdata_fast/raw/main"

mkdir -p "$DESTINO/vosk" "$DESTINO/voces" "$DESTINO/tessdata"
echo "Carpeta de modelos: $DESTINO"

descargar() { # url archivo
    if command -v curl >/dev/null 2>&1; then curl -fL --retry 3 -o "$2" "$1"
    else wget -O "$2" "$1"; fi
}

if [ "${SIN_PAQUETES:-0}" = "1" ]; then
    echo "[--] Paquetes del sistema omitidos (SIN_PAQUETES=1)"
else
    SESION="${XDG_SESSION_TYPE:-}"
    if [ -z "$SESION" ]; then
        if [ -n "${WAYLAND_DISPLAY:-}" ]; then SESION=wayland; elif [ -n "${DISPLAY:-}" ]; then SESION=x11; fi
    fi
    echo "[..] Sesion grafica detectada: ${SESION:-desconocida}"
    if command -v apt-get >/dev/null 2>&1; then
        PAQ="tesseract-ocr tesseract-ocr-spa xdg-utils libglib2.0-bin pulseaudio-utils brightnessctl unzip curl"
        if [ "$SESION" = "wayland" ]; then PAQ="$PAQ grim slurp ydotool wtype wl-clipboard gnome-screenshot"
        else PAQ="$PAQ wmctrl xdotool"; fi
        echo "[..] sudo apt-get install $PAQ"
        sudo apt-get update -qq
        sudo apt-get install -y $PAQ || echo "[!!] Algunos paquetes no estan en tu distribucion; revisa la vista Diagnostico"
    elif command -v dnf >/dev/null 2>&1; then
        PAQ="tesseract tesseract-langpack-spa xdg-utils glib2 pulseaudio-utils brightnessctl unzip curl"
        if [ "$SESION" = "wayland" ]; then PAQ="$PAQ grim slurp ydotool wtype wl-clipboard gnome-screenshot"
        else PAQ="$PAQ wmctrl xdotool"; fi
        echo "[..] sudo dnf install $PAQ"
        sudo dnf install -y $PAQ || echo "[!!] Algunos paquetes no estan en tu distribucion; revisa la vista Diagnostico"
    else
        echo "[!!] No se encontro apt ni dnf: instala manualmente tesseract (spa), wmctrl/xdotool o grim/ydotool, brightnessctl"
    fi
fi

if [ -d "$DESTINO/vosk/$VOSK_MODELO" ]; then
    echo "[ok] Vosk ya instalado"
else
    echo "[..] Descargando Vosk ($VOSK_MODELO)"
    descargar "https://alphacephei.com/vosk/models/$VOSK_MODELO.zip" "/tmp/$VOSK_MODELO.zip"
    unzip -q "/tmp/$VOSK_MODELO.zip" -d "$DESTINO/vosk"
    rm -f "/tmp/$VOSK_MODELO.zip"
fi

if [ -x "$DESTINO/piper/piper" ]; then
    echo "[ok] Piper ya instalado"
else
    case "$(uname -m)" in
        x86_64)        ARQ="x86_64" ;;
        aarch64|arm64) ARQ="aarch64" ;;
        armv7l)        ARQ="armv7l" ;;
        *) echo "Arquitectura no soportada por Piper: $(uname -m)"; exit 1 ;;
    esac
    echo "[..] Descargando Piper ($ARQ)"
    descargar "https://github.com/rhasspy/piper/releases/download/$PIPER_VERSION/piper_linux_$ARQ.tar.gz" /tmp/piper.tar.gz
    tar -xzf /tmp/piper.tar.gz -C "$DESTINO"
    rm -f /tmp/piper.tar.gz
fi

for ext in onnx onnx.json; do
    if [ -f "$DESTINO/voces/$VOZ.$ext" ]; then
        echo "[ok] $VOZ.$ext ya existe"
    else
        echo "[..] Descargando $VOZ.$ext"
        descargar "$VOZ_URL/$VOZ.$ext" "$DESTINO/voces/$VOZ.$ext"
    fi
done

if [ -f "$DESTINO/tessdata/spa.traineddata" ]; then
    echo "[ok] spa.traineddata ya existe"
else
    echo "[..] Descargando spa.traineddata"
    descargar "$TESSDATA_URL/spa.traineddata" "$DESTINO/tessdata/spa.traineddata"
fi

if command -v ollama >/dev/null 2>&1; then
    echo "[..] Descargando modelos de Ollama ($LLM, $EMBEDDINGS)"
    ollama pull "$LLM"
    ollama pull "$EMBEDDINGS"
    if [ "${ATLAS_VISION:-0}" = "1" ]; then
        echo "[..] Descargando modelo de vision ($VISION, ~3 GB)"
        ollama pull "$VISION"
    else
        echo "[--] Modelo de vision omitido (opcional: ATLAS_VISION=1 bash $0)"
    fi
else
    echo "[!!] Ollama no esta instalado. Instalalo con:"
    echo "     curl -fsSL https://ollama.com/install.sh | sh"
    echo "     y vuelve a ejecutar este script."
fi

echo "Listo. Atlas ya puede funcionar sin internet."
