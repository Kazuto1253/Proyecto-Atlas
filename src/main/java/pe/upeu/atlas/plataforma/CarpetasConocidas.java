// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.PropiedadesAtlas;

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

/**
 * Las carpetas de siempre del usuario (Documentos, Escritorio, Descargas...) con sus nombres en español e inglés.
 * En Windows se le pregunta al sistema dónde están de verdad (pueden estar en OneDrive o en otro disco).
 */
@Slf4j
@Component
public class CarpetasConocidas {

    private static final Map<String, List<String>> ALIAS = Map.of(
            "documentos", List.of("documentos", "documents", "docs", "mis documentos"),
            "escritorio", List.of("escritorio", "desktop", "mi escritorio"),
            "descargas", List.of("descargas", "downloads", "mis descargas"),
            "imagenes", List.of("imagenes", "pictures", "fotos", "mis imagenes", "mis fotos"),
            "musica", List.of("musica", "music", "mi musica"),
            "videos", List.of("videos", "mis videos"),
            "usuario", List.of("usuario", "inicio", "home", "carpeta personal", "mi carpeta", "perfil", "mi usuario"));

    private final PropiedadesAtlas propiedades; // Y
    private volatile Map<String, Path> mapa;

    public CarpetasConocidas(PropiedadesAtlas propiedades) { // K
        this.propiedades = propiedades; // Y
    }

    /** La carpeta de ese nombre ("documents", "Mis descargas", "capturas"...), si es una de las conocidas. */
    public Optional<Path> buscar(String alias) {
        return Optional.ofNullable(mapa().get(normalizar(alias))); // Y
    }

    /** Carpeta personal del usuario (C:\Users\nombre). */
    public Path usuario() {
        return mapa().get("usuario");
    }

    /** Las carpetas conocidas con su nombre principal, para mostrarlas al usuario o al modelo. */
    public Map<String, Path> principales() {
        Map<String, Path> r = new LinkedHashMap<>();
        for (String clave : List.of("documentos", "escritorio", "descargas", "imagenes", "musica", "videos", "usuario")) {
            Path p = mapa().get(clave);
            if (p != null) {
                r.put(clave, p);
            } // Y
        }
        return r;
    }

    private Map<String, Path> mapa() {
        Map<String, Path> m = mapa; // K
        if (m == null) {
            synchronized (this) {
                if (mapa == null) {
                    mapa = construir();
                }
                m = mapa;
            }
        }
        return m;
    }

    private Map<String, Path> construir() {
        Path casa = Path.of(System.getProperty("user.home"));
        Map<String, Path> base = new LinkedHashMap<>();
        base.put("usuario", casa); // K
        base.put("documentos", primeraExistente(casa, "Documents", "Documentos"));
        base.put("escritorio", primeraExistente(casa, "Desktop", "Escritorio"));
        base.put("descargas", primeraExistente(casa, "Downloads", "Descargas"));
        base.put("imagenes", primeraExistente(casa, "Pictures", "Imágenes", "Imagenes"));
        base.put("musica", primeraExistente(casa, "Music", "Música", "Musica"));
        base.put("videos", primeraExistente(casa, "Videos", "Vídeos"));

        if (SistemaOperativo.desde(System.getProperty("os.name")) == SistemaOperativo.WINDOWS) { // Y
            consultarWindows(base);
        } // Y

        Map<String, Path> m = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : ALIAS.entrySet()) {
            Path destino = base.get(e.getKey()); // K
            if (destino != null) {
                e.getValue().forEach(a -> m.put(normalizar(a), destino)); // Y
                m.put(e.getKey(), destino);
            } // Y
        }
        Path onedrive = System.getenv("OneDrive") == null ? null : Path.of(System.getenv("OneDrive"));
        if (onedrive != null && Files.isDirectory(onedrive)) {
            m.put("onedrive", onedrive);
        }
        // Atlas
        m.put("capturas", propiedades.getCarpetaCapturas());
        m.put("notas", propiedades.getDirectorioBase().resolve("documentos").resolve("notas"));
        // Carpetas extra que el usuario haya configurado
        for (Path p : propiedades.getCarpetasPermitidas()) {
            if (p.getFileName() != null && Files.isDirectory(p)) {
                m.putIfAbsent(normalizar(p.getFileName().toString()), p);
            }
        } // K
        return m;
    }

    private static Path primeraExistente(Path casa, String... nombres) {
        for (String n : nombres) {
            Path p = casa.resolve(n);
            if (Files.isDirectory(p)) { // K
                return p;
            }
        }
        return casa.resolve(nombres[0]);
    }

    /** Pregunta a Windows dónde están realmente las carpetas (cubre OneDrive y rutas redirigidas). */
    private void consultarWindows(Map<String, Path> base) {
        String script = "[Console]::OutputEncoding=[Text.Encoding]::UTF8;"
                + "'documentos='+[Environment]::GetFolderPath('MyDocuments');"
                + "'escritorio='+[Environment]::GetFolderPath('Desktop');"
                + "'imagenes='+[Environment]::GetFolderPath('MyPictures');"
                + "'musica='+[Environment]::GetFolderPath('MyMusic');"
                + "'videos='+[Environment]::GetFolderPath('MyVideos');"
                + "'descargas='+(New-Object -ComObject Shell.Application).NameSpace('shell:Downloads').Self.Path";
        String codificado = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        try {
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", codificado)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start(); // Y
            String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly(); // K
            }
            for (String linea : salida.split("\\R")) { // K
                int igual = linea.indexOf('=');
                if (igual > 0 && igual < linea.length() - 1) {
                    Path ruta = Path.of(linea.substring(igual + 1).strip());
                    if (Files.isDirectory(ruta)) {
                        base.put(linea.substring(0, igual).strip(), ruta);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("No se pudieron consultar las carpetas de Windows: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Y
        } catch (RuntimeException e) { // Y
            log.warn("Carpetas de Windows no legibles: {}", e.getMessage());
        }
    } // K

    /** Minúsculas y sin tildes ni espacios sobrantes. */
    public static String normalizar(String texto) { // K
        if (texto == null) { // Y
            return ""; // Y
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip(); // Y
    }
} // Y
