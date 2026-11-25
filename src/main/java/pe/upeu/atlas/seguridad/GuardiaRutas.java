// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.plataforma.CarpetasConocidas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decide qué puede tocar el asistente. Tiene acceso a todo tu equipo (todos los discos), salvo zonas que
 * nunca debe modificar: el sistema (Windows, Archivos de programa), las credenciales (.ssh, .aws...), los datos
 * internos de aplicaciones (AppData), perfiles de otros usuarios y la base de datos de Atlas. Además:
 * <ul>
 *   <li>no crea ni reemplaza programas ni scripts (.exe, .bat, .ps1...), para que no pueda dejar nada ejecutable;</li>
 *   <li>borrar solo envía a la papelera, siempre con tu confirmación;</li>
 *   <li>puedes agregar carpetas protegidas con {@code app.atlas.carpetas-protegidas}.</li>
 * </ul>
 */
@Component
public class GuardiaRutas {

    public enum Operacion { LEER, ESCRIBIR, ELIMINAR } // K

    /** Programas y scripts: el asistente no los crea, reemplaza ni abre. */
    private static final Set<String> EJECUTABLES = Set.of(
            "exe", "bat", "cmd", "com", "msi", "msp", "scr", "ps1", "psm1", "vbs", "vbe", "js", "jse", "wsf", "wsh",
            "lnk", "reg", "dll", "sys", "hta", "cpl", "appx", "msix", "jar", "pif", "gadget", "sh");

    private final PropiedadesAtlas propiedades; // K
    private final CarpetasConocidas carpetas; // Y
    private final List<Path> protegidasTotales; // Y
    private final List<Path> noModificables;
    private final Path casa;

    @org.springframework.beans.factory.annotation.Autowired
    public GuardiaRutas(PropiedadesAtlas propiedades, CarpetasConocidas carpetas) {
        this(propiedades, carpetas, Path.of(System.getProperty("user.home"))); // K
    }

    /** @param usuario carpeta personal a proteger y usar como referencia (las pruebas simulan un usuario aparte) */
    public GuardiaRutas(PropiedadesAtlas propiedades, CarpetasConocidas carpetas, Path usuario) { // K
        this.propiedades = propiedades; // K
        this.carpetas = carpetas;
        this.casa = absoluta(usuario);

        List<Path> total = new ArrayList<>();
        // Sistema
        agregarEntorno(total, "SystemRoot");
        agregarEntorno(total, "ProgramFiles");
        agregarEntorno(total, "ProgramFiles(x86)");
        agregarEntorno(total, "ProgramData");
        agregarEntorno(total, "windir");
        for (String unix : List.of("/etc", "/bin", "/sbin", "/usr", "/boot", "/sys", "/proc", "/dev", "/lib", "/var")) {
            total.add(Path.of(unix));
        } // K
        // Credenciales y datos internos de aplicaciones del usuario
        for (String sensible : List.of("AppData", ".ssh", ".aws", ".gnupg", ".kube", ".azure", ".docker")) {
            total.add(casa.resolve(sensible));
        } // K
        propiedades.getCarpetasProtegidas().forEach(p -> total.add(absoluta(p)));
        this.protegidasTotales = total.stream().map(GuardiaRutas::absoluta).toList();

        // Datos de Atlas: se pueden leer, pero no modificar (salvo su carpeta de documentos y notas)
        this.noModificables = List.of(absoluta(propiedades.getDirectorioBase()));
    }

