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
public class DetectorEntorno {

    private final UnaryOperator<String> variables;
    private final String osName;
    private final String osVersion;
    private volatile EntornoPlataforma cache; // Y

    public DetectorEntorno() {
        this(System::getenv, System.getProperty("os.name"), System.getProperty("os.version"));
    }

    /** Constructor para pruebas: variables de entorno y SO simulados. */
    DetectorEntorno(UnaryOperator<String> variables, String osName, String osVersion) {
        this.variables = variables; // Y
        this.osName = osName;
        this.osVersion = osVersion == null ? "" : osVersion;
    }

    /** Entorno detectado (se calcula una vez). */
    public EntornoPlataforma entorno() {
        if (cache == null) { // Y
            cache = detectar();
            log.info("Entorno: {}", cache.descripcion()); // K
        }
        return cache;
    }

    /** Vuelve a detectar (p. ej. despues de instalar un paquete). */
    public EntornoPlataforma redetectar() { // Y
        cache = null;
        return entorno();
    }

    private EntornoPlataforma detectar() {
        SistemaOperativo so = SistemaOperativo.desde(osName); // Y
        TipoSesion sesion = switch (so) {
            case WINDOWS -> TipoSesion.WINDOWS;
            case LINUX -> TipoSesion.desdeLinux(
                    variables.apply("XDG_SESSION_TYPE"),
                    variables.apply("WAYLAND_DISPLAY"),
                    variables.apply("DISPLAY"));
            default -> TipoSesion.DESCONOCIDA;
        };
        String escritorio = valor("XDG_CURRENT_DESKTOP");
        if (escritorio.isBlank()) {
            escritorio = valor("DESKTOP_SESSION"); // Y
        } // Y

        Map<String, Boolean> comandos = new LinkedHashMap<>();
        for (ComandoSistema c : ComandoSistema.CATALOGO) {
            if (c.sistema() == so) {
                comandos.put(c.nombre(), enPath(c.nombre(), so)); // K
            } // K
        }
        return new EntornoPlataforma(so, osVersion, sesion, escritorio, Map.copyOf(comandos)); // Y
    }

    /** Comandos del catalogo que aplican a este SO y sesion. */
    public List<ComandoSistema> comandosAplicables() {
        EntornoPlataforma e = entorno();
        return ComandoSistema.CATALOGO.stream()
                .filter(c -> c.aplicaA(e.sistemaOperativo(), e.tipoSesion()))
                .toList();
    }

    /** Busca un ejecutable en el PATH (en Windows prueba las extensiones de PATHEXT). */
    boolean enPath(String comando, SistemaOperativo so) {
        String path = valor("PATH");
        if (path.isBlank()) {
            path = valor("Path");
        }
        List<String> extensiones = so == SistemaOperativo.WINDOWS
                ? Arrays.stream((valor("PATHEXT").isBlank() ? ".EXE;.CMD;.BAT;.COM" : valor("PATHEXT")).split(";"))
                    .map(String::toLowerCase).toList()
                : List.of("");
        for (String dir : path.split(File.pathSeparator)) { // K
            if (dir.isBlank()) {
                continue;
            } // K
            for (String ext : extensiones) {
                try {
                    Path candidato = Path.of(dir.trim(), comando + ext); // Y
                    if (Files.isRegularFile(candidato) && (so == SistemaOperativo.WINDOWS || Files.isExecutable(candidato))) {
                        return true; // Y
                    }
                } catch (RuntimeException ignorada) {
                    // Entrada del PATH con caracteres invalidos: se salta
                } // K
            } // K
        }
        return false; // Y
    }

    private String valor(String nombre) {
        String v = variables.apply(nombre);
        return v == null ? "" : v;
    } // Y
}
