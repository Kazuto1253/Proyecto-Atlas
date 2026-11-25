// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente.herramientas;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.plataforma.Lanzador;
import pe.upeu.atlas.plataforma.Papelera;
import pe.upeu.atlas.plataforma.ResolvedorRutas;
import pe.upeu.atlas.seguridad.ConfirmadorAcciones;
import pe.upeu.atlas.seguridad.GuardiaRutas;
import pe.upeu.atlas.seguridad.GuardiaRutas.Operacion;
import pe.upeu.atlas.service.ExtractorTexto;
import pe.upeu.atlas.service.MonitorSistema;
import pe.upeu.atlas.service.ServicioActividad;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static pe.upeu.atlas.plataforma.CarpetasConocidas.normalizar;

/**
 * Manejo de archivos y carpetas por voz o texto: abrir, listar, buscar, leer, guardar, copiar, mover, renombrar y
 * eliminar. Funciona en todo el equipo (todos los discos); {@link GuardiaRutas} protege el sistema, las credenciales
 * y los programas. Borrar solo envía a la papelera y siempre pide confirmación.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HerramientasArchivos {

    private static final int MAX_LISTA = 40;
    private static final int MAX_RESULTADOS = 20;
    private static final int MAX_CARACTERES_LECTURA = 3500;
    private static final long MAX_BYTES_LECTURA = 25L * 1024 * 1024;
    private static final int MAX_CARACTERES_ESCRITURA = 1_000_000;
    private static final long TIEMPO_BUSQUEDA_MS = 10_000; // K
    private static final int MAX_ELEMENTOS_COPIA = 5000;
    private static final String CANCELADO = HerramientasSistema.MENSAJE_CANCELADO; // K

    private static final Set<String> TEXTO_PLANO = Set.of("txt", "md", "csv", "json", "xml", "html", "htm", "css", "java",
            "py", "c", "cpp", "h", "cs", "sql", "log", "ini", "yaml", "yml", "properties", "tex", "tsv", "ts", "tsx", "jsx",
            "php", "rb", "go", "rs", "kt", "gradle", "toml", "cfg", "conf", "env", "bib", "srt");
    private static final Set<String> CON_EXTRACTOR = Set.of("pdf", "docx", "png", "jpg", "jpeg");

    private final PropiedadesAtlas propiedades;
    private final ResolvedorRutas rutas;
    private final GuardiaRutas guardia; // K
    private final Lanzador lanzador; // K
    private final Papelera papelera;
    private final ConfirmadorAcciones confirmador; // K
    private final ServicioActividad actividad; // Y
    private final ExtractorTexto extractor; // Y

    private volatile Consumer<String> notificador = t -> { }; // Y

    public void setNotificador(Consumer<String> notificador) {
        this.notificador = notificador == null ? t -> { } : notificador;
    }

    // ===== Ver =====

    @Tool("Abre una carpeta o un archivo (documento, PDF, imagen, hoja de cálculo...) en el programa predeterminado. "
            + "La ruta puede ser un nombre de carpeta (documentos, descargas, escritorio, imágenes, música, videos, "
            + "usuario), una subcarpeta (escritorio/redes) o una ruta completa (D:\\Proyectos).")
    public String abrirRuta(@P("Carpeta o archivo a abrir") String ruta) {
        if (cancelado()) {
            return CANCELADO;
        } // K
        try {
            Path p = guardia.verificar(rutas.resolver(ruta), Operacion.LEER); // K
            if (!Files.exists(p)) { // K
                return "No existe: " + p + ". Usa buscarArchivos si no sabes dónde está."; // K
            }
            if (Files.isRegularFile(p)) { // K
                guardia.verificarNoEjecutable(p);
            }
            lanzador.abrir(p);
            usado("Abrió " + nombre(p), p.toString()); // Y
            return "Abrí " + (Files.isDirectory(p) ? "la carpeta " : "el archivo ") + nombre(p) + ".";
        } catch (PermisoDenegadoException | NegocioException e) {
            return e.getMessage();
        } catch (RuntimeException e) { // Y
            return fallo("abrir", e); // K
        }
    }

    @Tool("Lista lo que hay dentro de una carpeta (archivos y subcarpetas, con su tamaño). "
            + "Úsala para ver qué archivos tiene el usuario en documentos, descargas, escritorio, un disco, etc.")
    public String listarContenido(@P("Carpeta a listar, por ejemplo descargas o D:\\Proyectos") String carpeta) {
        if (cancelado()) {
            return CANCELADO;
        }
        try {
            Path dir = guardia.verificar(rutas.resolver(carpeta), Operacion.LEER); // K
            if (!Files.isDirectory(dir)) { // K
                return Files.exists(dir) ? nombre(dir) + " es un archivo, no una carpeta." : "No existe la carpeta " + dir + "."; // K
            }
            List<Path> hijos;
            try (Stream<Path> s = Files.list(dir)) {
                hijos = s.filter(h -> !oculto(h))
                        .sorted(Comparator.comparing((Path h) -> !Files.isDirectory(h)).thenComparing(h -> normalizar(nombre(h))))
                        .toList();
            }
            usado("Listó " + nombre(dir), dir.toString());
            if (hijos.isEmpty()) { // Y
                return "La carpeta " + nombre(dir) + " está vacía."; // Y
            }
            StringBuilder sb = new StringBuilder("En " + dir + " hay " + hijos.size() + " elementos:\n");
            for (Path h : hijos.stream().limit(MAX_LISTA).toList()) {
                sb.append("- ").append(nombre(h));
                if (Files.isDirectory(h)) { // K
                    sb.append(" (carpeta)"); // K
                } else {
                    sb.append(" (").append(MonitorSistema.bytes(tamano(h))).append(')');
                }
                sb.append('\n');
            }
            if (hijos.size() > MAX_LISTA) { // Y
                sb.append("... y ").append(hijos.size() - MAX_LISTA).append(" más.");
            }
            return sb.toString(); // K
        } catch (PermisoDenegadoException | NegocioException e) {
            return e.getMessage();
        } catch (IOException | RuntimeException e) {
            return fallo("listar la carpeta", e);
        } // K
    }

    @Tool("Busca archivos o carpetas por parte de su nombre. Si no se indica carpeta, busca en todo el usuario "
            + "(documentos, escritorio, descargas...). Devuelve las rutas completas.")
    public String buscarArchivos(@P("Parte del nombre, por ejemplo tarea redes") String nombre,
                                 @P("Carpeta o disco donde buscar (opcional; vacío = todo el usuario), por ejemplo D:\\") String carpeta) {
        if (cancelado()) { // Y
            return CANCELADO; // K
        } // Y
        try {
            List<String> palabras = Arrays.stream(normalizar(nombre).split("[^a-z0-9ñ]+")).filter(p -> p.length() >= 2).toList();
            if (palabras.isEmpty()) { // Y
                return "Dime qué nombre buscar.";
            }
            Path raiz = carpeta == null || carpeta.isBlank()
                    ? guardia.verificar(rutas.resolver("usuario"), Operacion.LEER)
                    : guardia.verificar(rutas.resolver(carpeta), Operacion.LEER); // Y
            List<Path> encontrados = new ArrayList<>(); // Y
            long limite = System.currentTimeMillis() + TIEMPO_BUSQUEDA_MS;
            boolean[] cortada = {false}; // K
            Files.walkFileTree(raiz, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 8, new SimpleFileVisitor<>() { // Y
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) { // Y
                    if (!dir.equals(raiz) && guardia.debeOmitirseAlBuscar(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    } // Y
                    return alTerminar() ? FileVisitResult.TERMINATE : coincide(dir); // K
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) { // K
                    return alTerminar() ? FileVisitResult.TERMINATE : coincide(file); // Y
                } // K

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                private boolean alTerminar() {
                    if (cancelado() || System.currentTimeMillis() > limite || encontrados.size() >= MAX_RESULTADOS) {
                        cortada[0] = System.currentTimeMillis() > limite;
                        return true;
                    } // Y
                    return false;
                } // Y

                private FileVisitResult coincide(Path p) {
                    String n = normalizar(nombre(p)); // Y
                    if (palabras.stream().allMatch(n::contains) && !p.equals(raiz)) {
                        encontrados.add(p);
                    } // Y
                    return FileVisitResult.CONTINUE; // K
                }
            });
            usado("Buscó archivos", nombre);
            if (encontrados.isEmpty()) {
                return "No encontré nada que se llame como «" + nombre + "» en " + raiz
                        + (cortada[0] ? " (la búsqueda se cortó por tiempo; prueba en una carpeta más específica)." : "."); // Y
            } // Y
            StringBuilder sb = new StringBuilder("Encontré " + encontrados.size() + " resultado(s):\n");
            for (Path p : encontrados) { // K
                sb.append("- ").append(p).append(Files.isDirectory(p) ? " (carpeta)" : " (" + MonitorSistema.bytes(tamano(p)) + ")").append('\n');
            }
            return sb.toString(); // K
        } catch (PermisoDenegadoException | NegocioException e) {
            return e.getMessage();
        } catch (IOException | RuntimeException e) {
            return fallo("buscar", e); // Y
        } // Y
    }

    @Tool("Lee el contenido de un archivo para poder responder sobre él o resumirlo: texto, Markdown, CSV, código, "
            + "PDF, Word (.docx) o imágenes con texto (usa OCR). Devuelve el principio del contenido.")
    public String leerArchivo(@P("Archivo a leer, por ejemplo documentos/tarea.pdf o D:\\apuntes.txt") String ruta) {
        if (cancelado()) {
            return CANCELADO;
        }
        try {
            Path p = guardia.verificar(rutas.resolver(ruta), Operacion.LEER); // K
            if (Files.isDirectory(p)) { // Y
                return nombre(p) + " es una carpeta; usa listarContenido para ver lo que tiene."; // K
            }
            if (!Files.isRegularFile(p)) { // Y
                return "No existe el archivo " + p + ". Usa buscarArchivos si no sabes dónde está."; // K
            }
            if (Files.size(p) > MAX_BYTES_LECTURA) { // K
                return "El archivo es demasiado grande para leerlo (más de 25 MB).";
            }
            String ext = GuardiaRutas.extension(p); // Y
            String texto;
            if (CON_EXTRACTOR.contains(ext)) {
                texto = extractor.extraer(p); // Y
            } else if (TEXTO_PLANO.contains(ext) || ext.isEmpty()) { // K
                texto = leerTexto(p);
            } else {
                return "No sé leer archivos ." + ext + ". Puedo leer texto, PDF, Word e imágenes con texto.";
            } // Y
            usado("Leyó " + nombre(p), p.toString());
            String limpio = texto.strip(); // K
            if (limpio.isEmpty()) {
                return "El archivo " + nombre(p) + " no tiene texto legible.";
            } // K
            if (limpio.length() <= MAX_CARACTERES_LECTURA) { // K
                return "Contenido de " + nombre(p) + ":\n" + limpio; // Y
            } // K
            return "Contenido de " + nombre(p) + " (primeros " + MAX_CARACTERES_LECTURA + " de " + limpio.length()
                    + " caracteres):\n" + limpio.substring(0, MAX_CARACTERES_LECTURA);
        } catch (PermisoDenegadoException | NegocioException e) {
            return e.getMessage();
        } catch (IOException | RuntimeException e) { // K
            return fallo("leer el archivo", e); // K
        } // K
    } // K

    // ===== Crear y modificar =====

    @Tool("Crea una carpeta nueva (y las carpetas intermedias que falten). Ejemplo: escritorio/redes o D:\\Proyectos\\atlas.")
    public String crearCarpeta(@P("Ruta de la carpeta a crear") String ruta) {
        if (cancelado()) {
            return CANCELADO; // K
        }
        String bloqueo = bloqueoSoloLectura();
        if (bloqueo != null) { // K
            return bloqueo;
        }
        try {
            Path p = guardia.verificar(rutas.resolver(ruta), Operacion.ESCRIBIR);
            if (Files.isDirectory(p)) {
                return "La carpeta " + p + " ya existía.";
            } // Y
            if (Files.exists(p)) { // K
                return "Ya existe un archivo con ese nombre: " + p;
            } // K
            if (guardia.pideConfirmacionPorUbicacion(p)
                    && !confirmador.confirmar("¿Crear carpeta?", "Atlas quiere crear la carpeta:\n" + p)) { // K
                return "Cancelé la creación de la carpeta porque no lo confirmaste.";
            } // K
            Files.createDirectories(p);
            usado("Creó la carpeta " + nombre(p), p.toString());
            return "Creé la carpeta " + p + ".";
        } catch (PermisoDenegadoException | NegocioException e) {
            return e.getMessage();
        } catch (IOException | RuntimeException e) {
            return fallo("crear la carpeta", e); // K
        } // K
    } // Y

    @Tool("Guarda un archivo de texto con el contenido que se indique (apuntes, listas, resúmenes, código, CSV...) en una carpeta. "
            + "Crea la carpeta si no existe. Si ya existe un archivo con ese nombre, pide confirmación para reemplazarlo.")
    public String guardarArchivo(@P("Carpeta donde guardar: documentos, escritorio, descargas, D:\\Proyectos...") String carpeta,
                                 @P("Nombre del archivo con extensión, por ejemplo lista-compras.txt (sin extensión se usa .txt)") String nombreArchivo,
                                 @P("Todo el texto que debe llevar el archivo") String contenido) {
        if (cancelado()) { // K
            return CANCELADO; // K
        } // Y
        String bloqueo = bloqueoSoloLectura();
        if (bloqueo != null) {
            return bloqueo;
        }
        try {
            Path destino = prepararArchivo(carpeta, nombreArchivo, contenido);
            boolean existe = Files.exists(destino);
            if (existe && !confirmador.confirmar("¿Reemplazar archivo?", "Ya existe:\n" + destino + "\n\n¿Quieres reemplazarlo?")) { // Y
                return "No reemplacé el archivo porque no lo confirmaste.";
            }
            if (!existe && guardia.pideConfirmacionPorUbicacion(destino)
                    && !confirmador.confirmar("¿Guardar archivo?", "Atlas quiere guardar:\n" + destino)) {
                return "Cancelé el guardado porque no lo confirmaste."; // Y
            }
            Files.createDirectories(destino.getParent());
            Files.writeString(destino, contenido, StandardCharsets.UTF_8); // Y
            usado("Guardó " + nombre(destino), destino.toString());
            return (existe ? "Reemplacé" : "Guardé") + " el archivo " + nombre(destino) + " en " + destino.getParent() + ".";
        } catch (PermisoDenegadoException | NegocioException e) {
            return e.getMessage();
        } catch (IOException | RuntimeException e) {
            return fallo("guardar el archivo", e); // K
        }
    }

    @Tool("Agrega texto al final de un archivo (lo crea si no existe). Útil para añadir cosas a una lista o a unos apuntes.")
    public String agregarAArchivo(@P("Carpeta del archivo") String carpeta,
                                  @P("Nombre del archivo con extensión") String nombreArchivo,
                                  @P("Texto a agregar") String contenido) {
        if (cancelado()) {
            return CANCELADO; // Y
        } // K
        String bloqueo = bloqueoSoloLectura();
        if (bloqueo != null) {
            return bloqueo;
        } // Y
        try {
            Path destino = prepararArchivo(carpeta, nombreArchivo, contenido); // Y
            if (!Files.exists(destino) && guardia.pideConfirmacionPorUbicacion(destino)
                    && !confirmador.confirmar("¿Guardar archivo?", "Atlas quiere crear:\n" + destino)) {
                return "Cancelé el guardado porque no lo confirmaste.";
            }
            Files.createDirectories(destino.getParent());
            String separador = Files.exists(destino) && Files.size(destino) > 0 ? System.lineSeparator() : "";
            Files.writeString(destino, separador + contenido, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND); // K
            usado("Agregó texto a " + nombre(destino), destino.toString());
            return "Agregué el texto a " + nombre(destino) + " en " + destino.getParent() + "."; // Y
        } catch (PermisoDenegadoException | NegocioException e) {
            return e.getMessage();
        } catch (IOException | RuntimeException e) {
            return fallo("agregar al archivo", e);
        }
    } // Y

    @Tool("Copia un archivo o una carpeta completa dentro de otra carpeta (el original se conserva). Si ya hay uno con ese nombre, "
            + "la copia se llama «nombre (2)».")
    public String copiar(@P("Archivo o carpeta a copiar") String origen,
                         @P("Carpeta de destino, por ejemplo escritorio o D:\\Respaldo") String carpetaDestino) {
        return transferir(origen, carpetaDestino, false);
    } // K

    @Tool("Mueve un archivo o una carpeta completa a otra carpeta (ya no queda en su lugar original). Si ya hay uno con ese "
            + "nombre, queda como «nombre (2)».")
    public String mover(@P("Archivo o carpeta a mover") String origen,
                        @P("Carpeta de destino, por ejemplo documentos/redes") String carpetaDestino) {
        return transferir(origen, carpetaDestino, true); // K
    }

    @Tool("Cambia el nombre de un archivo o carpeta. Si el nuevo nombre no tiene extensión, se conserva la del archivo.")
    public String renombrar(@P("Archivo o carpeta a renombrar") String ruta,
                            @P("Nombre nuevo") String nuevoNombre) {
        if (cancelado()) {
            return CANCELADO; // Y
        }
        String bloqueo = bloqueoSoloLectura();
        if (bloqueo != null) {
            return bloqueo; // Y
        }
        try {
            Path p = guardia.verificar(rutas.resolver(ruta), Operacion.ESCRIBIR);
            if (!Files.exists(p)) {
                return "No existe: " + p;
            } // K
            String limpio = nombreSeguro(nuevoNombre); // K
            if (Files.isRegularFile(p) && !limpio.contains(".") && !GuardiaRutas.extension(p).isEmpty()) {
                limpio += "." + GuardiaRutas.extension(p); // Y
            }
            Path destino = guardia.verificar(p.resolveSibling(limpio), Operacion.ESCRIBIR); // K
            if (Files.isRegularFile(p)) {
                guardia.verificarNoEjecutable(destino);
            }
            if (Files.exists(destino)) { // K
                return "Ya existe algo llamado " + limpio + " en esa carpeta.";
            } // Y
            Files.move(p, destino);
            usado("Renombró " + nombre(p), nombre(p) + " → " + limpio);
            return "Renombré " + nombre(p) + " a " + limpio + ".";
        } catch (PermisoDenegadoException | NegocioException e) {
            return e.getMessage();
        } catch (IOException | RuntimeException e) { // K
            return fallo("renombrar", e); // Y
        } // Y
    } // K

    @Tool("Elimina un archivo o carpeta enviándolo a la papelera de reciclaje (se puede restaurar desde allí). "
            + "Siempre pide confirmación al usuario en una ventana.")
    public String eliminar(@P("Archivo o carpeta a eliminar") String ruta) { // Y
        if (cancelado()) {
            return CANCELADO; // K
        }
        String bloqueo = bloqueoSoloLectura();
        if (bloqueo != null) {
            return bloqueo;
        }
        try {
            Path p = guardia.verificar(rutas.resolver(ruta), Operacion.ELIMINAR);
            if (!Files.exists(p)) {
                return "No existe: " + p;
            } // K
            String detalle = Files.isDirectory(p) ? "la carpeta (con " + contar(p) + " elementos)" : "el archivo"; // Y
            if (!confirmador.confirmar("¿Enviar a la papelera?",
                    "Atlas quiere enviar a la papelera de reciclaje " + detalle + ":\n" + p
                            + "\n\nPodrás restaurarlo desde la papelera.")) { // K
                usado("Eliminación cancelada", p.toString()); // Y
                return "No eliminé nada porque no lo confirmaste.";
            } // K
            papelera.enviar(p); // Y
            usado("Envió a la papelera " + nombre(p), p.toString());
            return "Envié " + nombre(p) + " a la papelera de reciclaje; puedes restaurarlo desde allí."; // Y
        } catch (PermisoDenegadoException | NegocioException e) {
            return e.getMessage(); // Y
        } catch (RuntimeException e) {
            return fallo("eliminar", e);
        }
    }

    // ===== Internos =====

    private String transferir(String origen, String carpetaDestino, boolean mover) {
        if (cancelado()) { // Y
            return CANCELADO; // Y
        }
        String bloqueo = bloqueoSoloLectura(); // K
        if (bloqueo != null) {
            return bloqueo;
        }
        try {
            Path desde = guardia.verificar(rutas.resolver(origen), mover ? Operacion.ESCRIBIR : Operacion.LEER);
            if (!Files.exists(desde)) {
                return "No existe: " + desde + ". Usa buscarArchivos si no sabes dónde está."; // K
            }
            Path dir = guardia.verificar(rutas.resolver(carpetaDestino), Operacion.ESCRIBIR);
            if (Files.isRegularFile(dir)) {
                return "El destino " + dir + " es un archivo; indica una carpeta.";
            }
            if (Files.isRegularFile(desde)) { // Y
                guardia.verificarNoEjecutable(desde);
            }
            if (Files.isDirectory(desde) && (dir.equals(desde) || dir.startsWith(desde))) { // K
                return "No puedo " + (mover ? "mover" : "copiar") + " una carpeta dentro de sí misma.";
            }
            if (Files.isDirectory(desde) && contar(desde) > MAX_ELEMENTOS_COPIA) {
                return "La carpeta tiene demasiados elementos (más de " + MAX_ELEMENTOS_COPIA + "); hazlo desde el Explorador."; // K
            }
            if (guardia.pideConfirmacionPorUbicacion(dir)
                    && !confirmador.confirmar(mover ? "¿Mover?" : "¿Copiar?", "Destino fuera de tu usuario:\n" + dir
                    + "\n\n" + (mover ? "Mover" : "Copiar") + ": " + desde)) { // Y
                return "Cancelé la operación porque no lo confirmaste.";
            }
            Files.createDirectories(dir);
            Path destino = unico(dir.resolve(desde.getFileName().toString()));
            if (mover) {
                moverRecursivo(desde, destino);
            } else {
                copiarRecursivo(desde, destino);
            }
            usado((mover ? "Movió " : "Copió ") + nombre(desde), desde + " → " + destino);
            return (mover ? "Moví " : "Copié ") + nombre(desde) + " a " + destino.getParent()
                    + (destino.getFileName().equals(desde.getFileName()) ? "." : " (quedó como " + nombre(destino) + ").");
        } catch (PermisoDenegadoException | NegocioException e) {
            return e.getMessage();
        } catch (IOException | RuntimeException e) {
            return fallo(mover ? "mover" : "copiar", e);
        }
    }

    /** Valida carpeta, nombre y contenido; devuelve la ruta final del archivo. */
    private Path prepararArchivo(String carpeta, String nombreArchivo, String contenido) { // Y
        if (contenido == null) {
            throw new NegocioException("Falta el contenido del archivo."); // K
        }
        if (contenido.length() > MAX_CARACTERES_ESCRITURA) {
            throw new NegocioException("El contenido es demasiado grande (más de 1 millón de caracteres).");
        }
        Path dir = guardia.verificar(rutas.resolver(carpeta), Operacion.ESCRIBIR);
        if (Files.isRegularFile(dir)) {
            throw new NegocioException(dir + " es un archivo; indica una carpeta y el nombre del archivo aparte."); // Y
        }
        String nombre = nombreSeguro(nombreArchivo);
        if (!nombre.contains(".")) {
            nombre += ".txt";
        } // Y
        Path destino = guardia.verificar(dir.resolve(nombre), Operacion.ESCRIBIR);
        guardia.verificarNoEjecutable(destino); // Y
        return destino;
    }

    /** Quita caracteres no válidos en Windows y cualquier intento de salir de la carpeta. */
    static String nombreSeguro(String nombre) {
        String n = nombre == null ? "" : nombre.strip();
        n = n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-").replaceAll("^\\.+", "").replaceAll("[ .]+$", "").strip();
        if (n.isEmpty()) { // K
            throw new NegocioException("Dime con qué nombre guardar el archivo.");
        }
        return n.length() > 120 ? n.substring(0, 120) : n;
    }

    /** Si ya existe, agrega " (2)", " (3)"... antes de la extensión. */
    static Path unico(Path destino) {
        if (!Files.exists(destino)) {
            return destino; // K
        } // K
        String n = destino.getFileName().toString();
        int punto = n.lastIndexOf('.');
        String base = punto > 0 ? n.substring(0, punto) : n;
        String ext = punto > 0 ? n.substring(punto) : ""; // Y
        for (int i = 2; i < 1000; i++) {
            Path candidato = destino.resolveSibling(base + " (" + i + ")" + ext);
            if (!Files.exists(candidato)) {
                return candidato;
            } // Y
        }
        throw new NegocioException("Demasiados archivos con el mismo nombre en el destino.");
    }

    private static void copiarRecursivo(Path desde, Path hasta) throws IOException {
        if (!Files.isDirectory(desde)) {
            Files.copy(desde, hasta, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        } // Y
        Files.walkFileTree(desde, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(hasta.resolve(desde.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, hasta.resolve(desde.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            } // Y
        });
    } // Y

    private static void moverRecursivo(Path desde, Path hasta) throws IOException {
        try {
            Files.move(desde, hasta);
        } catch (IOException e) {
            // Entre discos las carpetas no se pueden mover de golpe: se copia y luego se borra el original
            copiarRecursivo(desde, hasta);
            borrarRecursivo(desde);
        } // Y
    } // K

    private static void borrarRecursivo(Path raiz) throws IOException { // Y
        Files.walkFileTree(raiz, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { // Y
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { // Y
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static long contar(Path carpeta) {
        try (Stream<Path> s = Files.walk(carpeta)) { // Y
            return s.limit(MAX_ELEMENTOS_COPIA + 1L).count() - 1;
        } catch (IOException | RuntimeException e) { // K
            return 0; // Y
        }
    } // Y

    private static String leerTexto(Path p) throws IOException {
        byte[] bytes = Files.readAllBytes(p);
        int n = Math.min(bytes.length, 400_000); // Y
        for (int i = 0; i < Math.min(n, 4096); i++) {
            if (bytes[i] == 0) {
                throw new NegocioException("Ese archivo no es de texto."); // Y
            } // K
        }
        String utf8 = new String(bytes, 0, n, StandardCharsets.UTF_8); // K
        return utf8.indexOf('\uFFFD') >= 0 ? new String(bytes, 0, n, Charset.forName("windows-1252")) : utf8; // K
    } // Y

    private static long tamano(Path p) {
        try { // Y
            return Files.size(p); // Y
        } catch (IOException e) {
            return 0; // K
        } // Y
    } // K

    private static boolean oculto(Path p) { // K
        String n = nombre(p).toLowerCase();
        return n.equals("desktop.ini") || n.equals("thumbs.db") || n.startsWith("$") || n.startsWith("~$");
    } // Y

    private static String nombre(Path p) {
        return p.getFileName() == null ? p.toString() : p.getFileName().toString(); // Y
    }

    private static boolean cancelado() { // K
        return HerramientasSistema.cancelado();
    }

    private String bloqueoSoloLectura() {
        return propiedades.isModoSoloLectura()
                ? "El modo solo lectura está activo, así que no puedo crear, mover ni borrar archivos."
                : null; // K
    } // K

    private void usado(String accion, String detalle) {
        actividad.registrar(accion, detalle);
        try { // Y
            notificador.accept(accion);
        } catch (RuntimeException e) {
            log.debug("El notificador falló", e);
        }
    } // K

    private String fallo(String que, Exception e) {
        log.warn("Falló la herramienta de archivos al {}", que, e);
        return "No pude " + que + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
} // Y
