# Changelog

Todos los cambios de Atlas, organizados por fase.

## [Archivos] El asistente maneja todo tu equipo - 2026-11-24

### Cambiado
- [Docs] Manual de Usuario (30 p.) y Guía de Ejecución (15 p.) actualizados: voz con Whisper, botón Interrumpir/Esc, modo flotante, manejo de archivos y reglas de seguridad.
- Se eliminó el límite de «carpetas permitidas»: el asistente trabaja con **todas tus carpetas y discos** (C:, D:, OneDrive...).
  `app.atlas.carpetas-permitidas` ahora solo da nombres a carpetas propias; se pueden proteger más con `app.atlas.carpetas-protegidas`.

### Agregado
- `HerramientasArchivos`: abrir carpetas y archivos, listar, buscar por nombre, leer (texto, PDF, Word, imágenes con OCR),
  guardar y agregar a archivos, crear carpetas, copiar, mover, renombrar y eliminar (siempre a la papelera, con confirmación).
- `CarpetasConocidas` y `ResolvedorRutas`: entienden «documentos», «Downloads», «mis descargas», «escritorio/redes», rutas
  completas, «disco D» y carpetas por su nombre (consulta a Windows dónde están realmente, incluido OneDrive).
- `GuardiaRutas`: zonas protegidas (Windows, Archivos de programa, AppData, .ssh/.aws, perfiles de otros usuarios, datos de
  Atlas); no crea, reemplaza ni abre programas o scripts (.exe, .bat, .ps1...); no borra carpetas principales; pide
  confirmación para reemplazar, para borrar y para escribir fuera de tu usuario en el disco del sistema.
- `Papelera`: eliminar usa la papelera de reciclaje (reversible).
- Pruebas: seguridad de rutas (10), resolución de rutas (5), herramientas de archivos (20), papelera real y una prueba con el
  modelo real de 10 pedidos de archivos (todos resueltos).

### Corregido
- Los archivos pequeños se mostraban como «0 MB»; ahora «350 KB» o «12 B».
- Las instrucciones del asistente obligaban a buscar en documentos con solo mencionar «archivos»; ahora solo para preguntas sobre el
  contenido de tus apuntes.
## [Asistente] Interrumpir y modo flotante - 2026-11-24

### Agregado
- **Botón Interrumpir** (rojo) y tecla **Esc**: cortan a Atlas mientras escucha, piensa o habla. La llamada al modelo corre
  en un hilo propio que se abandona al instante, las 11 herramientas comprueban la cancelación antes de actuar y la memoria
  de la conversación solo se guarda si el turno termina bien. El pedido interrumpido vuelve al cuadro de texto para corregirlo.
- **Modo flotante**: ventana pequeña, sin bordes y siempre por encima de las demás, con el micrófono, interrumpir, la última
  respuesta y un cuadro para escribir. Se activa con «Modo flotante» (menú lateral o vista Asistente) y se vuelve a la ventana
  completa con el botón ↗. Se mueve arrastrando su barra.
- El orquestador admite varios oyentes (ventana principal y modo flotante a la vez).
- Pruebas: interrupción mientras piensa y mientras escucha, memoria sin contaminar, herramientas bloqueadas tras interrumpir,
  varios oyentes y carga de la vista del modo flotante.
## [Voz 2] Whisper para frases libres - 2026-11-24

### Agregado
- `ReconocedorWhisper`: las frases libres se transcriben con Whisper (whisper.cpp, sin Internet, modelo base de 141 MB)
  y una pista de vocabulario. Con tu micrófono Vosk oía «teníamos un resumen» y Whisper «Quiero que me hagas un resumen».
  Las órdenes conocidas siguen resolviéndose al instante con el reconocedor de comandos. Si Whisper no está instalado
  se usa solo Vosk. Propiedades `app.atlas.voz.usar-whisper`, `whisper-ejecutable`, `whisper-modelo`, `whisper-hilos`.
- `config/application.properties`: configuración local de este equipo (Whisper está en D:).

### Corregido
- El clic del ratón al pulsar el micrófono (nivel ~3000 en los primeros 0,4 s) falseaba la medición del ruido y le llegaba a
  Vosk, que inventaba palabras. Ahora se ignora el arranque y el ruido de fondo se mide como el mínimo de los últimos 2 s.
## [Voz] Reconocimiento de voz mejorado - 2026-11-24

### Corregido
- **Se perdía lo que decías**: cuando Vosk cerraba la frase en una pausa natural, el texto reconocido se descartaba
  y Atlas respondía «No te escuché». Ahora se conservan todos los segmentos.
- El umbral fijo de silencio (500) no servía para micrófonos de nivel bajo; el "pop" de arranque del micrófono
  podía confundirse con voz.

