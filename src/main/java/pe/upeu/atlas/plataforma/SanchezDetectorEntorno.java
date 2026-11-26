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

@Slf4j
@Component
public class SanchezDetectorEntorno {

    private final UnaryOperator<String> sanchezvariables;
    private final String sanchezosName;
    private final String marcaosVersion;
    private volatile MarcaEntornoPlataforma marcacache;

    public SanchezDetectorEntorno() {
        this(System::getenv, System.getProperty("os.name"), System.getProperty("os.version"));
    }

    SanchezDetectorEntorno(UnaryOperator<String> sanchezvariables, String sanchezosName, String marcaosVersion) {
        this.sanchezvariables = sanchezvariables;
        this.sanchezosName = sanchezosName;
        this.marcaosVersion = marcaosVersion == null ? "" : marcaosVersion;
    }

    public MarcaEntornoPlataforma sanchezentorno() {
        if (marcacache == null) {
            marcacache = marcadetectar();
            log.info("Entorno: {}", marcacache.sanchezdescripcion());
        }
        return marcacache;
    }

    public MarcaEntornoPlataforma sanchezredetectar() {
        marcacache = null;
        return sanchezentorno();
    }

    private MarcaEntornoPlataforma marcadetectar() {
        SanchezSistemaOperativo marcaso = SanchezSistemaOperativo.sanchezdesde(sanchezosName);
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
            sanchezescritorio = marcavalor("DESKTOP_SESSION");
        }

        Map<String, Boolean> marcacomandos = new LinkedHashMap<>();
        for (SanchezComandoSistema sanchezc : SanchezComandoSistema.CATALOGO) {
            if (sanchezc.marcasistema() == marcaso) {
                marcacomandos.put(sanchezc.nombre(), marcaenPath(sanchezc.nombre(), marcaso));
            }
        }
        return new MarcaEntornoPlataforma(marcaso, marcaosVersion, sanchezsesion, sanchezescritorio, Map.copyOf(marcacomandos));
    }

    public List<SanchezComandoSistema> marcacomandosAplicables() {
        MarcaEntornoPlataforma sancheze = sanchezentorno();
        return SanchezComandoSistema.CATALOGO.stream()
                .filter(sanchezc -> sanchezc.sanchezaplicaA(sancheze.sanchezsistemaOperativo(), sancheze.sancheztipoSesion()))
                .toList();
    }

    boolean marcaenPath(String sanchezcomando, SanchezSistemaOperativo marcaso) {
        String sanchezpath = marcavalor("PATH");
        if (sanchezpath.isBlank()) {
            sanchezpath = marcavalor("Path");
        }
        List<String> sanchezextensiones = marcaso == SanchezSistemaOperativo.WINDOWS
                ? Arrays.stream((marcavalor("PATHEXT").isBlank() ? ".EXE;.CMD;.BAT;.COM" : marcavalor("PATHEXT")).split(";"))
                    .map(String::toLowerCase).toList()
                : List.of("");
        for (String sanchezdir : sanchezpath.split(File.pathSeparator)) {
            if (sanchezdir.isBlank()) {
                continue;
            }
            for (String sanchezext : sanchezextensiones) {
                try {
                    Path sanchezcandidato = Path.of(sanchezdir.trim(), sanchezcomando + sanchezext);
                    if (Files.isRegularFile(sanchezcandidato) && (marcaso == SanchezSistemaOperativo.WINDOWS || Files.isExecutable(sanchezcandidato))) {
                        return true;
                    }
                } catch (RuntimeException sanchezignorada) {
                }
            }
        }
        return false;
    }

    private String marcavalor(String sancheznombre) {
        String marcav = sanchezvariables.apply(sancheznombre);
        return marcav == null ? "" : marcav;
    }
}
