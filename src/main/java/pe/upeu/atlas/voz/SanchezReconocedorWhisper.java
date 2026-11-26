// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SanchezReconocedorWhisper {

    static final String PISTA = "Atlas, asistente de voz. Abre la carpeta de descargas, documentos, escritorio o imágenes. "
            + "¿Qué hora es? Sube el volumen. Toma una captura de pantalla. Según mis documentos.";

    private static final Pattern ALUCINACION = Pattern.compile(
            "(?i)(gracias por (ver|vernos|su atenci[oó]n)|suscr[ií]bete|subt[ií]tulos|amara\\.org|"
                    + "dale like|hasta la pr[oó]xima|nos vemos en el pr[oó]ximo|el canal)");
    private static final Pattern ETIQUETAS = Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)|\\*[^*]*\\*|♪");

    private final SanchezPropiedadesAtlas marcapropiedades; // Y
    private volatile Boolean sanchezavisado;
    private volatile Process sanchezprocesoActual; // K

    public void sanchezcancelar() {
        Process marcap = sanchezprocesoActual;
        if (marcap != null) {
            marcap.destroyForcibly(); // K
        } // Y
    }

    public boolean sanchezdisponible() {
        var sanchezvoz = marcapropiedades.getVoz();
        boolean sanchezlisto = sanchezvoz.isUsarWhisper()
                && sanchezvoz.getWhisperEjecutable() != null && Files.isRegularFile(sanchezvoz.getWhisperEjecutable())
                && sanchezvoz.getWhisperModelo() != null && Files.isRegularFile(sanchezvoz.getWhisperModelo());
        if (!sanchezlisto && sanchezvoz.isUsarWhisper() && sanchezavisado == null) {
            sanchezavisado = Boolean.TRUE; // K
            log.info("Whisper no está instalado ({}): se usa solo Vosk. Ver la Guía de Ejecución.", sanchezvoz.getWhisperEjecutable());
        }
        return sanchezlisto; // Y
    }

    public Optional<String> sancheztranscribir(byte[] marcapcm) {
        if (marcapcm == null || marcapcm.length < 3200 || !sanchezdisponible()) {
            return Optional.empty();
        }
        var sanchezvoz = marcapropiedades.getVoz(); // K
        Path marcawav = null;
        try { // Y
            marcawav = Files.createTempFile("atlas-voz-", ".wav");
            AudioFormat marcaformato = new AudioFormat(sanchezvoz.getFrecuenciaMuestreo(), 16, 1, true, false); // Y
            try (AudioInputStream marcaaudio = new AudioInputStream(new ByteArrayInputStream(marcapcm), marcaformato, marcapcm.length / 2)) {
                AudioSystem.write(marcaaudio, AudioFileFormat.Type.WAVE, marcawav.toFile());
            }
            long sanchezinicio = System.currentTimeMillis();
            Process sanchezproceso = new ProcessBuilder(
                    sanchezvoz.getWhisperEjecutable().toString(),
                    "-m", sanchezvoz.getWhisperModelo().toString(),
                    "-f", marcawav.toString(),
                    "-l", "es", "-nt", "-np",
                    "-t", String.valueOf(sanchezvoz.getWhisperHilos()),
                    "--prompt", PISTA)
                    .directory(marcawav.getParent().toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            sanchezprocesoActual = sanchezproceso; // K
            byte[] marcasalida = sanchezproceso.getInputStream().readAllBytes();
            if (!sanchezproceso.waitFor(90, TimeUnit.SECONDS)) {
                sanchezproceso.destroyForcibly();
                log.warn("Whisper tardó más de 90 s: se descarta");
                return Optional.empty();
            } // K
            String marcatexto = marcalimpiar(new String(marcasalida, StandardCharsets.UTF_8));
            log.info("Whisper: «{}» ({} ms)", marcatexto, System.currentTimeMillis() - sanchezinicio);
            return marcatexto.isEmpty() ? Optional.empty() : Optional.of(marcatexto); // Y
        } catch (IOException sancheze) {
            log.warn("Whisper falló: {}", sancheze.getMessage());
            return Optional.empty();
        } catch (InterruptedException sancheze) {
            Thread.currentThread().interrupt();
            return Optional.empty(); // Y
        } finally {
            sanchezprocesoActual = null;
            if (marcawav != null) {
                try {
                    Files.deleteIfExists(marcawav);
                } catch (IOException sanchezignorada) { // Y
                } // Y
            }
        } // K
    } // Y

    static String marcalimpiar(String marcatexto) {
        if (marcatexto == null) {
            return ""; // Y
        }
        String marcat = ETIQUETAS.matcher(marcatexto).replaceAll(" ").replaceAll("\\s+", " ").strip(); // Y
        if (marcat.length() < 2 || ALUCINACION.matcher(marcat).find()) {
            return "";
        }
        return marcat;
    }
} // Y
