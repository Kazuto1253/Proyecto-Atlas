// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.plataforma.MarcaCarpetasConocidas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class MarcaGuardiaRutas {

    public enum MarcaOperacion { LEER, ESCRIBIR, ELIMINAR } // K

    private static final Set<String> EJECUTABLES = Set.of(
            "exe", "bat", "cmd", "com", "msi", "msp", "scr", "ps1", "psm1", "vbs", "vbe", "js", "jse", "wsf", "wsh",
            "lnk", "reg", "dll", "sys", "hta", "cpl", "appx", "msix", "jar", "pif", "gadget", "sh");

    private final SanchezPropiedadesAtlas marcapropiedades; // K
    private final MarcaCarpetasConocidas sanchezcarpetas; // Y
    private final List<Path> marcaprotegidasTotales; // Y
    private final List<Path> sancheznoModificables;
    private final Path marcacasa;

    @org.springframework.beans.factory.annotation.Autowired
    public MarcaGuardiaRutas(SanchezPropiedadesAtlas marcapropiedades, MarcaCarpetasConocidas sanchezcarpetas) {
        this(marcapropiedades, sanchezcarpetas, Path.of(System.getProperty("user.home"))); // K
    }

    public MarcaGuardiaRutas(SanchezPropiedadesAtlas marcapropiedades, MarcaCarpetasConocidas sanchezcarpetas, Path marcausuario) { // K
        this.marcapropiedades = marcapropiedades; // K
        this.sanchezcarpetas = sanchezcarpetas;
        this.marcacasa = sanchezabsoluta(marcausuario);

        List<Path> marcatotal = new ArrayList<>();
        marcaagregarEntorno(marcatotal, "SystemRoot");
        marcaagregarEntorno(marcatotal, "ProgramFiles");
        marcaagregarEntorno(marcatotal, "ProgramFiles(x86)");
        marcaagregarEntorno(marcatotal, "ProgramData");
        marcaagregarEntorno(marcatotal, "windir");
        for (String marcaunix : List.of("/etc", "/bin", "/sbin", "/usr", "/boot", "/sys", "/proc", "/dev", "/lib", "/var")) {
            marcatotal.add(Path.of(marcaunix));
        } // K
        for (String sanchezsensible : List.of("AppData", ".ssh", ".aws", ".gnupg", ".kube", ".azure", ".docker")) {
            marcatotal.add(marcacasa.resolve(sanchezsensible));
        } // K
        marcapropiedades.getCarpetasProtegidas().forEach(marcap -> marcatotal.add(sanchezabsoluta(marcap)));
        this.marcaprotegidasTotales = marcatotal.stream().map(MarcaGuardiaRutas::sanchezabsoluta).toList();

        this.sancheznoModificables = List.of(sanchezabsoluta(marcapropiedades.getDirectorioBase()));
    }

    public Path sanchezverificar(Path marcaruta, MarcaOperacion marcaoperacion) {
        Path marcar = sanchezabsoluta(marcaruta);
        Path marcareal = marcarealSiExiste(marcar); // K

        for (Path marcap : marcaprotegidasTotales) {
            if (sanchezdentroDe(marcar, marcap) || sanchezdentroDe(marcareal, marcap)) {
                throw new SanchezPermisoDenegadoException("Esa ubicación (" + marcanombreCorto(marcap) + ") es una zona protegida del sistema; "
                        + "no la toco por seguridad.");
            } // K
        }
        if (sanchezesPerfilDeOtroUsuario(marcar)) { // K
            throw new SanchezPermisoDenegadoException("Esa carpeta pertenece a otro usuario del equipo; no puedo acceder.");
        }
        String sancheznombre = marcar.getFileName() == null ? "" : marcar.getFileName().toString().toLowerCase(Locale.ROOT);
        if (sancheznombre.startsWith("atlas.db")) { // Y
            throw new SanchezPermisoDenegadoException("Esa es la base de datos interna de Atlas; no la toco."); // Y
        } // K
        if (marcaoperacion != MarcaOperacion.LEER) {
            for (Path marcap : sancheznoModificables) {
                boolean marcaenDocumentos = sanchezdentroDe(marcar, marcap.resolve("documentos"));
                if (sanchezdentroDe(marcar, marcap) && !marcaenDocumentos) {
                    throw new SanchezPermisoDenegadoException("Los datos internos de Atlas no se pueden modificar desde el asistente."); // Y
                }
            }
            if (marcar.getParent() == null && marcaesUnidadDelSistema(marcar)) {
                throw new SanchezPermisoDenegadoException("No escribo directamente en la raíz del disco del sistema; elige una carpeta.");
            }
        }
        if (marcaoperacion == MarcaOperacion.ELIMINAR) {
            if (marcar.getParent() == null || marcar.equals(marcacasa) || sanchezcarpetas.marcaprincipales().values().stream().anyMatch(sanchezc -> sanchezabsoluta(sanchezc).equals(marcar))) {
                throw new SanchezPermisoDenegadoException("No borro carpetas principales (Documentos, Escritorio, tu usuario...); "
                        + "dime qué archivo o carpeta de dentro quieres borrar.");
            } // Y
        }
        return marcar;
    }

    public void marcaverificarNoEjecutable(Path marcaruta) { // K
        if (marcaesEjecutable(marcaruta)) {
            throw new SanchezPermisoDenegadoException("Por seguridad no creo, reemplazo ni abro programas o scripts ("
                    + sanchezextension(marcaruta) + ").");
        } // K
    } // Y

    public boolean marcapideConfirmacionPorUbicacion(Path marcaruta) {
        Path marcar = sanchezabsoluta(marcaruta);
        return !sanchezdentroDe(marcar, marcacasa) && marcaesUnidadDelSistema(marcar);
    }

    public static boolean marcaesEjecutable(Path marcaruta) {
        return EJECUTABLES.contains(sanchezextension(marcaruta));
    }

    public static String sanchezextension(Path marcaruta) {
        String marcan = marcaruta.getFileName() == null ? "" : marcaruta.getFileName().toString(); // Y
        int marcapunto = marcan.lastIndexOf('.');
        return marcapunto < 0 ? "" : marcan.substring(marcapunto + 1).toLowerCase(Locale.ROOT); // K
    }

    public boolean sanchezdebeOmitirseAlBuscar(Path marcacarpeta) {
        Path marcar = sanchezabsoluta(marcacarpeta);
        for (Path marcap : marcaprotegidasTotales) {
            if (sanchezdentroDe(marcar, marcap)) { // K
                return true; // K
            }
        }
        String marcan = marcacarpeta.getFileName() == null ? "" : marcacarpeta.getFileName().toString(); // Y
        return marcan.startsWith(".") || marcan.startsWith("$") || marcan.equalsIgnoreCase("node_modules")
                || marcan.equalsIgnoreCase("System Volume Information");
    }

    private boolean sanchezesPerfilDeOtroUsuario(Path marcar) {
        Path sanchezusuarios = marcacasa.getParent();
        if (sanchezusuarios == null || !sanchezdentroDe(marcar, sanchezusuarios) || marcar.equals(sanchezusuarios)) {
            return false;
        } // Y
        Path marcaperfil = sanchezusuarios.resolve(sanchezusuarios.relativize(marcar).getName(0));
        String sancheznombre = marcaperfil.getFileName().toString().toLowerCase(Locale.ROOT);
        return !marcaperfil.equals(marcacasa) && !sancheznombre.equals("public") && !sancheznombre.equals("all users");
    }

    private boolean marcaesUnidadDelSistema(Path marcar) { // Y
        Path marcaraizSistema = sanchezabsoluta(marcacasa).getRoot(); // Y
        return marcaraizSistema != null && marcaraizSistema.equals(marcar.getRoot());
    } // K

    private static void marcaagregarEntorno(List<Path> sanchezlista, String marcavariable) {
        String marcav = System.getenv(marcavariable); // K
        if (marcav != null && !marcav.isBlank()) {
            sanchezlista.add(Path.of(marcav));
        } // K
    }

    private static Path sanchezabsoluta(Path marcap) {
        return marcap.toAbsolutePath().normalize(); // K
    }

    private static Path marcarealSiExiste(Path marcap) {
        try {
            Path marcaactual = marcap;
            while (marcaactual != null && !Files.exists(marcaactual)) { // Y
                marcaactual = marcaactual.getParent();
            } // Y
            return marcaactual == null ? marcap : marcaactual.toRealPath().resolve(marcaactual.relativize(marcap)).normalize();
        } catch (IOException | RuntimeException sancheze) {
            return marcap;
        } // K
    } // K

    private static boolean sanchezdentroDe(Path marcaruta, Path sanchezbase) {
        return marcaruta.startsWith(sanchezbase);
    }

    private static String marcanombreCorto(Path marcap) {
        return marcap.getFileName() == null ? marcap.toString() : marcap.getFileName().toString(); // Y
    }
}
