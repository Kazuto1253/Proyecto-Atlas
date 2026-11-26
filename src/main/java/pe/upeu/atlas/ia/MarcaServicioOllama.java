// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ia;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.atlas.ollama.autoarranque", matchIfMissing = true)
public class MarcaServicioOllama {

    private final SanchezPropiedadesAtlas marcapropiedades;
    private volatile Process sanchezproceso;

    @EventListener(ApplicationStartedEvent.class)
    public void marcaalIniciar() {
        Thread marcahilo = new Thread(this::sanchezasegurar, "atlas-ollama");
        marcahilo.setDaemon(true);
        marcahilo.start();
    }

    void sanchezasegurar() {
        URI marcadireccion = URI.create(marcapropiedades.getLlm().getUrl());
        if (!marcaesLocal(marcadireccion) || sanchezresponde(marcadireccion)) {
            return;
        }
        Path marcaproyecto = Path.of(System.getProperty("user.dir"));
        Optional<Path> sanchezejecutable = marcabuscarEjecutable(marcaproyecto, System.getenv());
        if (sanchezejecutable.isEmpty()) {
            log.warn("No se encontró Ollama. Ejecuta INSTALAR.bat en la carpeta del proyecto.");
            return;
        }
        try {
            ProcessBuilder marcacomando = new ProcessBuilder(sanchezejecutable.get().toString(), "serve")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            int sanchezpuerto = marcadireccion.getPort() > 0 ? marcadireccion.getPort() : 11434;
            marcacomando.environment().put("OLLAMA_HOST", marcadireccion.getHost() + ":" + sanchezpuerto);
            Path marcamodelos = marcaproyecto.resolve("herramientas").resolve("ollama-modelos");
            if (Files.isDirectory(marcamodelos)) {
                marcacomando.environment().put("OLLAMA_MODELS", marcamodelos.toAbsolutePath().toString());
            }
            sanchezproceso = marcacomando.start();
            log.info("Ollama iniciado por Atlas desde {}", sanchezejecutable.get());
        } catch (IOException sancheze) {
            log.warn("No se pudo iniciar Ollama", sancheze);
        }
    }

    static boolean marcaesLocal(URI sanchezdireccion) {
        String marcaservidor = sanchezdireccion.getHost();
        return "localhost".equalsIgnoreCase(marcaservidor) || "127.0.0.1".equals(marcaservidor);
    }

    static Optional<Path> marcabuscarEjecutable(Path sanchezproyecto, Map<String, String> marcaentorno) {
        List<Path> sanchezcandidatos = new ArrayList<>();
        sanchezcandidatos.add(sanchezproyecto.resolve("herramientas").resolve("ollama").resolve("ollama.exe"));
        String marcalocal = marcaentorno.get("LOCALAPPDATA");
        if (marcalocal != null) {
            sanchezcandidatos.add(Path.of(marcalocal, "Programs", "Ollama", "ollama.exe"));
        }
        String sanchezrutas = marcaentorno.getOrDefault("Path", marcaentorno.getOrDefault("PATH", ""));
        for (String marcacarpeta : sanchezrutas.split(Pattern.quote(File.pathSeparator))) {
            if (!marcacarpeta.isBlank() && !marcacarpeta.contains("\"")) {
                sanchezcandidatos.add(Path.of(marcacarpeta, "ollama.exe"));
                sanchezcandidatos.add(Path.of(marcacarpeta, "ollama"));
            }
        }
        return sanchezcandidatos.stream().filter(Files::isRegularFile).findFirst();
    }

    private boolean sanchezresponde(URI marcadireccion) {
        try {
            HttpClient sanchezcliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest marcapedido = HttpRequest.newBuilder(marcadireccion.resolve("/api/tags")).timeout(Duration.ofSeconds(3)).build();
            return sanchezcliente.send(marcapedido, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException sancheze) {
            return false;
        } catch (InterruptedException sancheze) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @PreDestroy
    void marcadetener() {
        Process sanchezactual = sanchezproceso;
        if (sanchezactual != null && sanchezactual.isAlive()) {
            sanchezactual.descendants().forEach(ProcessHandle::destroy);
            sanchezactual.destroy();
        }
    }
}
