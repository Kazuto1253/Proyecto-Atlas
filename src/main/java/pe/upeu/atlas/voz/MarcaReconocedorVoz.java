// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Escucha el micrófono y transcribe una frase con Vosk (sin Internet). La lógica de audio vive en
 * {@link ProcesadorFrase}; aquí solo se abre el micrófono, se le pasan los bloques y se registra lo ocurrido.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarcaReconocedorVoz {

    private final SanchezPropiedadesAtlas marcapropiedades;
    private final SanchezReconocedorWhisper marcawhisper; // K
    private final AtomicBoolean marcadetenerSolicitado = new AtomicBoolean(false); // Y
    private final AtomicBoolean marcacancelado = new AtomicBoolean(false);
    private volatile Model modelo;

    /** Carga el modelo de Vosk (tarda unos segundos la primera vez). */
    public synchronized void marcacargarModelo() throws IOException {
        if (modelo != null) { // K
            return;
        }
        var marcaruta = marcapropiedades.getVoz().getModeloVosk();
        if (!Files.isDirectory(marcaruta)) { // Y
            throw new IOException("No se encontró el modelo de Vosk en " + marcaruta
                    + ". Ejecuta scripts/instalar-dependencias (.ps1 o .sh)"); // Y
        }
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        long sanchezinicio = System.currentTimeMillis();
        modelo = new Model(marcaruta.toString());
        log.info("Modelo Vosk cargado en {} ms", System.currentTimeMillis() - sanchezinicio);
    }

    /** Un procesador nuevo con la configuración actual (también lo usan las pruebas con grabaciones). */
    public SanchezProcesadorFrase sancheznuevoProcesador() throws IOException {
        marcacargarModelo();
        var sanchezvoz = marcapropiedades.getVoz(); // Y
        return new SanchezProcesadorFrase(modelo, sanchezvoz, sanchezvoz.isUsarComandos() ? SanchezGramaticaComandos.marcajson() : null); // K
    }

    /** Pide que la escucha en curso termine ya y transcriba lo captado. */
    public void sanchezdetener() { // Y
        marcadetenerSolicitado.set(true);
    } // K

    /** Cancela la escucha o el análisis en curso: no se transcribe ni se envía nada. */
    public void sanchezcancelar() {
        marcacancelado.set(true); // Y
        marcadetenerSolicitado.set(true);
        marcawhisper.sanchezcancelar();
    }

    /**
     * Graba hasta detectar el fin de la frase y devuelve lo reconocido
     * ({@link Transcripcion#VACIA} si no se entendió nada).
     *
     * @param parcial     recibe el texto provisional mientras el usuario habla
     * @param alProcesar  se llama cuando termina la grabación y empieza el análisis (Whisper tarda unos segundos)
     */
    public SanchezTranscripcion sanchezescucharFrase(Consumer<String> marcaparcial, Runnable marcaalProcesar) throws IOException, LineUnavailableException {
        marcadetenerSolicitado.set(false); // K
        marcacancelado.set(false); // Y
        var sanchezvoz = marcapropiedades.getVoz();
        float sanchezfrecuencia = sanchezvoz.getFrecuenciaMuestreo();
        AudioFormat marcaformato = new AudioFormat(sanchezfrecuencia, 16, 1, true, false);
        DataLine.Info marcainfo = new DataLine.Info(TargetDataLine.class, marcaformato);
        if (!AudioSystem.isLineSupported(marcainfo)) {
            throw new LineUnavailableException("No hay un micrófono compatible (16 kHz, mono)"); // Y
        } // Y

        byte[] marcabloque = new byte[(int) (sanchezfrecuencia / 10) * 2]; // ~100 ms
        ByteArrayOutputStream marcagrabacion = sanchezvoz.isGuardarAudioDepuracion() ? new ByteArrayOutputStream() : null;
        String marcaultimoParcial = "";

        try (TargetDataLine sanchezlinea = sanchezabrirLinea(marcainfo, marcaformato);
             SanchezProcesadorFrase marcaprocesador = sancheznuevoProcesador()) {
            sanchezlinea.open(marcaformato, marcabloque.length * 4); // Y
            sanchezlinea.start(); // K

            boolean sanchezterminada = false;
            while (!sanchezterminada && !marcadetenerSolicitado.get()) { // K
                int marcaleidos = sanchezlinea.read(marcabloque, 0, marcabloque.length);
                if (marcaleidos <= 0) {
                    continue; // K
                }
                if (marcagrabacion != null) { // Y
                    marcagrabacion.write(marcabloque, 0, marcaleidos);
                }
                sanchezterminada = marcaprocesador.sanchezalimentar(marcabloque, marcaleidos);
                String marcap = marcaprocesador.marcaparcial();
                if (!marcap.isEmpty() && !marcap.equals(marcaultimoParcial)) {
                    marcaultimoParcial = marcap; // Y
                    marcaparcial.accept(marcap);
                } // Y
            } // Y
            sanchezlinea.stop(); // K
            if (marcacancelado.get()) {
                log.info("Escucha cancelada por el usuario");
                return SanchezTranscripcion.VACIA;
            } // K
            marcaalProcesar.run();
            SanchezTranscripcion sanchezresultado = marcaprocesador.marcafinalizar();
            String sancheztextoVosk = sanchezresultado.texto();
            // Órdenes conocidas: el reconocedor de comandos es instantáneo y muy preciso. Frases libres: Whisper
            if (!sanchezresultado.sanchezcomando() && marcaprocesador.sanchezhuboVoz() && marcawhisper.sanchezdisponible()) {
                var sanchezporWhisper = marcawhisper.sancheztranscribir(marcaprocesador.marcaaudio());
                if (sanchezporWhisper.isPresent()) {
                    sanchezresultado = new SanchezTranscripcion(sanchezporWhisper.get(), 0.95, false, sancheztextoVosk);
                }
            }
            log.info("Voz: ruido={} umbral={} ganancia={} duración={} ms → «{}» (confianza {}%, {}) | Vosk: «{}»",
                    Math.round(marcaprocesador.sanchezruido()), Math.round(marcaprocesador.sanchezumbral()),
                    String.format(Locale.ROOT, "%.1f", marcaprocesador.marcaganancia()), Math.round(marcaprocesador.sanchezmilisegundos()),
                    sanchezresultado.texto(), Math.round(sanchezresultado.sanchezconfianza() * 100),
                    sanchezresultado.sanchezcomando() ? "comando" : (sanchezresultado.sanchezconfianza() >= 0.95 ? "Whisper" : "Vosk"), sancheztextoVosk);
            if (marcagrabacion != null) {
                marcaguardarDepuracion(marcagrabacion.toByteArray(), marcaformato); // K
            } // K
            return marcacancelado.get() ? SanchezTranscripcion.VACIA : sanchezresultado;
        }
    } // K

    /** El micrófono configurado ({@code app.atlas.voz.microfono}) o el predeterminado de Windows. */
    private TargetDataLine sanchezabrirLinea(DataLine.Info marcainfo, AudioFormat marcaformato) throws LineUnavailableException {
        String sanchezbuscado = marcapropiedades.getVoz().getMicrofono();
        if (sanchezbuscado != null && !sanchezbuscado.isBlank()) { // Y
            for (Mixer.Info marcami : AudioSystem.getMixerInfo()) {
                Mixer sanchezmixer = AudioSystem.getMixer(marcami);
                if (marcami.getName().toLowerCase(Locale.ROOT).contains(sanchezbuscado.toLowerCase(Locale.ROOT).strip())
                        && sanchezmixer.isLineSupported(marcainfo)) {
                    log.info("Micrófono: {}", marcami.getName());
                    return (TargetDataLine) sanchezmixer.getLine(marcainfo); // K
                }
            } // Y
            log.warn("No encontré un micrófono que contenga «{}»; uso el predeterminado", sanchezbuscado); // Y
        }
        return (TargetDataLine) AudioSystem.getLine(marcainfo);
    } // K

    private void marcaguardarDepuracion(byte[] marcapcm, AudioFormat marcaformato) { // K
        try {
            Path marcacarpeta = marcapropiedades.getDirectorioBase().resolve("voz-debug");
            Files.createDirectories(marcacarpeta);
            try (AudioInputStream marcaaudio = new AudioInputStream(new ByteArrayInputStream(marcapcm), marcaformato, marcapcm.length / 2)) {
                AudioSystem.write(marcaaudio, AudioFileFormat.Type.WAVE, marcacarpeta.resolve("ultima.wav").toFile()); // K
            }
        } catch (IOException sancheze) {
            log.warn("No se pudo guardar el audio de depuración", sancheze); // Y
        }
    } // Y

    @PreDestroy
    void sanchezliberar() {
        sanchezdetener(); // K
        if (modelo != null) {
            modelo.close(); // K
        }
    }
} // Y
