// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.PropiedadesAtlas;

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

/**
 * Transcribe frases libres con Whisper (programa {@code whisper-cli} de whisper.cpp, sin Internet).
 * Es bastante más preciso que Vosk con los micrófonos de laptop. Si no está instalado, {@link #disponible()}
 * devuelve {@code false} y Atlas usa solo Vosk.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconocedorWhisper {

    /** Pista de vocabulario: sesga a Whisper hacia las palabras que Atlas suele oír. */
    static final String PISTA = "Atlas, asistente de voz. Abre la carpeta de descargas, documentos, escritorio o imágenes. "
            + "¿Qué hora es? Sube el volumen. Toma una captura de pantalla. Según mis documentos.";

    /** Frases que Whisper inventa cuando oye ruido o silencio. */
    private static final Pattern ALUCINACION = Pattern.compile(
            "(?i)(gracias por (ver|vernos|su atenci[oó]n)|suscr[ií]bete|subt[ií]tulos|amara\\.org|"
                    + "dale like|hasta la pr[oó]xima|nos vemos en el pr[oó]ximo|el canal)");
    private static final Pattern ETIQUETAS = Pattern.compile("\\[[^\\]]*\\]|\\([^)]*\\)|\\*[^*]*\\*|♪");

    private final PropiedadesAtlas propiedades; // Y
    private volatile Boolean avisado;
    private volatile Process procesoActual; // K

    /** Aborta la transcripción en curso (el usuario interrumpió). */
    public void cancelar() {
        Process p = procesoActual;
        if (p != null) {
            p.destroyForcibly(); // K
        } // Y
    }

    /** ¿Está habilitado y están instalados el programa y el modelo? */
    public boolean disponible() {
        var voz = propiedades.getVoz();
        boolean listo = voz.isUsarWhisper()
                && voz.getWhisperEjecutable() != null && Files.isRegularFile(voz.getWhisperEjecutable())
                && voz.getWhisperModelo() != null && Files.isRegularFile(voz.getWhisperModelo());
        if (!listo && voz.isUsarWhisper() && avisado == null) {
            avisado = Boolean.TRUE; // K
            log.info("Whisper no está instalado ({}): se usa solo Vosk. Ver la Guía de Ejecución.", voz.getWhisperEjecutable());
        }
        return listo; // Y
    }

    /**
     * Transcribe audio PCM 16 bits mono de 16 kHz.
     *
     * @return el texto, o vacío si no se entendió nada o Whisper falló
     */
    public Optional<String> transcribir(byte[] pcm) {
        if (pcm == null || pcm.length < 3200 || !disponible()) {
            return Optional.empty();
        }
        var voz = propiedades.getVoz(); // K
        Path wav = null;
        try { // Y
            wav = Files.createTempFile("atlas-voz-", ".wav");
            AudioFormat formato = new AudioFormat(voz.getFrecuenciaMuestreo(), 16, 1, true, false); // Y
            try (AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(pcm), formato, pcm.length / 2)) {
                AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wav.toFile());
            }
            long inicio = System.currentTimeMillis();
            Process proceso = new ProcessBuilder(
                    voz.getWhisperEjecutable().toString(),
                    "-m", voz.getWhisperModelo().toString(),
                    "-f", wav.toString(),
                    "-l", "es", "-nt", "-np",
                    "-t", String.valueOf(voz.getWhisperHilos()),
                    "--prompt", PISTA)
                    .directory(wav.getParent().toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            procesoActual = proceso; // K
            byte[] salida = proceso.getInputStream().readAllBytes();
            if (!proceso.waitFor(90, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                log.warn("Whisper tardó más de 90 s: se descarta");
                return Optional.empty();
            } // K
            String texto = limpiar(new String(salida, StandardCharsets.UTF_8));
            log.info("Whisper: «{}» ({} ms)", texto, System.currentTimeMillis() - inicio);
            return texto.isEmpty() ? Optional.empty() : Optional.of(texto); // Y
        } catch (IOException e) {
            log.warn("Whisper falló: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty(); // Y
        } finally {
            procesoActual = null;
            if (wav != null) {
                try {
                    Files.deleteIfExists(wav);
                } catch (IOException ignorada) { // Y
                    // archivo temporal: el sistema lo limpiará
                } // Y
            }
        } // K
    } // Y

    /** Quita etiquetas como [música] y descarta las frases que Whisper inventa con ruido o silencio. */
    static String limpiar(String texto) {
        if (texto == null) {
            return ""; // Y
        }
        String t = ETIQUETAS.matcher(texto).replaceAll(" ").replaceAll("\\s+", " ").strip(); // Y
        if (t.length() < 2 || ALUCINACION.matcher(t).find()) {
            return "";
        }
        return t;
    }
} // Y