### Agregado
- `ProcesadorFrase`: filtro paso alto, umbral de voz adaptativo (mide el ruido de fondo), control automático de
  ganancia, fin de frase por silencio o antes si ya se reconoció una orden completa.
- Reconocedor de comandos de vocabulario acotado (`GramaticaComandos`) en paralelo al libre: «abre la carpeta de
  descargas» se entendió al 93 % donde el libre oía «estorbos». Solo acepta frases exactas y coherentes con el libre.
- Si la confianza es baja, el texto se coloca en el cuadro para corregirlo antes de enviarlo.
- `NormalizadorTranscripcion` (¿Qué hora es?) e instrucciones al modelo sobre errores de transcripción de voz.
- Propiedades `app.atlas.voz.microfono`, `ganancia-maxima`, `confianza-minima`, `usar-comandos`,
  `guardar-audio-depuracion`; `umbral-silencio=0` ahora es automático. El registro muestra ruido, umbral, ganancia y texto.
- Pruebas con audio de Piper degradado (volumen bajo, ruido, pausa a mitad de frase, pop de arranque) y una prueba
  opcional con tu propia grabación (`-Datlas.wav=ruta`).
## [Documentación] Manual de Usuario y Guía de Ejecución en Word - 2026-11-24

### Agregado
- `docs/Manual_de_Usuario_Atlas.docx` (27 páginas): cada pantalla con captura real y marcas numeradas con
  flechas, tablas de explicación, pasos, roles, solución de problemas, privacidad y glosario.
- `docs/Guia_de_Ejecucion_Atlas.docx` (13 páginas): requisitos, instalación de Java y Ollama, modelos,
  compilación, ejecución, pruebas, configuración, datos y problemas comunes.
- `docs/capturas/`: capturas generadas desde la aplicación real con `GeneradorCapturasManual`
  (`mvnw test -Dtest=GeneradorCapturasManual -Datlas.capturas=true`); `completas/` guarda las de acceso sin recortar.
- `scripts/iniciar-atlas.bat`: inicia Atlas con doble clic.

### Corregido
- Un estudiante veía la actividad reciente de otros usuarios en Inicio: ahora solo ve la suya (el administrador ve todas).
- En Documentos, el mensaje «Buscando...» se quedaba fijo al terminar la búsqueda.
## [Mejoras 2] Historial, documentos con OCR y reportes - 2026-11-24

### Agregado
- **Historial**: cada conversación se guarda por usuario (`Conversacion`, `Mensaje`); búsqueda por
  título o contenido, lectura, eliminación y exportación a PDF; botón "Nueva conversación".
- **Documentos**: importar PDF, Word, texto e imágenes; OCR con Tesseract (también PDF escaneados,
  hasta 15 páginas); troceado, embeddings con `nomic-embed-text` y búsqueda semántica por coseno.
  Cada usuario ve solo sus documentos. Nueva herramienta del asistente `buscarEnDocumentos`.
- **Reportes**: actividad (Excel), usuarios (Excel, solo administrador, sin contraseñas), informe del
  equipo (PDF) y conversación (PDF), con OpenPDF y Apache POI.
- Pruebas: historial y permisos, documentos (con embeddings simulados), extractor (txt, docx, pdf y
  OCR real), reportes (PDF y Excel), monitor del sistema bajo carga, y una prueba opcional de punta
  a punta con Ollama (`-Datlas.ia=true`).

### Corregido
- El asistente respondía de memoria en vez de consultar los documentos: reforzado el prompt.
- Formato de decimales con coma fija en tamaños de memoria y disco.
## [Mejoras] Acceso, panel de inicio y herramientas - 2026-11-23

### Agregado
- **Acceso con roles** (ESTUDIANTE, DOCENTE, ADMINISTRADOR): pantalla de login y autoregistro de
  estudiantes, contraseñas con BCrypt, bloqueo de 60 s tras 5 intentos fallidos, cierre de sesión
  (limpia también la memoria del chat) y menú según el rol.
- **Vista Usuarios** (administrador): crear, activar/desactivar, cambiar rol y restablecer contraseña,
  sin dejar el sistema sin administrador activo.
- **Vista Inicio**: métricas en vivo con OSHI (procesador, memoria, disco, batería), gráfico de los
  últimos 2 minutos, estado de Ollama, actividad reciente, datos del equipo y accesos rápidos.
- **Herramientas del asistente** (LangChain4j `@Tool`): fecha y hora, estado del equipo, calculadora
  propia, listar y abrir carpetas permitidas, abrir aplicaciones de una lista fija, volumen, captura
  de pantalla, notas y bloqueo de pantalla con confirmación. Respetan `modo-solo-lectura` y quedan
  en la bitácora; el chat muestra qué herramienta usó el modelo y hay botones de sugerencia.
