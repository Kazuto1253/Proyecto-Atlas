// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente.herramientas;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.plataforma.SanchezLanzador;
import pe.upeu.atlas.plataforma.MarcaPapelera;
import pe.upeu.atlas.plataforma.MarcaResolvedorRutas;
import pe.upeu.atlas.seguridad.SanchezConfirmadorAcciones;
import pe.upeu.atlas.seguridad.MarcaGuardiaRutas;
import pe.upeu.atlas.seguridad.MarcaGuardiaRutas.MarcaOperacion;
import pe.upeu.atlas.service.MarcaExtractorTexto;
import pe.upeu.atlas.service.MarcaMonitorSistema;
import pe.upeu.atlas.service.SanchezServicioActividad;

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

import static pe.upeu.atlas.plataforma.MarcaCarpetasConocidas.sancheznormalizar;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarcaHerramientasArchivos {

    private static final int MAX_LISTA = 40;
    private static final int MAX_RESULTADOS = 20;
    private static final int MAX_CARACTERES_LECTURA = 3500;
    private static final long MAX_BYTES_LECTURA = 25L * 1024 * 1024;
    private static final int MAX_CARACTERES_ESCRITURA = 1_000_000;
    private static final long TIEMPO_BUSQUEDA_MS = 10_000; // K
    private static final int MAX_ELEMENTOS_COPIA = 5000;
    private static final String CANCELADO = SanchezHerramientasSistema.MENSAJE_CANCELADO; // K

    private static final Set<String> TEXTO_PLANO = Set.of("txt", "md", "csv", "json", "xml", "html", "htm", "css", "java",
            "py", "c", "cpp", "h", "cs", "sql", "log", "ini", "yaml", "yml", "properties", "tex", "tsv", "ts", "tsx", "jsx",
            "php", "rb", "go", "rs", "kt", "gradle", "toml", "cfg", "conf", "env", "bib", "srt");
    private static final Set<String> CON_EXTRACTOR = Set.of("pdf", "docx", "png", "jpg", "jpeg");

    private final SanchezPropiedadesAtlas marcapropiedades;
    private final MarcaResolvedorRutas sanchezrutas;
    private final MarcaGuardiaRutas sanchezguardia; // K
    private final SanchezLanzador sanchezlanzador; // K
    private final MarcaPapelera marcapapelera;
    private final SanchezConfirmadorAcciones marcaconfirmador; // K
    private final SanchezServicioActividad sanchezactividad; // Y
    private final MarcaExtractorTexto marcaextractor; // Y

    private volatile Consumer<String> marcanotificador = marcat -> { }; // Y

    public void marcasetNotificador(Consumer<String> marcanotificador) {
        this.marcanotificador = marcanotificador == null ? marcat -> { } : marcanotificador;
    }

    @Tool("Abre una carpeta o un archivo (documento, PDF, imagen, hoja de cálculo...) en el programa predeterminado. "
            + "La ruta puede ser un nombre de carpeta (documentos, descargas, escritorio, imágenes, música, videos, "
            + "usuario), una subcarpeta (escritorio/redes) o una ruta completa (D:\\Proyectos).")
    public String abrirRuta(@P("Carpeta o archivo a abrir") String ruta) {
        if (marcacancelado()) {
            return CANCELADO;
        } // K
        try {
            Path marcap = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(ruta), MarcaOperacion.LEER); // K
            if (!Files.exists(marcap)) { // K
                return "No existe: " + marcap + ". Usa buscarArchivos si no sabes dónde está."; // K
            }
            if (Files.isRegularFile(marcap)) { // K
                sanchezguardia.marcaverificarNoEjecutable(marcap);
            }
            sanchezlanzador.marcaabrir(marcap);
            marcausado("Abrió " + nombre(marcap), marcap.toString()); // Y
            return "Abrí " + (Files.isDirectory(marcap) ? "la carpeta " : "el archivo ") + nombre(marcap) + ".";
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (RuntimeException sancheze) { // Y
            return marcafallo("abrir", sancheze); // K
        }
    }

    @Tool("Lista lo que hay dentro de una carpeta (archivos y subcarpetas, con su tamaño). "
            + "Úsala para ver qué archivos tiene el usuario en documentos, descargas, escritorio, un disco, etc.")
    public String listarContenido(@P("Carpeta a listar, por ejemplo descargas o D:\\Proyectos") String carpeta) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        try {
            Path sanchezdir = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(carpeta), MarcaOperacion.LEER); // K
            if (!Files.isDirectory(sanchezdir)) { // K
                return Files.exists(sanchezdir) ? nombre(sanchezdir) + " es un archivo, no una carpeta." : "No existe la carpeta " + sanchezdir + "."; // K
            }
            List<Path> sanchezhijos;
            try (Stream<Path> sanchezs = Files.list(sanchezdir)) {
                sanchezhijos = sanchezs.filter(marcah -> !marcaoculto(marcah))
                        .sorted(Comparator.comparing((Path marcah) -> !Files.isDirectory(marcah)).thenComparing(marcah -> sancheznormalizar(nombre(marcah))))
                        .toList();
            }
            marcausado("Listó " + nombre(sanchezdir), sanchezdir.toString());
            if (sanchezhijos.isEmpty()) { // Y
                return "La carpeta " + nombre(sanchezdir) + " está vacía."; // Y
            }
            StringBuilder sanchezsb = new StringBuilder("En " + sanchezdir + " hay " + sanchezhijos.size() + " elementos:\n");
            for (Path marcah : sanchezhijos.stream().limit(MAX_LISTA).toList()) {
                sanchezsb.append("- ").append(nombre(marcah));
                if (Files.isDirectory(marcah)) { // K
                    sanchezsb.append(" (carpeta)"); // K
                } else {
                    sanchezsb.append(" (").append(MarcaMonitorSistema.sanchezbytes(marcatamano(marcah))).append(')');
                }
                sanchezsb.append('\n');
            }
            if (sanchezhijos.size() > MAX_LISTA) { // Y
                sanchezsb.append("... y ").append(sanchezhijos.size() - MAX_LISTA).append(" más.");
            }
            return sanchezsb.toString(); // K
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("listar la carpeta", sancheze);
        } // K
    }

    @Tool("Busca archivos o carpetas por parte de su nombre. Si no se indica carpeta, busca en todo el usuario "
            + "(documentos, escritorio, descargas...). Devuelve las rutas completas.")
    public String buscarArchivos(@P("Parte del nombre, por ejemplo tarea redes") String nombre,
                                 @P("Carpeta o disco donde buscar (opcional; vacío = todo el usuario), por ejemplo D:\\") String carpeta) {
        if (marcacancelado()) { // Y
            return CANCELADO; // K
        } // Y
        try {
            List<String> marcapalabras = Arrays.stream(sancheznormalizar(nombre).split("[^a-z0-9ñ]+")).filter(marcap -> marcap.length() >= 2).toList();
            if (marcapalabras.isEmpty()) { // Y
                return "Dime qué nombre buscar.";
            }
            Path marcaraiz = carpeta == null || carpeta.isBlank()
                    ? sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver("usuario"), MarcaOperacion.LEER)
                    : sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(carpeta), MarcaOperacion.LEER); // Y
            List<Path> marcaencontrados = new ArrayList<>(); // Y
            long marcalimite = System.currentTimeMillis() + TIEMPO_BUSQUEDA_MS;
            boolean[] marcacortada = {false}; // K
            Files.walkFileTree(marcaraiz, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 8, new SimpleFileVisitor<>() { // Y
                @Override
                public FileVisitResult preVisitDirectory(Path sanchezdir, BasicFileAttributes marcaattrs) { // Y
                    if (!sanchezdir.equals(marcaraiz) && sanchezguardia.sanchezdebeOmitirseAlBuscar(sanchezdir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    } // Y
                    return alTerminar() ? FileVisitResult.TERMINATE : coincide(sanchezdir); // K
                }

                @Override
                public FileVisitResult visitFile(Path marcafile, BasicFileAttributes marcaattrs) { // K
                    return alTerminar() ? FileVisitResult.TERMINATE : coincide(marcafile); // Y
                } // K

                @Override
                public FileVisitResult visitFileFailed(Path marcafile, IOException marcaexc) {
                    return FileVisitResult.CONTINUE;
                }

                private boolean alTerminar() {
                    if (marcacancelado() || System.currentTimeMillis() > marcalimite || marcaencontrados.size() >= MAX_RESULTADOS) {
                        marcacortada[0] = System.currentTimeMillis() > marcalimite;
                        return true;
                    } // Y
                    return false;
                } // Y

                private FileVisitResult coincide(Path marcap) {
                    String marcan = sancheznormalizar(nombre(marcap)); // Y
                    if (marcapalabras.stream().allMatch(marcan::contains) && !marcap.equals(marcaraiz)) {
                        marcaencontrados.add(marcap);
                    } // Y
                    return FileVisitResult.CONTINUE; // K
                }
            });
            marcausado("Buscó archivos", nombre);
            if (marcaencontrados.isEmpty()) {
                return "No encontré nada que se llame como «" + nombre + "» en " + marcaraiz
                        + (marcacortada[0] ? " (la búsqueda se cortó por tiempo; prueba en una carpeta más específica)." : "."); // Y
            } // Y
            StringBuilder sanchezsb = new StringBuilder("Encontré " + marcaencontrados.size() + " resultado(s):\n");
            for (Path marcap : marcaencontrados) { // K
                sanchezsb.append("- ").append(marcap).append(Files.isDirectory(marcap) ? " (carpeta)" : " (" + MarcaMonitorSistema.sanchezbytes(marcatamano(marcap)) + ")").append('\n');
            }
            return sanchezsb.toString(); // K
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("buscar", sancheze); // Y
        } // Y
    }

    @Tool("Lee el contenido de un archivo para poder responder sobre él o resumirlo: texto, Markdown, CSV, código, "
            + "PDF, Word (.docx) o imágenes con texto (usa OCR). Devuelve el principio del contenido.")
    public String leerArchivo(@P("Archivo a leer, por ejemplo documentos/tarea.pdf o D:\\apuntes.txt") String ruta) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        try {
            Path marcap = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(ruta), MarcaOperacion.LEER); // K
            if (Files.isDirectory(marcap)) { // Y
                return nombre(marcap) + " es una carpeta; usa listarContenido para ver lo que tiene."; // K
            }
            if (!Files.isRegularFile(marcap)) { // Y
                return "No existe el archivo " + marcap + ". Usa buscarArchivos si no sabes dónde está."; // K
            }
            if (Files.size(marcap) > MAX_BYTES_LECTURA) { // K
                return "El archivo es demasiado grande para leerlo (más de 25 MB).";
            }
            String sanchezext = MarcaGuardiaRutas.sanchezextension(marcap); // Y
            String marcatexto;
            if (CON_EXTRACTOR.contains(sanchezext)) {
                marcatexto = marcaextractor.sanchezextraer(marcap); // Y
            } else if (TEXTO_PLANO.contains(sanchezext) || sanchezext.isEmpty()) { // K
                marcatexto = marcaleerTexto(marcap);
            } else {
                return "No sé leer archivos ." + sanchezext + ". Puedo leer texto, PDF, Word e imágenes con texto.";
            } // Y
            marcausado("Leyó " + nombre(marcap), marcap.toString());
            String marcalimpio = marcatexto.strip(); // K
            if (marcalimpio.isEmpty()) {
                return "El archivo " + nombre(marcap) + " no tiene texto legible.";
            } // K
            if (marcalimpio.length() <= MAX_CARACTERES_LECTURA) { // K
                return "Contenido de " + nombre(marcap) + ":\n" + marcalimpio; // Y
            } // K
            return "Contenido de " + nombre(marcap) + " (primeros " + MAX_CARACTERES_LECTURA + " de " + marcalimpio.length()
                    + " caracteres):\n" + marcalimpio.substring(0, MAX_CARACTERES_LECTURA);
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) { // K
            return marcafallo("leer el archivo", sancheze); // K
        } // K
    } // K

    @Tool("Crea una carpeta nueva (y las carpetas intermedias que falten). Ejemplo: escritorio/redes o D:\\Proyectos\\atlas.")
    public String crearCarpeta(@P("Ruta de la carpeta a crear") String ruta) {
        if (marcacancelado()) {
            return CANCELADO; // K
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) { // K
            return sanchezbloqueo;
        }
        try {
            Path marcap = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(ruta), MarcaOperacion.ESCRIBIR);
            if (Files.isDirectory(marcap)) {
                return "La carpeta " + marcap + " ya existía.";
            } // Y
            if (Files.exists(marcap)) { // K
                return "Ya existe un archivo con ese nombre: " + marcap;
            } // K
            if (sanchezguardia.marcapideConfirmacionPorUbicacion(marcap)
                    && !marcaconfirmador.sanchezconfirmar("¿Crear carpeta?", "Atlas quiere crear la carpeta:\n" + marcap)) { // K
                return "Cancelé la creación de la carpeta porque no lo confirmaste.";
            } // K
            Files.createDirectories(marcap);
            marcausado("Creó la carpeta " + nombre(marcap), marcap.toString());
            return "Creé la carpeta " + marcap + ".";
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("crear la carpeta", sancheze); // K
        } // K
    } // Y

    @Tool("Guarda un archivo de texto con el contenido que se indique (apuntes, listas, resúmenes, código, CSV...) en una carpeta. "
            + "Crea la carpeta si no existe. Si ya existe un archivo con ese nombre, pide confirmación para reemplazarlo.")
    public String guardarArchivo(@P("Carpeta donde guardar: documentos, escritorio, descargas, D:\\Proyectos...") String carpeta,
                                 @P("Nombre del archivo con extensión, por ejemplo lista-compras.txt (sin extensión se usa .txt)") String nombreArchivo,
                                 @P("Todo el texto que debe llevar el archivo") String contenido) {
        if (marcacancelado()) { // K
            return CANCELADO; // K
        } // Y
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        try {
            Path marcadestino = sanchezprepararArchivo(carpeta, nombreArchivo, contenido);
            boolean marcaexiste = Files.exists(marcadestino);
            if (marcaexiste && !marcaconfirmador.sanchezconfirmar("¿Reemplazar archivo?", "Ya existe:\n" + marcadestino + "\n\n¿Quieres reemplazarlo?")) { // Y
                return "No reemplacé el archivo porque no lo confirmaste.";
            }
            if (!marcaexiste && sanchezguardia.marcapideConfirmacionPorUbicacion(marcadestino)
                    && !marcaconfirmador.sanchezconfirmar("¿Guardar archivo?", "Atlas quiere guardar:\n" + marcadestino)) {
                return "Cancelé el guardado porque no lo confirmaste."; // Y
            }
            Files.createDirectories(marcadestino.getParent());
            Files.writeString(marcadestino, contenido, StandardCharsets.UTF_8); // Y
            marcausado("Guardó " + nombre(marcadestino), marcadestino.toString());
            return (marcaexiste ? "Reemplacé" : "Guardé") + " el archivo " + nombre(marcadestino) + " en " + marcadestino.getParent() + ".";
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("guardar el archivo", sancheze); // K
        }
    }

    @Tool("Agrega texto al final de un archivo (lo crea si no existe). Útil para añadir cosas a una lista o a unos apuntes.")
    public String agregarAArchivo(@P("Carpeta del archivo") String carpeta,
                                  @P("Nombre del archivo con extensión") String nombreArchivo,
                                  @P("Texto a agregar") String contenido) {
        if (marcacancelado()) {
            return CANCELADO; // Y
        } // K
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        } // Y
        try {
            Path marcadestino = sanchezprepararArchivo(carpeta, nombreArchivo, contenido); // Y
            if (!Files.exists(marcadestino) && sanchezguardia.marcapideConfirmacionPorUbicacion(marcadestino)
                    && !marcaconfirmador.sanchezconfirmar("¿Guardar archivo?", "Atlas quiere crear:\n" + marcadestino)) {
                return "Cancelé el guardado porque no lo confirmaste.";
            }
            Files.createDirectories(marcadestino.getParent());
            String sanchezseparador = Files.exists(marcadestino) && Files.size(marcadestino) > 0 ? System.lineSeparator() : "";
            Files.writeString(marcadestino, sanchezseparador + contenido, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND); // K
            marcausado("Agregó texto a " + nombre(marcadestino), marcadestino.toString());
            return "Agregué el texto a " + nombre(marcadestino) + " en " + marcadestino.getParent() + "."; // Y
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("agregar al archivo", sancheze);
        }
    } // Y

    @Tool("Copia un archivo o una carpeta completa dentro de otra carpeta (el original se conserva). Si ya hay uno con ese nombre, "
            + "la copia se llama «nombre (2)».")
    public String copiar(@P("Archivo o carpeta a copiar") String origen,
                         @P("Carpeta de destino, por ejemplo escritorio o D:\\Respaldo") String carpetaDestino) {
        return marcatransferir(origen, carpetaDestino, false);
    } // K

    @Tool("Mueve un archivo o una carpeta completa a otra carpeta (ya no queda en su lugar original). Si ya hay uno con ese "
            + "nombre, queda como «nombre (2)».")
    public String mover(@P("Archivo o carpeta a mover") String origen,
                        @P("Carpeta de destino, por ejemplo documentos/redes") String carpetaDestino) {
        return marcatransferir(origen, carpetaDestino, true); // K
    }

    @Tool("Cambia el nombre de un archivo o carpeta. Si el nuevo nombre no tiene extensión, se conserva la del archivo.")
    public String renombrar(@P("Archivo o carpeta a renombrar") String ruta,
                            @P("Nombre nuevo") String nuevoNombre) {
        if (marcacancelado()) {
            return CANCELADO; // Y
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo; // Y
        }
        try {
            Path marcap = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(ruta), MarcaOperacion.ESCRIBIR);
            if (!Files.exists(marcap)) {
                return "No existe: " + marcap;
            } // K
            String marcalimpio = marcanombreSeguro(nuevoNombre); // K
            if (Files.isRegularFile(marcap) && !marcalimpio.contains(".") && !MarcaGuardiaRutas.sanchezextension(marcap).isEmpty()) {
                marcalimpio += "." + MarcaGuardiaRutas.sanchezextension(marcap); // Y
            }
            Path marcadestino = sanchezguardia.sanchezverificar(marcap.resolveSibling(marcalimpio), MarcaOperacion.ESCRIBIR); // K
            if (Files.isRegularFile(marcap)) {
                sanchezguardia.marcaverificarNoEjecutable(marcadestino);
            }
            if (Files.exists(marcadestino)) { // K
                return "Ya existe algo llamado " + marcalimpio + " en esa carpeta.";
            } // Y
            Files.move(marcap, marcadestino);
            marcausado("Renombró " + nombre(marcap), nombre(marcap) + " → " + marcalimpio);
            return "Renombré " + nombre(marcap) + " a " + marcalimpio + ".";
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) { // K
            return marcafallo("renombrar", sancheze); // Y
        } // Y
    } // K

    @Tool("Elimina un archivo o carpeta enviándolo a la papelera de reciclaje (se puede restaurar desde allí). "
            + "Siempre pide confirmación al usuario en una ventana.")
    public String eliminar(@P("Archivo o carpeta a eliminar") String ruta) { // Y
        if (marcacancelado()) {
            return CANCELADO; // K
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        try {
            Path marcap = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(ruta), MarcaOperacion.ELIMINAR);
            if (!Files.exists(marcap)) {
                return "No existe: " + marcap;
            } // K
            String sanchezdetalle = Files.isDirectory(marcap) ? "la carpeta (con " + sanchezcontar(marcap) + " elementos)" : "el archivo"; // Y
            if (!marcaconfirmador.sanchezconfirmar("¿Enviar a la papelera?",
                    "Atlas quiere enviar a la papelera de reciclaje " + sanchezdetalle + ":\n" + marcap
                            + "\n\nPodrás restaurarlo desde la papelera.")) { // K
                marcausado("Eliminación cancelada", marcap.toString()); // Y
                return "No eliminé nada porque no lo confirmaste.";
            } // K
            marcapapelera.sanchezenviar(marcap); // Y
            marcausado("Envió a la papelera " + nombre(marcap), marcap.toString());
            return "Envié " + nombre(marcap) + " a la papelera de reciclaje; puedes restaurarlo desde allí."; // Y
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage(); // Y
        } catch (RuntimeException sancheze) {
            return marcafallo("eliminar", sancheze);
        }
    }

    private String marcatransferir(String marcaorigen, String marcacarpetaDestino, boolean sanchezmover) {
        if (marcacancelado()) { // Y
            return CANCELADO; // Y
        }
        String sanchezbloqueo = marcabloqueoSoloLectura(); // K
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        try {
            Path sanchezdesde = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(marcaorigen), sanchezmover ? MarcaOperacion.ESCRIBIR : MarcaOperacion.LEER);
            if (!Files.exists(sanchezdesde)) {
                return "No existe: " + sanchezdesde + ". Usa buscarArchivos si no sabes dónde está."; // K
            }
            Path sanchezdir = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(marcacarpetaDestino), MarcaOperacion.ESCRIBIR);
            if (Files.isRegularFile(sanchezdir)) {
                return "El destino " + sanchezdir + " es un archivo; indica una carpeta.";
            }
            if (Files.isRegularFile(sanchezdesde)) { // Y
                sanchezguardia.marcaverificarNoEjecutable(sanchezdesde);
            }
            if (Files.isDirectory(sanchezdesde) && (sanchezdir.equals(sanchezdesde) || sanchezdir.startsWith(sanchezdesde))) { // K
                return "No puedo " + (sanchezmover ? "mover" : "copiar") + " una carpeta dentro de sí misma.";
            }
            if (Files.isDirectory(sanchezdesde) && sanchezcontar(sanchezdesde) > MAX_ELEMENTOS_COPIA) {
                return "La carpeta tiene demasiados elementos (más de " + MAX_ELEMENTOS_COPIA + "); hazlo desde el Explorador."; // K
            }
            if (sanchezguardia.marcapideConfirmacionPorUbicacion(sanchezdir)
                    && !marcaconfirmador.sanchezconfirmar(sanchezmover ? "¿Mover?" : "¿Copiar?", "Destino fuera de tu usuario:\n" + sanchezdir
                    + "\n\n" + (sanchezmover ? "Mover" : "Copiar") + ": " + sanchezdesde)) { // Y
                return "Cancelé la operación porque no lo confirmaste.";
            }
            Files.createDirectories(sanchezdir);
            Path marcadestino = marcaunico(sanchezdir.resolve(sanchezdesde.getFileName().toString()));
            if (sanchezmover) {
                sanchezmoverRecursivo(sanchezdesde, marcadestino);
            } else {
                marcacopiarRecursivo(sanchezdesde, marcadestino);
            }
            marcausado((sanchezmover ? "Movió " : "Copió ") + nombre(sanchezdesde), sanchezdesde + " → " + marcadestino);
            return (sanchezmover ? "Moví " : "Copié ") + nombre(sanchezdesde) + " a " + marcadestino.getParent()
                    + (marcadestino.getFileName().equals(sanchezdesde.getFileName()) ? "." : " (quedó como " + nombre(marcadestino) + ").");
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo(sanchezmover ? "mover" : "copiar", sancheze);
        }
    }

    private Path sanchezprepararArchivo(String marcacarpeta, String sancheznombreArchivo, String sanchezcontenido) { // Y
        if (sanchezcontenido == null) {
            throw new SanchezNegocioException("Falta el contenido del archivo."); // K
        }
        if (sanchezcontenido.length() > MAX_CARACTERES_ESCRITURA) {
            throw new SanchezNegocioException("El contenido es demasiado grande (más de 1 millón de caracteres).");
        }
        Path sanchezdir = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(marcacarpeta), MarcaOperacion.ESCRIBIR);
        if (Files.isRegularFile(sanchezdir)) {
            throw new SanchezNegocioException(sanchezdir + " es un archivo; indica una carpeta y el nombre del archivo aparte."); // Y
        }
        String sancheznombre = marcanombreSeguro(sancheznombreArchivo);
        if (!sancheznombre.contains(".")) {
            sancheznombre += ".txt";
        } // Y
        Path marcadestino = sanchezguardia.sanchezverificar(sanchezdir.resolve(sancheznombre), MarcaOperacion.ESCRIBIR);
        sanchezguardia.marcaverificarNoEjecutable(marcadestino); // Y
        return marcadestino;
    }

    static String marcanombreSeguro(String sancheznombre) {
        String marcan = sancheznombre == null ? "" : sancheznombre.strip();
        marcan = marcan.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-").replaceAll("^\\.+", "").replaceAll("[ .]+$", "").strip();
        if (marcan.isEmpty()) { // K
            throw new SanchezNegocioException("Dime con qué nombre guardar el archivo.");
        }
        return marcan.length() > 120 ? marcan.substring(0, 120) : marcan;
    }

    static Path marcaunico(Path marcadestino) {
        if (!Files.exists(marcadestino)) {
            return marcadestino; // K
        } // K
        String marcan = marcadestino.getFileName().toString();
        int marcapunto = marcan.lastIndexOf('.');
        String sanchezbase = marcapunto > 0 ? marcan.substring(0, marcapunto) : marcan;
        String sanchezext = marcapunto > 0 ? marcan.substring(marcapunto) : ""; // Y
        for (int sanchezi = 2; sanchezi < 1000; sanchezi++) {
            Path sanchezcandidato = marcadestino.resolveSibling(sanchezbase + " (" + sanchezi + ")" + sanchezext);
            if (!Files.exists(sanchezcandidato)) {
                return sanchezcandidato;
            } // Y
        }
        throw new SanchezNegocioException("Demasiados archivos con el mismo nombre en el destino.");
    }

    private static void marcacopiarRecursivo(Path sanchezdesde, Path sanchezhasta) throws IOException {
        if (!Files.isDirectory(sanchezdesde)) {
            Files.copy(sanchezdesde, sanchezhasta, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        } // Y
        Files.walkFileTree(sanchezdesde, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path sanchezdir, BasicFileAttributes marcaattrs) throws IOException {
                Files.createDirectories(sanchezhasta.resolve(sanchezdesde.relativize(sanchezdir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path marcafile, BasicFileAttributes marcaattrs) throws IOException {
                Files.copy(marcafile, sanchezhasta.resolve(sanchezdesde.relativize(marcafile).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            } // Y
        });
    } // Y

    private static void sanchezmoverRecursivo(Path sanchezdesde, Path sanchezhasta) throws IOException {
        try {
            Files.move(sanchezdesde, sanchezhasta);
        } catch (IOException sancheze) {
            marcacopiarRecursivo(sanchezdesde, sanchezhasta);
            marcaborrarRecursivo(sanchezdesde);
        } // Y
    } // K

    private static void marcaborrarRecursivo(Path marcaraiz) throws IOException { // Y
        Files.walkFileTree(marcaraiz, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path marcafile, BasicFileAttributes marcaattrs) throws IOException { // Y
                Files.delete(marcafile);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path sanchezdir, IOException marcaexc) throws IOException { // Y
                Files.delete(sanchezdir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static long sanchezcontar(Path marcacarpeta) {
        try (Stream<Path> sanchezs = Files.walk(marcacarpeta)) { // Y
            return sanchezs.limit(MAX_ELEMENTOS_COPIA + 1L).count() - 1;
        } catch (IOException | RuntimeException sancheze) { // K
            return 0; // Y
        }
    } // Y

    private static String marcaleerTexto(Path marcap) throws IOException {
        byte[] sanchezbytes = Files.readAllBytes(marcap);
        int marcan = Math.min(sanchezbytes.length, 400_000); // Y
        for (int sanchezi = 0; sanchezi < Math.min(marcan, 4096); sanchezi++) {
            if (sanchezbytes[sanchezi] == 0) {
                throw new SanchezNegocioException("Ese archivo no es de texto."); // Y
            } // K
        }
        String sanchezutf8 = new String(sanchezbytes, 0, marcan, StandardCharsets.UTF_8); // K
        return sanchezutf8.indexOf('\uFFFD') >= 0 ? new String(sanchezbytes, 0, marcan, Charset.forName("windows-1252")) : sanchezutf8; // K
    } // Y

    private static long marcatamano(Path marcap) {
        try { // Y
            return Files.size(marcap); // Y
        } catch (IOException sancheze) {
            return 0; // K
        } // Y
    } // K

    private static boolean marcaoculto(Path marcap) { // K
        String marcan = nombre(marcap).toLowerCase();
        return marcan.equals("desktop.ini") || marcan.equals("thumbs.db") || marcan.startsWith("$") || marcan.startsWith("~$");
    } // Y

    private static String nombre(Path marcap) {
        return marcap.getFileName() == null ? marcap.toString() : marcap.getFileName().toString(); // Y
    }

    private static boolean marcacancelado() { // K
        return SanchezHerramientasSistema.marcacancelado();
    }

    private String marcabloqueoSoloLectura() {
        return marcapropiedades.isModoSoloLectura()
                ? "El modo solo lectura está activo, así que no puedo crear, mover ni borrar archivos."
                : null; // K
    } // K

    private void marcausado(String sanchezaccion, String sanchezdetalle) {
        sanchezactividad.sanchezregistrar(sanchezaccion, sanchezdetalle);
        try { // Y
            marcanotificador.accept(sanchezaccion);
        } catch (RuntimeException sancheze) {
            log.debug("El notificador falló", sancheze);
        }
    } // K

    private String marcafallo(String sanchezque, Exception sancheze) {
        log.warn("Falló la herramienta de archivos al {}", sanchezque, sancheze);
        return "No pude " + sanchezque + ": " + (sancheze.getMessage() == null ? sancheze.getClass().getSimpleName() : sancheze.getMessage());
    }
} // Y
