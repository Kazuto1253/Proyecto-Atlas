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
    private static final long TIEMPO_BUSQUEDA_MS = 10_000;
    private static final int MAX_ELEMENTOS_COPIA = 5000;
    private static final String CANCELADO = SanchezHerramientasSistema.MENSAJE_CANCELADO;

    private static final Set<String> TEXTO_PLANO = Set.of("txt", "md", "csv", "json", "xml", "html", "htm", "css", "java",
            "py", "c", "cpp", "h", "cs", "sql", "log", "ini", "yaml", "yml", "properties", "tex", "tsv", "ts", "tsx", "jsx",
            "php", "rb", "go", "rs", "kt", "gradle", "toml", "cfg", "conf", "env", "bib", "srt");
    private static final Set<String> CON_EXTRACTOR = Set.of("pdf", "docx", "png", "jpg", "jpeg");

    private final SanchezPropiedadesAtlas marcapropiedades;
    private final MarcaResolvedorRutas sanchezrutas;
    private final MarcaGuardiaRutas sanchezguardia;
    private final SanchezLanzador sanchezlanzador;
    private final MarcaPapelera marcapapelera;
    private final SanchezConfirmadorAcciones marcaconfirmador;
    private final SanchezServicioActividad sanchezactividad;
    private final MarcaExtractorTexto marcaextractor;

    private volatile Consumer<String> marcanotificador = marcat -> { };

    public void marcasetNotificador(Consumer<String> marcanotificador) {
        this.marcanotificador = marcanotificador == null ? marcat -> { } : marcanotificador;
    }

    @Tool("Abre una carpeta o un archivo (documento, PDF, imagen, hoja de cálculo...) en el programa predeterminado. "
            + "La ruta puede ser un nombre de carpeta (documentos, descargas, escritorio, imágenes, música, videos, "
            + "usuario), una subcarpeta (escritorio/redes) o una ruta completa (D:\\Proyectos).")
    public String abrirRuta(@P("Carpeta o archivo a abrir") String ruta) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        try {
            Path marcap = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(ruta), MarcaOperacion.LEER);
            if (!Files.exists(marcap)) {
                return "No existe: " + marcap + ". Usa buscarArchivos si no sabes dónde está.";
            }
            if (Files.isRegularFile(marcap)) {
                sanchezguardia.marcaverificarNoEjecutable(marcap);
            }
            sanchezlanzador.marcaabrir(marcap);
            marcausado("Abrió " + nombre(marcap), marcap.toString());
            return "Abrí " + (Files.isDirectory(marcap) ? "la carpeta " : "el archivo ") + nombre(marcap) + ".";
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (RuntimeException sancheze) {
            return marcafallo("abrir", sancheze);
        }
    }

    @Tool("Lista lo que hay dentro de una carpeta (archivos y subcarpetas, con su tamaño). "
            + "Úsala para ver qué archivos tiene el usuario en documentos, descargas, escritorio, un disco, etc.")
    public String listarContenido(@P("Carpeta a listar, por ejemplo descargas o D:\\Proyectos") String carpeta) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        try {
            Path sanchezdir = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(carpeta), MarcaOperacion.LEER);
            if (!Files.isDirectory(sanchezdir)) {
                return Files.exists(sanchezdir) ? nombre(sanchezdir) + " es un archivo, no una carpeta." : "No existe la carpeta " + sanchezdir + ".";
            }
            List<Path> sanchezhijos;
            try (Stream<Path> sanchezs = Files.list(sanchezdir)) {
                sanchezhijos = sanchezs.filter(marcah -> !marcaoculto(marcah))
                        .sorted(Comparator.comparing((Path marcah) -> !Files.isDirectory(marcah)).thenComparing(marcah -> sancheznormalizar(nombre(marcah))))
                        .toList();
            }
            marcausado("Listó " + nombre(sanchezdir), sanchezdir.toString());
            if (sanchezhijos.isEmpty()) {
                return "La carpeta " + nombre(sanchezdir) + " está vacía.";
            }
            StringBuilder sanchezsb = new StringBuilder("En " + sanchezdir + " hay " + sanchezhijos.size() + " elementos:\n");
            for (Path marcah : sanchezhijos.stream().limit(MAX_LISTA).toList()) {
                sanchezsb.append("- ").append(nombre(marcah));
                if (Files.isDirectory(marcah)) {
                    sanchezsb.append(" (carpeta)");
                } else {
                    sanchezsb.append(" (").append(MarcaMonitorSistema.sanchezbytes(marcatamano(marcah))).append(')');
                }
                sanchezsb.append('\n');
            }
            if (sanchezhijos.size() > MAX_LISTA) {
                sanchezsb.append("... y ").append(sanchezhijos.size() - MAX_LISTA).append(" más.");
            }
            return sanchezsb.toString();
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("listar la carpeta", sancheze);
        }
    }

    @Tool("Busca archivos o carpetas por parte de su nombre. Si no se indica carpeta, busca en todo el usuario "
            + "(documentos, escritorio, descargas...). Devuelve las rutas completas.")
    public String buscarArchivos(@P("Parte del nombre, por ejemplo tarea redes") String nombre,
                                 @P("Carpeta o disco donde buscar (opcional; vacío = todo el usuario), por ejemplo D:\\") String carpeta) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        try {
            List<String> marcapalabras = Arrays.stream(sancheznormalizar(nombre).split("[^a-z0-9ñ]+")).filter(marcap -> marcap.length() >= 2).toList();
            if (marcapalabras.isEmpty()) {
                return "Dime qué nombre buscar.";
            }
            Path marcaraiz = carpeta == null || carpeta.isBlank()
                    ? sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver("usuario"), MarcaOperacion.LEER)
                    : sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(carpeta), MarcaOperacion.LEER);
            List<Path> marcaencontrados = new ArrayList<>();
            long marcalimite = System.currentTimeMillis() + TIEMPO_BUSQUEDA_MS;
            boolean[] marcacortada = {false};
            Files.walkFileTree(marcaraiz, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), 8, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path sanchezdir, BasicFileAttributes marcaattrs) {
                    if (!sanchezdir.equals(marcaraiz) && sanchezguardia.sanchezdebeOmitirseAlBuscar(sanchezdir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return alTerminar() ? FileVisitResult.TERMINATE : coincide(sanchezdir);
                }

                @Override
                public FileVisitResult visitFile(Path marcafile, BasicFileAttributes marcaattrs) {
                    return alTerminar() ? FileVisitResult.TERMINATE : coincide(marcafile);
                }

                @Override
                public FileVisitResult visitFileFailed(Path marcafile, IOException marcaexc) {
                    return FileVisitResult.CONTINUE;
                }

                private boolean alTerminar() {
                    if (marcacancelado() || System.currentTimeMillis() > marcalimite || marcaencontrados.size() >= MAX_RESULTADOS) {
                        marcacortada[0] = System.currentTimeMillis() > marcalimite;
                        return true;
                    }
                    return false;
                }

                private FileVisitResult coincide(Path marcap) {
                    String marcan = sancheznormalizar(nombre(marcap));
                    if (marcapalabras.stream().allMatch(marcan::contains) && !marcap.equals(marcaraiz)) {
                        marcaencontrados.add(marcap);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            marcausado("Buscó archivos", nombre);
            if (marcaencontrados.isEmpty()) {
                return "No encontré nada que se llame como «" + nombre + "» en " + marcaraiz
                        + (marcacortada[0] ? " (la búsqueda se cortó por tiempo; prueba en una carpeta más específica)." : ".");
            }
            StringBuilder sanchezsb = new StringBuilder("Encontré " + marcaencontrados.size() + " resultado(s):\n");
            for (Path marcap : marcaencontrados) {
                sanchezsb.append("- ").append(marcap).append(Files.isDirectory(marcap) ? " (carpeta)" : " (" + MarcaMonitorSistema.sanchezbytes(marcatamano(marcap)) + ")").append('\n');
            }
            return sanchezsb.toString();
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("buscar", sancheze);
        }
    }

    @Tool("Lee el contenido de un archivo para poder responder sobre él o resumirlo: texto, Markdown, CSV, código, "
            + "PDF, Word (.docx) o imágenes con texto (usa OCR). Devuelve el principio del contenido.")
    public String leerArchivo(@P("Archivo a leer, por ejemplo documentos/tarea.pdf o D:\\apuntes.txt") String ruta) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        try {
            Path marcap = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(ruta), MarcaOperacion.LEER);
            if (Files.isDirectory(marcap)) {
                return nombre(marcap) + " es una carpeta; usa listarContenido para ver lo que tiene.";
            }
            if (!Files.isRegularFile(marcap)) {
                return "No existe el archivo " + marcap + ". Usa buscarArchivos si no sabes dónde está.";
            }
            if (Files.size(marcap) > MAX_BYTES_LECTURA) {
                return "El archivo es demasiado grande para leerlo (más de 25 MB).";
            }
            String sanchezext = MarcaGuardiaRutas.sanchezextension(marcap);
            String marcatexto;
            if (CON_EXTRACTOR.contains(sanchezext)) {
                marcatexto = marcaextractor.sanchezextraer(marcap);
            } else if (TEXTO_PLANO.contains(sanchezext) || sanchezext.isEmpty()) {
                marcatexto = marcaleerTexto(marcap);
            } else {
                return "No sé leer archivos ." + sanchezext + ". Puedo leer texto, PDF, Word e imágenes con texto.";
            }
            marcausado("Leyó " + nombre(marcap), marcap.toString());
            String marcalimpio = marcatexto.strip();
            if (marcalimpio.isEmpty()) {
                return "El archivo " + nombre(marcap) + " no tiene texto legible.";
            }
            if (marcalimpio.length() <= MAX_CARACTERES_LECTURA) {
                return "Contenido de " + nombre(marcap) + ":\n" + marcalimpio;
            }
            return "Contenido de " + nombre(marcap) + " (primeros " + MAX_CARACTERES_LECTURA + " de " + marcalimpio.length()
                    + " caracteres):\n" + marcalimpio.substring(0, MAX_CARACTERES_LECTURA);
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("leer el archivo", sancheze);
        }
    }

    @Tool("Crea una carpeta nueva (y las carpetas intermedias que falten). Ejemplo: escritorio/redes o D:\\Proyectos\\atlas.")
    public String crearCarpeta(@P("Ruta de la carpeta a crear") String ruta) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        try {
            Path marcap = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(ruta), MarcaOperacion.ESCRIBIR);
            if (Files.isDirectory(marcap)) {
                return "La carpeta " + marcap + " ya existía.";
            }
            if (Files.exists(marcap)) {
                return "Ya existe un archivo con ese nombre: " + marcap;
            }
            if (sanchezguardia.marcapideConfirmacionPorUbicacion(marcap)
                    && !marcaconfirmador.sanchezconfirmar("¿Crear carpeta?", "Atlas quiere crear la carpeta:\n" + marcap)) {
                return "Cancelé la creación de la carpeta porque no lo confirmaste.";
            }
            Files.createDirectories(marcap);
            marcausado("Creó la carpeta " + nombre(marcap), marcap.toString());
            return "Creé la carpeta " + marcap + ".";
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("crear la carpeta", sancheze);
        }
    }

    @Tool("Guarda un archivo de texto con el contenido que se indique (apuntes, listas, resúmenes, código, CSV...) en una carpeta. "
            + "Crea la carpeta si no existe. Si ya existe un archivo con ese nombre, pide confirmación para reemplazarlo.")
    public String guardarArchivo(@P("Carpeta donde guardar: documentos, escritorio, descargas, D:\\Proyectos...") String carpeta,
                                 @P("Nombre del archivo con extensión, por ejemplo lista-compras.txt (sin extensión se usa .txt)") String nombreArchivo,
                                 @P("Todo el texto que debe llevar el archivo") String contenido) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        try {
            Path marcadestino = sanchezprepararArchivo(carpeta, nombreArchivo, contenido);
            boolean marcaexiste = Files.exists(marcadestino);
            if (marcaexiste && !marcaconfirmador.sanchezconfirmar("¿Reemplazar archivo?", "Ya existe:\n" + marcadestino + "\n\n¿Quieres reemplazarlo?")) {
                return "No reemplacé el archivo porque no lo confirmaste.";
            }
            if (!marcaexiste && sanchezguardia.marcapideConfirmacionPorUbicacion(marcadestino)
                    && !marcaconfirmador.sanchezconfirmar("¿Guardar archivo?", "Atlas quiere guardar:\n" + marcadestino)) {
                return "Cancelé el guardado porque no lo confirmaste.";
            }
            Files.createDirectories(marcadestino.getParent());
            Files.writeString(marcadestino, contenido, StandardCharsets.UTF_8);
            marcausado("Guardó " + nombre(marcadestino), marcadestino.toString());
            return (marcaexiste ? "Reemplacé" : "Guardé") + " el archivo " + nombre(marcadestino) + " en " + marcadestino.getParent() + ".";
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("guardar el archivo", sancheze);
        }
    }

    @Tool("Agrega texto al final de un archivo (lo crea si no existe). Útil para añadir cosas a una lista o a unos apuntes.")
    public String agregarAArchivo(@P("Carpeta del archivo") String carpeta,
                                  @P("Nombre del archivo con extensión") String nombreArchivo,
                                  @P("Texto a agregar") String contenido) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        try {
            Path marcadestino = sanchezprepararArchivo(carpeta, nombreArchivo, contenido);
            if (!Files.exists(marcadestino) && sanchezguardia.marcapideConfirmacionPorUbicacion(marcadestino)
                    && !marcaconfirmador.sanchezconfirmar("¿Guardar archivo?", "Atlas quiere crear:\n" + marcadestino)) {
                return "Cancelé el guardado porque no lo confirmaste.";
            }
            Files.createDirectories(marcadestino.getParent());
            String sanchezseparador = Files.exists(marcadestino) && Files.size(marcadestino) > 0 ? System.lineSeparator() : "";
            Files.writeString(marcadestino, sanchezseparador + contenido, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            marcausado("Agregó texto a " + nombre(marcadestino), marcadestino.toString());
            return "Agregué el texto a " + nombre(marcadestino) + " en " + marcadestino.getParent() + ".";
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("agregar al archivo", sancheze);
        }
    }

    @Tool("Copia un archivo o una carpeta completa dentro de otra carpeta (el original se conserva). Si ya hay uno con ese nombre, "
            + "la copia se llama «nombre (2)».")
    public String copiar(@P("Archivo o carpeta a copiar") String origen,
                         @P("Carpeta de destino, por ejemplo escritorio o D:\\Respaldo") String carpetaDestino) {
        return marcatransferir(origen, carpetaDestino, false);
    }

    @Tool("Mueve un archivo o una carpeta completa a otra carpeta (ya no queda en su lugar original). Si ya hay uno con ese "
            + "nombre, queda como «nombre (2)».")
    public String mover(@P("Archivo o carpeta a mover") String origen,
                        @P("Carpeta de destino, por ejemplo documentos/redes") String carpetaDestino) {
        return marcatransferir(origen, carpetaDestino, true);
    }

    @Tool("Cambia el nombre de un archivo o carpeta. Si el nuevo nombre no tiene extensión, se conserva la del archivo.")
    public String renombrar(@P("Archivo o carpeta a renombrar") String ruta,
                            @P("Nombre nuevo") String nuevoNombre) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        try {
            Path marcap = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(ruta), MarcaOperacion.ESCRIBIR);
            if (!Files.exists(marcap)) {
                return "No existe: " + marcap;
            }
            String marcalimpio = marcanombreSeguro(nuevoNombre);
            if (Files.isRegularFile(marcap) && !marcalimpio.contains(".") && !MarcaGuardiaRutas.sanchezextension(marcap).isEmpty()) {
                marcalimpio += "." + MarcaGuardiaRutas.sanchezextension(marcap);
            }
            Path marcadestino = sanchezguardia.sanchezverificar(marcap.resolveSibling(marcalimpio), MarcaOperacion.ESCRIBIR);
            if (Files.isRegularFile(marcap)) {
                sanchezguardia.marcaverificarNoEjecutable(marcadestino);
            }
            if (Files.exists(marcadestino)) {
                return "Ya existe algo llamado " + marcalimpio + " en esa carpeta.";
            }
            Files.move(marcap, marcadestino);
            marcausado("Renombró " + nombre(marcap), nombre(marcap) + " → " + marcalimpio);
            return "Renombré " + nombre(marcap) + " a " + marcalimpio + ".";
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("renombrar", sancheze);
        }
    }

    @Tool("Elimina un archivo o carpeta enviándolo a la papelera de reciclaje (se puede restaurar desde allí). "
            + "Siempre pide confirmación al usuario en una ventana.")
    public String eliminar(@P("Archivo o carpeta a eliminar") String ruta) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        try {
            Path marcap = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(ruta), MarcaOperacion.ELIMINAR);
            if (!Files.exists(marcap)) {
                return "No existe: " + marcap;
            }
            String sanchezdetalle = Files.isDirectory(marcap) ? "la carpeta (con " + sanchezcontar(marcap) + " elementos)" : "el archivo";
            if (!marcaconfirmador.sanchezconfirmar("¿Enviar a la papelera?",
                    "Atlas quiere enviar a la papelera de reciclaje " + sanchezdetalle + ":\n" + marcap
                            + "\n\nPodrás restaurarlo desde la papelera.")) {
                marcausado("Eliminación cancelada", marcap.toString());
                return "No eliminé nada porque no lo confirmaste.";
            }
            marcapapelera.sanchezenviar(marcap);
            marcausado("Envió a la papelera " + nombre(marcap), marcap.toString());
            return "Envié " + nombre(marcap) + " a la papelera de reciclaje; puedes restaurarlo desde allí.";
        } catch (SanchezPermisoDenegadoException | SanchezNegocioException sancheze) {
            return sancheze.getMessage();
        } catch (RuntimeException sancheze) {
            return marcafallo("eliminar", sancheze);
        }
    }

    private String marcatransferir(String marcaorigen, String marcacarpetaDestino, boolean sanchezmover) {
        if (marcacancelado()) {
            return CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        try {
            Path sanchezdesde = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(marcaorigen), sanchezmover ? MarcaOperacion.ESCRIBIR : MarcaOperacion.LEER);
            if (!Files.exists(sanchezdesde)) {
                return "No existe: " + sanchezdesde + ". Usa buscarArchivos si no sabes dónde está.";
            }
            Path sanchezdir = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(marcacarpetaDestino), MarcaOperacion.ESCRIBIR);
            if (Files.isRegularFile(sanchezdir)) {
                return "El destino " + sanchezdir + " es un archivo; indica una carpeta.";
            }
            if (Files.isRegularFile(sanchezdesde)) {
                sanchezguardia.marcaverificarNoEjecutable(sanchezdesde);
            }
            if (Files.isDirectory(sanchezdesde) && (sanchezdir.equals(sanchezdesde) || sanchezdir.startsWith(sanchezdesde))) {
                return "No puedo " + (sanchezmover ? "mover" : "copiar") + " una carpeta dentro de sí misma.";
            }
            if (Files.isDirectory(sanchezdesde) && sanchezcontar(sanchezdesde) > MAX_ELEMENTOS_COPIA) {
                return "La carpeta tiene demasiados elementos (más de " + MAX_ELEMENTOS_COPIA + "); hazlo desde el Explorador.";
            }
            if (sanchezguardia.marcapideConfirmacionPorUbicacion(sanchezdir)
                    && !marcaconfirmador.sanchezconfirmar(sanchezmover ? "¿Mover?" : "¿Copiar?", "Destino fuera de tu usuario:\n" + sanchezdir
                    + "\n\n" + (sanchezmover ? "Mover" : "Copiar") + ": " + sanchezdesde)) {
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

    private Path sanchezprepararArchivo(String marcacarpeta, String sancheznombreArchivo, String sanchezcontenido) {
        if (sanchezcontenido == null) {
            throw new SanchezNegocioException("Falta el contenido del archivo.");
        }
        if (sanchezcontenido.length() > MAX_CARACTERES_ESCRITURA) {
            throw new SanchezNegocioException("El contenido es demasiado grande (más de 1 millón de caracteres).");
        }
        Path sanchezdir = sanchezguardia.sanchezverificar(sanchezrutas.marcaresolver(marcacarpeta), MarcaOperacion.ESCRIBIR);
        if (Files.isRegularFile(sanchezdir)) {
            throw new SanchezNegocioException(sanchezdir + " es un archivo; indica una carpeta y el nombre del archivo aparte.");
        }
        String sancheznombre = marcanombreSeguro(sancheznombreArchivo);
        if (!sancheznombre.contains(".")) {
            sancheznombre += ".txt";
        }
        Path marcadestino = sanchezguardia.sanchezverificar(sanchezdir.resolve(sancheznombre), MarcaOperacion.ESCRIBIR);
        sanchezguardia.marcaverificarNoEjecutable(marcadestino);
        return marcadestino;
    }

    static String marcanombreSeguro(String sancheznombre) {
        String marcan = sancheznombre == null ? "" : sancheznombre.strip();
        marcan = marcan.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-").replaceAll("^\\.+", "").replaceAll("[ .]+$", "").strip();
        if (marcan.isEmpty()) {
            throw new SanchezNegocioException("Dime con qué nombre guardar el archivo.");
        }
        return marcan.length() > 120 ? marcan.substring(0, 120) : marcan;
    }

    static Path marcaunico(Path marcadestino) {
        if (!Files.exists(marcadestino)) {
            return marcadestino;
        }
        String marcan = marcadestino.getFileName().toString();
        int marcapunto = marcan.lastIndexOf('.');
        String sanchezbase = marcapunto > 0 ? marcan.substring(0, marcapunto) : marcan;
        String sanchezext = marcapunto > 0 ? marcan.substring(marcapunto) : "";
        for (int sanchezi = 2; sanchezi < 1000; sanchezi++) {
            Path sanchezcandidato = marcadestino.resolveSibling(sanchezbase + " (" + sanchezi + ")" + sanchezext);
            if (!Files.exists(sanchezcandidato)) {
                return sanchezcandidato;
            }
        }
        throw new SanchezNegocioException("Demasiados archivos con el mismo nombre en el destino.");
    }

    private static void marcacopiarRecursivo(Path sanchezdesde, Path sanchezhasta) throws IOException {
        if (!Files.isDirectory(sanchezdesde)) {
            Files.copy(sanchezdesde, sanchezhasta, StandardCopyOption.COPY_ATTRIBUTES);
            return;
        }
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
            }
        });
    }

    private static void sanchezmoverRecursivo(Path sanchezdesde, Path sanchezhasta) throws IOException {
        try {
            Files.move(sanchezdesde, sanchezhasta);
        } catch (IOException sancheze) {
            marcacopiarRecursivo(sanchezdesde, sanchezhasta);
            marcaborrarRecursivo(sanchezdesde);
        }
    }

    private static void marcaborrarRecursivo(Path marcaraiz) throws IOException {
        Files.walkFileTree(marcaraiz, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path marcafile, BasicFileAttributes marcaattrs) throws IOException {
                Files.delete(marcafile);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path sanchezdir, IOException marcaexc) throws IOException {
                Files.delete(sanchezdir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static long sanchezcontar(Path marcacarpeta) {
        try (Stream<Path> sanchezs = Files.walk(marcacarpeta)) {
            return sanchezs.limit(MAX_ELEMENTOS_COPIA + 1L).count() - 1;
        } catch (IOException | RuntimeException sancheze) {
            return 0;
        }
    }

    private static String marcaleerTexto(Path marcap) throws IOException {
        byte[] sanchezbytes = Files.readAllBytes(marcap);
        int marcan = Math.min(sanchezbytes.length, 400_000);
        for (int sanchezi = 0; sanchezi < Math.min(marcan, 4096); sanchezi++) {
            if (sanchezbytes[sanchezi] == 0) {
                throw new SanchezNegocioException("Ese archivo no es de texto.");
            }
        }
        String sanchezutf8 = new String(sanchezbytes, 0, marcan, StandardCharsets.UTF_8);
        return sanchezutf8.indexOf('\uFFFD') >= 0 ? new String(sanchezbytes, 0, marcan, Charset.forName("windows-1252")) : sanchezutf8;
    }

    private static long marcatamano(Path marcap) {
        try {
            return Files.size(marcap);
        } catch (IOException sancheze) {
            return 0;
        }
    }

    private static boolean marcaoculto(Path marcap) {
        String marcan = nombre(marcap).toLowerCase();
        return marcan.equals("desktop.ini") || marcan.equals("thumbs.db") || marcan.startsWith("$") || marcan.startsWith("~$");
    }

    private static String nombre(Path marcap) {
        return marcap.getFileName() == null ? marcap.toString() : marcap.getFileName().toString();
    }

    private static boolean marcacancelado() {
        return SanchezHerramientasSistema.marcacancelado();
    }

    private String marcabloqueoSoloLectura() {
        return marcapropiedades.isModoSoloLectura()
                ? "El modo solo lectura está activo, así que no puedo crear, mover ni borrar archivos."
                : null;
    }

    private void marcausado(String sanchezaccion, String sanchezdetalle) {
        sanchezactividad.sanchezregistrar(sanchezaccion, sanchezdetalle);
        try {
            marcanotificador.accept(sanchezaccion);
        } catch (RuntimeException sancheze) {
            log.debug("El notificador falló", sancheze);
        }
    }

    private String marcafallo(String sanchezque, Exception sancheze) {
        log.warn("Falló la herramienta de archivos al {}", sanchezque, sancheze);
        return "No pude " + sanchezque + ": " + (sancheze.getMessage() == null ? sancheze.getClass().getSimpleName() : sancheze.getMessage());
    }
}