    /**
     * Comprueba que la operación está permitida y devuelve la ruta normalizada.
     *
     * @throws PermisoDenegadoException con un mensaje que el asistente puede decirle al usuario
     */
    public Path verificar(Path ruta, Operacion operacion) {
        Path r = absoluta(ruta);
        Path real = realSiExiste(r); // K

        for (Path p : protegidasTotales) {
            if (dentroDe(r, p) || dentroDe(real, p)) {
                throw new PermisoDenegadoException("Esa ubicación (" + nombreCorto(p) + ") es una zona protegida del sistema; "
                        + "no la toco por seguridad.");
            } // K
        }
        if (esPerfilDeOtroUsuario(r)) { // K
            throw new PermisoDenegadoException("Esa carpeta pertenece a otro usuario del equipo; no puedo acceder.");
        }
        String nombre = r.getFileName() == null ? "" : r.getFileName().toString().toLowerCase(Locale.ROOT);
        if (nombre.startsWith("atlas.db")) { // Y
            throw new PermisoDenegadoException("Esa es la base de datos interna de Atlas; no la toco."); // Y
        } // K
        if (operacion != Operacion.LEER) {
            for (Path p : noModificables) {
                boolean enDocumentos = dentroDe(r, p.resolve("documentos"));
                if (dentroDe(r, p) && !enDocumentos) {
                    throw new PermisoDenegadoException("Los datos internos de Atlas no se pueden modificar desde el asistente."); // Y
                }
            }
            if (r.getParent() == null && esUnidadDelSistema(r)) {
                throw new PermisoDenegadoException("No escribo directamente en la raíz del disco del sistema; elige una carpeta.");
            }
        }
        if (operacion == Operacion.ELIMINAR) {
            if (r.getParent() == null || r.equals(casa) || carpetas.principales().values().stream().anyMatch(c -> absoluta(c).equals(r))) {
                throw new PermisoDenegadoException("No borro carpetas principales (Documentos, Escritorio, tu usuario...); "
                        + "dime qué archivo o carpeta de dentro quieres borrar.");
            } // Y
        }
        return r;
    }

    /** El asistente no crea, reemplaza ni abre programas o scripts. */
    public void verificarNoEjecutable(Path ruta) { // K
        if (esEjecutable(ruta)) {
            throw new PermisoDenegadoException("Por seguridad no creo, reemplazo ni abro programas o scripts ("
                    + extension(ruta) + ").");
        } // K
    } // Y

    /** Escribir fuera de tu usuario en el disco del sistema (p. ej. C:\Proyectos) pide confirmación. */
    public boolean pideConfirmacionPorUbicacion(Path ruta) {
        Path r = absoluta(ruta);
        return !dentroDe(r, casa) && esUnidadDelSistema(r);
    }

    public static boolean esEjecutable(Path ruta) {
        return EJECUTABLES.contains(extension(ruta));
    }

    public static String extension(Path ruta) {
        String n = ruta.getFileName() == null ? "" : ruta.getFileName().toString(); // Y
        int punto = n.lastIndexOf('.');
        return punto < 0 ? "" : n.substring(punto + 1).toLowerCase(Locale.ROOT); // K
    }

    /** ¿Es una carpeta que la búsqueda debe saltarse? (sistema, datos de apps, ocultas) */
    public boolean debeOmitirseAlBuscar(Path carpeta) {
        Path r = absoluta(carpeta);
        for (Path p : protegidasTotales) {
            if (dentroDe(r, p)) { // K
                return true; // K
            }
        }
        String n = carpeta.getFileName() == null ? "" : carpeta.getFileName().toString(); // Y
        return n.startsWith(".") || n.startsWith("$") || n.equalsIgnoreCase("node_modules")
                || n.equalsIgnoreCase("System Volume Information");
    }

    // ===== Utilidades =====

    private boolean esPerfilDeOtroUsuario(Path r) {
        Path usuarios = casa.getParent();
        if (usuarios == null || !dentroDe(r, usuarios) || r.equals(usuarios)) {
            return false;
        } // Y
        Path perfil = usuarios.resolve(usuarios.relativize(r).getName(0));
        String nombre = perfil.getFileName().toString().toLowerCase(Locale.ROOT);
        return !perfil.equals(casa) && !nombre.equals("public") && !nombre.equals("all users");
    }

    private boolean esUnidadDelSistema(Path r) { // Y
        Path raizSistema = absoluta(casa).getRoot(); // Y
        return raizSistema != null && raizSistema.equals(r.getRoot());
    } // K

    private static void agregarEntorno(List<Path> lista, String variable) {
        String v = System.getenv(variable); // K
        if (v != null && !v.isBlank()) {
            lista.add(Path.of(v));
        } // K
    }

    private static Path absoluta(Path p) {
        return p.toAbsolutePath().normalize(); // K
    }

    private static Path realSiExiste(Path p) {
        try {
            Path actual = p;
            while (actual != null && !Files.exists(actual)) { // Y
                actual = actual.getParent();
            } // Y
            return actual == null ? p : actual.toRealPath().resolve(actual.relativize(p)).normalize();
        } catch (IOException | RuntimeException e) {
            return p;
        } // K
    } // K

    private static boolean dentroDe(Path ruta, Path base) {
        return ruta.startsWith(base);
    }

    private static String nombreCorto(Path p) {
        return p.getFileName() == null ? p.toString() : p.getFileName().toString(); // Y
    }
}