- Entidades `Usuario` y `RegistroActividad`, cuentas de demostración (`app.seeder.enabled`).
- Pruebas: calculadora, autenticación y roles, herramientas (permisos, solo lectura, confirmación),
  carga real de las 6 vistas FXML y una prueba opcional con el modelo real (`-Datlas.ia=true`).
- Encabezado de autores `K (Kevin) y Y (Yenifher)` en todas las clases y manual/guía en `docs/`.
## [Fase 0] Entorno - 2026-11-19

### Agregado
- Proyecto Maven `pe.upeu:atlas` (Spring Boot 3.3.4, Java 17, sin servidor web) con las
  dependencias del proyecto: JPA + SQLite, Bean Validation, spring-security-crypto, JavaFX 21,
  AtlantaFX, Ikonli Feather, LangChain4j 0.36.2 + Ollama, Vosk 0.3.45, Tess4J 5.13, OSHI 6.6,
  JNativeHook 2.2, PDFBox 3, Apache POI, OpenPDF. JNA fijado en 5.15 para Vosk, OSHI y Tess4J.
- Estructura de paquetes: `model`, `repository`, `service`, `asistente` (+ `herramientas`),
  `seguridad`, `plataforma`, `voz`, `ia`, `controller`, `ui`, `config`, `exception`, `seeder`.
- Arranque Spring + JavaFX (`Launcher` → `JavaFxApplication` → `AtlasApplication`): los
  controladores FXML son beans de Spring; tema PrimerLight/PrimerDark según `app.atlas.tema`.
- `PropiedadesAtlas` (`app.atlas.*`, validadas): rutas de modelos, carpeta de capturas
  (`~/Imágenes/Atlas`), carpetas permitidas por defecto, modo solo lectura, atajo, modelo de
  visión, Tesseract (`ocr.*`) y voz. `ConfiguracionAplicacion` crea las carpetas al iniciar.
- `plataforma/DetectorEntorno`: detecta SO, sesión X11/Wayland (`XDG_SESSION_TYPE`, con
  `WAYLAND_DISPLAY`/`DISPLAY` como respaldo), escritorio y qué comandos de la lista blanca
  (`ComandoSistema.CATALOGO`) hay en el PATH, sin ejecutar ninguno.
- `ia/VerificadorDependencias` + vista **Diagnóstico**: Java, SO/sesión, carpeta de datos,
  Ollama y sus modelos (LLM, embeddings, visión opcional), Vosk, Piper y su voz, micrófono,
  salida de audio, `spa.traineddata` y comandos del SO, con el comando exacto para instalar
  lo que falta (botón para copiarlo).
- Ventana principal con menú lateral contraíble (animado), vistas cargadas una vez y
  guardadas en caché, interfaz `Refrescable` y `ModalPane` para diálogos.
- Excepciones: `NegocioException`, `PermisoDenegadoException`, `PlataformaException`,
  `VozException`, `IaException`.
- `scripts/instalar-dependencias.sh` (apt/dnf según X11 o Wayland + modelos) y
  `scripts/instalar-dependencias.ps1` (Windows). Descargan una sola vez Vosk, Piper, la voz,
  `spa.traineddata` y los modelos de Ollama; el modelo de visión es opcional
  (`ATLAS_VISION=1` / `-Vision`). Reemplazan a `instalar-modelos.*`.
- Logs en consola y en `~/.atlas/logs/atlas.log` (rotación de 5 MB, 7 archivos).
- Pruebas: `DetectorEntornoTest` (7), `VerificadorDependenciasTest` (5, con un Ollama
  simulado mediante `HttpServer` del JDK) y prueba de arranque del contexto. Perfil `test`
  con SQLite en memoria y carpetas temporales.

### Adelantado (provisional)
- Chat por voz y texto en la vista **Asistente**: `ReconocedorVoz` (Vosk + VAD por energía),
  `SintetizadorVoz` (Piper, interrumpible) y `OrquestadorConversacion` (LangChain4j + Ollama,
  memoria de 20 mensajes). Se reemplaza por `OrquestadorAsistente` con herramientas en la
  Fase 1 y se completa con las interfaces intercambiables en la Fase 3.

### Corregido
- `application.properties` se lee como ISO-8859-1: las rutas con tilde se escriben como
  `á` (antes se creaba `~/ImÃ¡genes`). Hay una prueba que lo verifica.

### Pospuesto
- `Platform.setImplicitExit(false)` pasa a la Fase 7, junto con el icono de bandeja: sin
  bandeja, cerrar la ventana dejaría la app viva e inaccesible.
