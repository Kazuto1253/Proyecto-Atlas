// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class SanchezSintetizadorVoz { // K

    private final SanchezPropiedadesAtlas marcapropiedades;
    private volatile Clip marcaclipActual; // K
    private volatile Process sanchezprocesoActual;

    public void marcahablar(String marcatexto) throws IOException, InterruptedException { // Y
        if (marcatexto == null || marcatexto.isBlank()) { // Y
            return; // K
        }
        Path marcaejecutable = marcaresolverEjecutable(); // Y
        Path sanchezvozModelo = marcapropiedades.getVoz().getVozPiper();
        if (!Files.isRegularFile(sanchezvozModelo)) {
            throw new IOException("No se encontró la voz de Piper en " + sanchezvozModelo);
        } // Y

        Path marcawav = Files.createTempFile("atlas-voz-", ".wav"); // K
        try { // K
            ProcessBuilder marcapb = new ProcessBuilder(
                    marcaejecutable.toString(),
                    "--model", sanchezvozModelo.toString(),
                    "--output_file", marcawav.toString())
                    .directory(marcaejecutable.getParent().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process sanchezproceso = marcapb.start();
            sanchezprocesoActual = sanchezproceso;
            try (OutputStream sanchezentrada = sanchezproceso.getOutputStream()) {
                sanchezentrada.write(marcatexto.replace('\n', ' ').getBytes(StandardCharsets.UTF_8));
                sanchezentrada.write('\n');
            }
            if (!sanchezproceso.waitFor(60, TimeUnit.SECONDS)) { // K
                sanchezproceso.destroyForcibly(); // Y
                throw new IOException("Piper tardó demasiado en sintetizar");
            }
            sanchezprocesoActual = null;
            if (sanchezproceso.exitValue() != 0 || Files.size(marcawav) == 0) { // Y
                if (sanchezproceso.exitValue() != 0 && Files.size(marcawav) == 0) { // Y
                    throw new IOException("Piper falló (código " + sanchezproceso.exitValue() + ")");
                }
                return; // Y
            }
            sanchezreproducir(marcawav);
        } finally {
            Files.deleteIfExists(marcawav); // K
        } // K
    }

    public void sanchezdetener() {
        Process marcap = sanchezprocesoActual; // Y
        if (marcap != null) {
            marcap.destroyForcibly();
        }
        Clip sanchezc = marcaclipActual;
        if (sanchezc != null) {
            sanchezc.stop();
        }
    } // Y

    private void sanchezreproducir(Path marcawav) throws IOException, InterruptedException {
        try (AudioInputStream marcaaudio = AudioSystem.getAudioInputStream(marcawav.toFile());
             Clip marcaclip = AudioSystem.getClip()) {
            CountDownLatch sanchezfin = new CountDownLatch(1);
            marcaclip.addLineListener(sancheze -> {
                if (sancheze.getType() == LineEvent.Type.STOP) {
                    sanchezfin.countDown();
                }
            });
            marcaclip.open(marcaaudio);
            marcaclipActual = marcaclip;
            marcaclip.start();
            sanchezfin.await();
        } catch (javax.sound.sampled.UnsupportedAudioFileException
                 | javax.sound.sampled.LineUnavailableException sancheze) { // K
            throw new IOException("No se pudo reproducir el audio", sancheze); // Y
        } finally {
            marcaclipActual = null;
        }
    } // K

    private Path marcaresolverEjecutable() throws IOException {
        Path marcaruta = marcapropiedades.getVoz().ejecutablePiperResuelto();
        if (!Files.isRegularFile(marcaruta)) {
            throw new IOException("No se encontró Piper en " + marcaruta);
        }
        return marcaruta;
    }

    @PreDestroy
    void sanchezliberar() {
        sanchezdetener();
    }
}
