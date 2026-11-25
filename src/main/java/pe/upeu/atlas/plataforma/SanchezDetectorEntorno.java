// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Detecta SO, tipo de sesion grafica, escritorio y que comandos de la lista blanca estan
 * instalados. No ejecuta nada: solo busca los binarios en el PATH.
 */
@Slf4j
@Component
public class SanchezDetectorEntorno {

    private final UnaryOperator<String> sanchezvariables;
    private final String sanchezosName;
    private final String marcaosVersion;
    private volatile MarcaEntornoPlataforma marcacache; // Y

    public SanchezDetectorEntorno() {
        this(System::getenv, System.getProperty("os.name"), System.getProperty("os.version"));
    }

    /** Constructor para pruebas: variables de entorno y SO simulados. */
    SanchezDetectorEntorno(UnaryOperator<String> sanchezvariables, String sanchezosName, String marcaosVersion) {
        this.sanchezvariables = sanchezvariables; // Y
        this.sanchezosName = sanchezosName;
        this.marcaosVersion = marcaosVersion == null ? "" : marcaosVersion;
    }

    /** Entorno detectado (se calcula una vez). */
    public MarcaEntornoPlataforma sanchezentorno() {
        if (marcacache == null) { // Y
            marcacache = marcadetectar();
            log.info("Entorno: {}", marcacache.sanchezdescripcion()); // K
        }
        return marcacache;
    }

    /** Vuelve a detectar (p. ej. despues de instalar un paquete). */
    public MarcaEntornoPlataforma sanchezredetectar() { // Y
        marcacache = null;
        return sanchezentorno();
    }

    private MarcaEntornoPlataforma marcadetectar() {
        SanchezSistemaOperativo marcaso = SanchezSistemaOperativo.sanchezdesde(sanchezosName); // Y
        SanchezTipoSesion sanchezsesion = switch (marcaso) {
            case WINDOWS -> SanchezTipoSesion.WINDOWS;
            case LINUX -> SanchezTipoSesion.sanchezdesdeLinux(
                    sanchezvariables.apply("XDG_SESSION_TYPE"),
                    sanchezvariables.apply("WAYLAND_DISPLAY"),
                    sanchezvariables.apply("DISPLAY"));
            default -> SanchezTipoSesion.DESCONOCIDA;
        };
        String sanchezescritorio = marcavalor("XDG_CURRENT_DESKTOP");
        if (sanchezescritorio.isBlank()) {
            sanchezescritorio = marcavalor("DESKTOP_SESSION"); // Y
        } // Y

        Map<String, Boolean> marcacomandos = new LinkedHashMap<>();
        for (SanchezComandoSistema sanchezc : SanchezComandoSistema.CATALOGO) {
            if (sanchezc.marcasistema() == marcaso) {
                marcacomandos.put(sanchezc.nombre(), marcaenPath(sanchezc.nombre(), marcaso)); // K
            } // K
        }
        return new MarcaEntornoPlataforma(marcaso, marcaosVersion, sanchezsesion, sanchezescritorio, Map.copyOf(marcacomandos)); // Y
    }

    /** Comandos del catalogo que aplican a este SO y sesion. */
    public List<SanchezComandoSistema> marcacomandosAplicables() {
        MarcaEntornoPlataforma sancheze = sanchezentorno();
        return SanchezComandoSistema.CATALOGO.stream()
                .filter(sanchezc -> sanchezc.sanchezaplicaA(sancheze.sanchezsistemaOperativo(), sancheze.sancheztipoSesion()))
                .toList();
    }

    /** Busca un ejecutable en el PATH (en Windows prueba las extensiones de PATHEXT). */
    boolean marcaenPath(String sanchezcomando, SanchezSistemaOperativo marcaso) {
        String sanchezpath = marcavalor("PATH");
        if (sanchezpath.isBlank()) {
            sanchezpath = marcavalor("Path");
        }
        List<String> sanchezextensiones = marcaso == SanchezSistemaOperativo.WINDOWS
                ? Arrays.stream((marcavalor("PATHEXT").isBlank() ? ".EXE;.CMD;.BAT;.COM" : marcavalor("PATHEXT")).split(";"))
                    .map(String::toLowerCase).toList()
                : List.of("");
        for (String sanchezdir : sanchezpath.split(File.pathSeparator)) { // K
            if (sanchezdir.isBlank()) {
                continue;
            } // K
            for (String sanchezext : sanchezextensiones) {
                try {
                    Path sanchezcandidato = Path.of(sanchezdir.trim(), sanchezcomando + sanchezext); // Y
                    if (Files.isRegularFile(sanchezcandidato) && (marcaso == SanchezSistemaOperativo.WINDOWS || Files.isExecutable(sanchezcandidato))) {
                        return true; // Y
                    }
                } catch (RuntimeException sanchezignorada) {
                    // Entrada del PATH con caracteres invalidos: se salta
                } // K
            } // K
        }
        return false; // Y
    }

    private String marcavalor(String sancheznombre) {
        String marcav = sanchezvariables.apply(sancheznombre);
        return marcav == null ? "" : marcav;
    } // Y
}
