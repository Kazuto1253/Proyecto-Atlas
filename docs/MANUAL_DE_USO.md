# Manual de Uso - Atlas

Autores: K (Kevin) y Y (Yenifher)

## 1. Introducción
Atlas es una aplicación de escritorio que funciona como asistente de voz para estudiantes y
docentes. Conversa por voz o por texto con un modelo de lenguaje que corre en la propia
computadora (Ollama), sin enviar información a servicios externos, y puede **actuar sobre el
equipo** (abrir carpetas, volumen, capturas, notas, etc.). Incluye inicio de sesión con roles,
un panel de inicio con métricas en vivo y una vista de diagnóstico.

## 2. Requisitos del sistema
- Windows 10 o superior (algunas herramientas del asistente solo existen en Windows por ahora).
- Java 17 o superior (probado con JDK 21).
- Ollama instalado y abierto, con los modelos `qwen2.5:3b` y `nomic-embed-text`.
- Micrófono y salida de audio (para el modo por voz).
- Internet solo para la instalación inicial de modelos; luego funciona sin conexión.

## 3. Instalación
1. Instala Java 17+ y Ollama (https://ollama.com/download).
2. Desde la carpeta del proyecto ejecuta:
   `powershell -ExecutionPolicy Bypass -File scripts\instalar-dependencias.ps1`
   Descarga Vosk, Piper, la voz `es_ES-davefx-medium`, `spa.traineddata` y los modelos de Ollama
   en `C:\Users\<usuario>\.atlas\modelos`.
3. Opcional ("describir la pantalla"): agrega `-Vision` (descarga ~3 GB).

## 4. Inicio de sesión y roles
Al abrir Atlas aparece la pantalla de acceso. En el primer arranque se crean **cuentas de
demostración** (cámbiales la contraseña desde la vista Usuarios):

| Usuario | Contraseña | Rol |
|---|---|---|
| admin | Admin123 | Administrador |
| docente | Docente123 | Docente |
| estudiante | Estudiante123 | Estudiante |

Cualquier estudiante puede crear su cuenta con **"Regístrate"** (siempre entra como Estudiante).
Las contraseñas se guardan cifradas (BCrypt) y, tras 5 intentos fallidos, el usuario se bloquea
60 segundos. Para no crear cuentas de demostración usa `app.seeder.enabled=false`.

| Vista | Estudiante | Docente | Administrador |
|---|:-:|:-:|:-:|
| Inicio | Sí | Sí | Sí |
| Asistente | Sí | Sí | Sí |
| Historial | Sí | Sí | Sí |
| Documentos | Sí | Sí | Sí |
| Reportes | Sí | Sí | Sí (+ exportar usuarios) |
| Diagnóstico | No | Sí | Sí |
| Usuarios | No | No | Sí |

## 5. Interfaz principal
Menú lateral contraíble con las vistas de tu rol, tu nombre y rol, y el botón **Cerrar sesión**.

### Inicio (panel)
- Tarjetas en vivo (cada 2 s): procesador, memoria, disco y batería.
- Gráfico de uso de procesador y memoria de los últimos 2 minutos.
- Estado de Ollama y cuántos modelos tiene.
- Actividad reciente (inicios de sesión y herramientas que usó el asistente).
- Datos del equipo y accesos rápidos (asistente, diagnóstico, carpeta de datos, capturas y notas).

### Asistente
Chat por voz y texto. Debajo hay botones de sugerencia (hora, estado del equipo, abrir
descargas, captura, calcular). Cada vez que el asistente usa una herramienta aparece una
etiqueta en el chat.

### Historial
Cada conversación con el asistente se guarda automáticamente (solo en tu equipo y solo la ves
tú). Puedes buscar por título o por el contenido de los mensajes, leerla completa, **exportarla
a PDF** o eliminarla. En el Asistente, **Nueva conversación** guarda la actual y empieza otra.

### Documentos
Tu biblioteca personal para estudiar:
1. Pulsa **Importar documento** y elige un PDF, Word (.docx), texto (.txt, .md) o una foto
   (.png, .jpg). Las imágenes y los PDF escaneados se leen con **OCR** (español).
2. Atlas divide el texto en fragmentos y calcula su significado con `nomic-embed-text`
   (tarda unos segundos según el tamaño; máximo 25 MB).
3. Usa **Buscar** para encontrar pasajes por significado, o pregúntale al asistente:
   "Según mis documentos, ¿cuántas capas tiene el modelo OSI?". Responde con lo que dicen tus apuntes.
Solo se guarda el texto extraído (el archivo original no se copia ni se modifica) y cada usuario
ve únicamente sus documentos.

### Reportes
- **Actividad** (Excel): tus acciones; el administrador exporta la de todos.
- **Informe del equipo** (PDF): procesador, memoria, disco y batería del momento.
- **Usuarios** (Excel, solo administrador): sin contraseñas.
- Las conversaciones se exportan a PDF desde **Historial**.
Tú eliges dónde guardar cada archivo y se abre al terminar.

### Diagnóstico (docente y administrador)
Revisa Java, sistema, carpeta de datos, Ollama y modelos, Vosk, Piper, micrófono, audio,
Tesseract y comandos, con el comando exacto para instalar lo que falte.

### Usuarios (administrador)
Tabla de cuentas: crear usuario, activar/desactivar, cambiar rol y restablecer contraseña. No se
puede desactivar tu propia cuenta ni al último administrador.

## 6. Herramientas del asistente
Pídelas con tus propias palabras:

| Qué decir | Qué hace |
|---|---|
| "¿Qué hora es?" | Fecha y hora actuales. |
| "¿Cómo está mi equipo?" | Procesador, memoria, disco y batería. |
| "¿Cuánto es 15 por 12 más 3?" | Calculadora propia (no calcula "de memoria"). |
| "Abre la carpeta de descargas" | Abre documentos, descargas, escritorio, imágenes o capturas. |
| "¿Qué archivos hay en documentos?" | Lista hasta 15 elementos de esa carpeta. |
| "Según mis documentos, ..." | Busca en tu biblioteca de Documentos y responde con lo encontrado. |
| "Abre la calculadora" | Calculadora, bloc de notas, paint, explorador o navegador. |
| "Sube el volumen" / "silencia" | Cambia el volumen (pasos de 2 %). |
| "Toma una captura de pantalla" | Guarda un PNG en `Imágenes\Atlas`. |
| "Guarda una nota: ..." | Crea un .txt en `.atlas\documentos\notas`. |
| "Bloquea la pantalla" | Pide confirmación en una ventana antes de bloquear. |

**Seguridad:** solo se tocan las carpetas de `app.atlas.carpetas-permitidas`; el modelo nunca
escribe comandos (solo elige de una lista fija); con `app.atlas.modo-solo-lectura=true` se
bloquean las acciones que modifican el equipo; todo queda en la actividad reciente.

## 7. Errores comunes y soluciones
| Mensaje | Solución |
|---|---|
| Usuario o contraseña incorrectos | Revisa los datos; tras 5 fallos espera 60 s. |
| No pude conectar con Ollama | Abre Ollama (http://localhost:11434). |
| No se encontró el modelo de Vosk | Ejecuta `scripts\instalar-dependencias.ps1`. |
| No te escuché | Revisa el micrófono y vuelve a pulsarlo. |
| "El modo solo lectura está activo" | Pon `app.atlas.modo-solo-lectura=false`. |
| Respuesta lenta | El modelo corre en CPU; espera o cierra otros programas. |

## 8. Consejos de buen uso
- Cambia las contraseñas de demostración antes de compartir el equipo.
- Habla cerca del micrófono y haz pausas al terminar la frase.
- Revisa **Diagnóstico** después de instalar o actualizar algo.
- Los registros están en `C:\Users\<usuario>\.atlas\logs\atlas.log`.
