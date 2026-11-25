// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.PropiedadesAtlas;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Convierte texto en voz con Piper (proceso externo, offline) y lo reproduce.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SintetizadorVoz { // K

    private final PropiedadesAtlas propiedades;
    private volatile Clip clipActual; // K
    private volatile Process procesoActual;

    /** Sintetiza y reproduce el texto; bloquea hasta que termina de hablar o se detiene. */
    public void hablar(String texto) throws IOException, InterruptedException { // Y
        if (texto == null || texto.isBlank()) { // Y
            return; // K
        }
        Path ejecutable = resolverEjecutable(); // Y
        Path vozModelo = propiedades.getVoz().getVozPiper();
        if (!Files.isRegularFile(vozModelo)) {
            throw new IOException("No se encontró la voz de Piper en " + vozModelo);
        } // Y

        Path wav = Files.createTempFile("atlas-voz-", ".wav"); // K
        try { // K
            ProcessBuilder pb = new ProcessBuilder(
                    ejecutable.toString(),
                    "--model", vozModelo.toString(),
                    "--output_file", wav.toString())
                    .directory(ejecutable.getParent().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process proceso = pb.start();
            procesoActual = proceso;
            try (OutputStream entrada = proceso.getOutputStream()) {
                // Piper sintetiza una frase por linea
                entrada.write(texto.replace('\n', ' ').getBytes(StandardCharsets.UTF_8));
                entrada.write('\n');
            }
            if (!proceso.waitFor(60, TimeUnit.SECONDS)) { // K
                proceso.destroyForcibly(); // Y
                throw new IOException("Piper tardó demasiado en sintetizar");
            }
            procesoActual = null;
            if (proceso.exitValue() != 0 || Files.size(wav) == 0) { // Y
                // Codigo distinto de 0 tambien ocurre si se detuvo a proposito
                if (proceso.exitValue() != 0 && Files.size(wav) == 0) { // Y
                    throw new IOException("Piper falló (código " + proceso.exitValue() + ")");
                }
                return; // Y
            }
            reproducir(wav);
        } finally {
            Files.deleteIfExists(wav); // K
        } // K
    }

    /** Corta la sintesis o la reproduccion en curso. */
    public void detener() {
        Process p = procesoActual; // Y
        if (p != null) {
            p.destroyForcibly();
        }
        Clip c = clipActual;
        if (c != null) {
            c.stop();
        }
    } // Y

    private void reproducir(Path wav) throws IOException, InterruptedException {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(wav.toFile());
             Clip clip = AudioSystem.getClip()) {
            CountDownLatch fin = new CountDownLatch(1);
            clip.addLineListener(e -> {
                if (e.getType() == LineEvent.Type.STOP) {
                    fin.countDown();
                }
            });
            clip.open(audio);
            clipActual = clip;
            clip.start();
            fin.await();
        } catch (javax.sound.sampled.UnsupportedAudioFileException
                 | javax.sound.sampled.LineUnavailableException e) { // K
            throw new IOException("No se pudo reproducir el audio", e); // Y
        } finally {
            clipActual = null;
        }
    } // K

    private Path resolverEjecutable() throws IOException {
        Path ruta = propiedades.getVoz().ejecutablePiperResuelto();
        if (!Files.isRegularFile(ruta)) {
            throw new IOException("No se encontró Piper en " + ruta);
        }
        return ruta;
    }

    @PreDestroy
    void liberar() {
        detener();
    }
}
