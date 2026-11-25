# Guía para ejecutar "Atlas"

Autores: K (Kevin) y Y (Yenifher)

## Requisitos previos
- Java 17 o superior (desarrollado y probado con JDK 21): https://adoptium.net
- Ollama instalado (https://ollama.com/download).
- Internet solo para la primera instalación de modelos.

## Ejecución desde el código fuente
1. Descomprime el proyecto (o clónalo).
2. Define `JAVA_HOME` apuntando a tu JDK (ej. `C:\Program Files\Java\jdk-21.0.10`).
3. Instala los modelos una sola vez:
   `powershell -ExecutionPolicy Bypass -File scripts\instalar-dependencias.ps1`
4. Compila (Maven viene incluido con `mvnw`, no hace falta instalarlo):
   `.\mvnw.cmd -DskipTests package`
5. Ejecuta:
   `java -jar target\atlas-0.1.0.jar`

Para correr las pruebas: `.\mvnw.cmd test`.

## Ejecución desde el JAR
Con el archivo `atlas-0.1.0.jar` ya generado, basta con `java -jar atlas-0.1.0.jar`.
Mantén Ollama abierto mientras uses el asistente.

## Posibles advertencias o errores comunes
| Problema | Solución |
|---|---|
| `JAVA_HOME environment variable is not defined correctly` | Define `JAVA_HOME` con la carpeta del JDK. |
| Ollama no responde | Abre la aplicación Ollama y verifica http://localhost:11434. |
| Falta el modelo de Vosk / Piper / Tesseract | Vuelve a ejecutar `instalar-dependencias.ps1` (omite lo ya instalado). |
| Aviso "Unsupported JavaFX configuration" en consola | Es normal al ejecutar desde el JAR; no afecta el funcionamiento. |
| Advertencia de Windows SmartScreen | El JAR no está firmado; ejecútalo solo si confías en su origen. |
