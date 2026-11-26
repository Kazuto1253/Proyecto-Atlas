package pe.upeu.atlas.plataforma;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MarcaCarpetasConocidas {

    private static final Map<String, List<String>> ALIAS = Map.of(
            "documentos", List.of("documentos", "documents", "docs", "mis documentos"),
            "escritorio", List.of("escritorio", "desktop", "mi escritorio"),
            "descargas", List.of("descargas", "downloads", "mis descargas"),
            "imagenes", List.of("imagenes", "pictures", "fotos", "mis imagenes", "mis fotos"),
            "musica", List.of("musica", "music", "mi musica"),
            "videos", List.of("videos", "mis videos"),
            "usuario", List.of("usuario", "inicio", "home", "carpeta personal", "mi carpeta", "perfil", "mi usuario"));

    private final SanchezPropiedadesAtlas marcapropiedades;
    private volatile Map<String, Path> sanchezmapa;

    public MarcaCarpetasConocidas(SanchezPropiedadesAtlas marcapropiedades) {
        this.marcapropiedades = marcapropiedades;
    }

    public Optional<Path> buscar(String marcaalias) {
        return Optional.ofNullable(sanchezmapa().get(sancheznormalizar(marcaalias)));
    }

    public Path marcausuario() {
        return sanchezmapa().get("usuario");
    }

    public Map<String, Path> marcaprincipales() {
        Map<String, Path> marcar = new LinkedHashMap<>();
        for (String sanchezclave : List.of("documentos", "escritorio", "descargas", "imagenes", "musica", "videos", "usuario")) {
            Path marcap = sanchezmapa().get(sanchezclave);
            if (marcap != null) {
                marcar.put(sanchezclave, marcap);
            }
        }
        return marcar;
    }

    private Map<String, Path> sanchezmapa() {
        Map<String, Path> sanchezm = sanchezmapa;
        if (sanchezm == null) {
            synchronized (this) {
                if (sanchezmapa == null) {
                    sanchezmapa = sanchezconstruir();
                }
                sanchezm = sanchezmapa;
            }
        }
        return sanchezm;
    }

    private Map<String, Path> sanchezconstruir() {
        Path marcacasa = Path.of(System.getProperty("user.home"));
        Map<String, Path> sanchezbase = new LinkedHashMap<>();
        sanchezbase.put("usuario", marcacasa);
        sanchezbase.put("documentos", sanchezprimeraExistente(marcacasa, "Documents", "Documentos"));
        sanchezbase.put("escritorio", sanchezprimeraExistente(marcacasa, "Desktop", "Escritorio"));
        sanchezbase.put("descargas", sanchezprimeraExistente(marcacasa, "Downloads", "Descargas"));
        sanchezbase.put("imagenes", sanchezprimeraExistente(marcacasa, "Pictures", "Imágenes", "Imagenes"));
        sanchezbase.put("musica", sanchezprimeraExistente(marcacasa, "Music", "Música", "Musica"));
        sanchezbase.put("videos", sanchezprimeraExistente(marcacasa, "Videos", "Vídeos"));

        if (SanchezSistemaOperativo.sanchezdesde(System.getProperty("os.name")) == SanchezSistemaOperativo.WINDOWS) {
            marcaconsultarWindows(sanchezbase);
        }

        Map<String, Path> sanchezm = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> sancheze : ALIAS.entrySet()) {
            Path marcadestino = sanchezbase.get(sancheze.getKey());
            if (marcadestino != null) {
                sancheze.getValue().forEach(sancheza -> sanchezm.put(sancheznormalizar(sancheza), marcadestino));
                sanchezm.put(sancheze.getKey(), marcadestino);
            }
        }
        Path marcaonedrive = System.getenv("OneDrive") == null ? null : Path.of(System.getenv("OneDrive"));
        if (marcaonedrive != null && Files.isDirectory(marcaonedrive)) {
            sanchezm.put("onedrive", marcaonedrive);
        }
        sanchezm.put("capturas", marcapropiedades.getCarpetaCapturas());
        sanchezm.put("notas", marcapropiedades.getDirectorioBase().resolve("documentos").resolve("notas"));
        for (Path marcap : marcapropiedades.getCarpetasPermitidas()) {
            if (marcap.getFileName() != null && Files.isDirectory(marcap)) {
                sanchezm.putIfAbsent(sancheznormalizar(marcap.getFileName().toString()), marcap);
            }
        }
        return sanchezm;
    }

    private static Path sanchezprimeraExistente(Path marcacasa, String... marcanombres) {
        for (String marcan : marcanombres) {
            Path marcap = marcacasa.resolve(marcan);
            if (Files.isDirectory(marcap)) {
                return marcap;
            }
        }
        return marcacasa.resolve(marcanombres[0]);
    }

    private void marcaconsultarWindows(Map<String, Path> sanchezbase) {
        String sanchezscript = "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                + "'documentos='+[Environment]::GetFolderPath('MyDocuments');"
                + "'escritorio='+[Environment]::GetFolderPath('Desktop');"
                + "'imagenes='+[Environment]::GetFolderPath('MyPictures');"
                + "'musica='+[Environment]::GetFolderPath('MyMusic');"
                + "'videos='+[Environment]::GetFolderPath('MyVideos');"
                + "'descargas='+(New-Object -ComObject Shell.Application).NameSpace('shell:Downloads').Self.Path";
        String sanchezcodificado = Base64.getEncoder().encodeToString(sanchezscript.getBytes(StandardCharsets.UTF_16LE));
        try {
            Process marcap = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", sanchezcodificado)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String marcasalida = new String(marcap.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!marcap.waitFor(15, TimeUnit.SECONDS)) {
                marcap.destroyForcibly();
            }
            for (String sanchezlinea : marcasalida.split("\\R")) {
                int marcaigual = sanchezlinea.indexOf('=');
                if (marcaigual > 0 && marcaigual < sanchezlinea.length() - 1) {
                    Path marcaruta = Path.of(sanchezlinea.substring(marcaigual + 1).strip());
                    if (Files.isDirectory(marcaruta)) {
                        sanchezbase.put(sanchezlinea.substring(0, marcaigual).strip(), marcaruta);
                    }
                }
            }
        } catch (IOException sancheze) {
            log.warn("No se pudieron consultar las carpetas de Windows: {}", sancheze.getMessage());
        } catch (InterruptedException sancheze) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException sancheze) {
            log.warn("Carpetas de Windows no legibles: {}", sancheze.getMessage());
        }
    }

    public static String sancheznormalizar(String marcatexto) {
        if (marcatexto == null) {
            return "";
        }
        return Normalizer.normalize(marcatexto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
