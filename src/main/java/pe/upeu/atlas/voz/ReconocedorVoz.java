// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import pe.upeu.atlas.config.PropiedadesAtlas;

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
public class ReconocedorVoz {

    private final PropiedadesAtlas propiedades;
    private final ReconocedorWhisper whisper; // K
    private final AtomicBoolean detenerSolicitado = new AtomicBoolean(false); // Y
    private final AtomicBoolean cancelado = new AtomicBoolean(false);
    private volatile Model modelo;

    /** Carga el modelo de Vosk (tarda unos segundos la primera vez). */
    public synchronized void cargarModelo() throws IOException {
        if (modelo != null) { // K
            return;
        }
        var ruta = propiedades.getVoz().getModeloVosk();
        if (!Files.isDirectory(ruta)) { // Y
            throw new IOException("No se encontró el modelo de Vosk en " + ruta
                    + ". Ejecuta scripts/instalar-dependencias (.ps1 o .sh)"); // Y
        }
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        long inicio = System.currentTimeMillis();
        modelo = new Model(ruta.toString());
        log.info("Modelo Vosk cargado en {} ms", System.currentTimeMillis() - inicio);
    }

    /** Un procesador nuevo con la configuración actual (también lo usan las pruebas con grabaciones). */
    public ProcesadorFrase nuevoProcesador() throws IOException {
        cargarModelo();
        var voz = propiedades.getVoz(); // Y
        return new ProcesadorFrase(modelo, voz, voz.isUsarComandos() ? GramaticaComandos.json() : null); // K
    }

    /** Pide que la escucha en curso termine ya y transcriba lo captado. */
    public void detener() { // Y
        detenerSolicitado.set(true);
    } // K

    /** Cancela la escucha o el análisis en curso: no se transcribe ni se envía nada. */
    public void cancelar() {
        cancelado.set(true); // Y
        detenerSolicitado.set(true);
        whisper.cancelar();
    }

    /**
     * Graba hasta detectar el fin de la frase y devuelve lo reconocido
     * ({@link Transcripcion#VACIA} si no se entendió nada).
     *
     * @param parcial     recibe el texto provisional mientras el usuario habla
     * @param alProcesar  se llama cuando termina la grabación y empieza el análisis (Whisper tarda unos segundos)
     */
    public Transcripcion escucharFrase(Consumer<String> parcial, Runnable alProcesar) throws IOException, LineUnavailableException {
        detenerSolicitado.set(false); // K
        cancelado.set(false); // Y
        var voz = propiedades.getVoz();
        float frecuencia = voz.getFrecuenciaMuestreo();
        AudioFormat formato = new AudioFormat(frecuencia, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("No hay un micrófono compatible (16 kHz, mono)"); // Y
        } // Y

        byte[] bloque = new byte[(int) (frecuencia / 10) * 2]; // ~100 ms
        ByteArrayOutputStream grabacion = voz.isGuardarAudioDepuracion() ? new ByteArrayOutputStream() : null;
        String ultimoParcial = "";

        try (TargetDataLine linea = abrirLinea(info, formato);
             ProcesadorFrase procesador = nuevoProcesador()) {
            linea.open(formato, bloque.length * 4); // Y
            linea.start(); // K

            boolean terminada = false;
            while (!terminada && !detenerSolicitado.get()) { // K
                int leidos = linea.read(bloque, 0, bloque.length);
                if (leidos <= 0) {
                    continue; // K
                }
                if (grabacion != null) { // Y
                    grabacion.write(bloque, 0, leidos);
                }
                terminada = procesador.alimentar(bloque, leidos);
                String p = procesador.parcial();
                if (!p.isEmpty() && !p.equals(ultimoParcial)) {
                    ultimoParcial = p; // Y
                    parcial.accept(p);
                } // Y
            } // Y
            linea.stop(); // K
            if (cancelado.get()) {
                log.info("Escucha cancelada por el usuario");
                return Transcripcion.VACIA;
            } // K
            alProcesar.run();
            Transcripcion resultado = procesador.finalizar();
            String textoVosk = resultado.texto();
            // Órdenes conocidas: el reconocedor de comandos es instantáneo y muy preciso. Frases libres: Whisper
            if (!resultado.comando() && procesador.huboVoz() && whisper.disponible()) {
                var porWhisper = whisper.transcribir(procesador.audio());
                if (porWhisper.isPresent()) {
                    resultado = new Transcripcion(porWhisper.get(), 0.95, false, textoVosk);
                }
            }
            log.info("Voz: ruido={} umbral={} ganancia={} duración={} ms → «{}» (confianza {}%, {}) | Vosk: «{}»",
                    Math.round(procesador.ruido()), Math.round(procesador.umbral()),
                    String.format(Locale.ROOT, "%.1f", procesador.ganancia()), Math.round(procesador.milisegundos()),
                    resultado.texto(), Math.round(resultado.confianza() * 100),
                    resultado.comando() ? "comando" : (resultado.confianza() >= 0.95 ? "Whisper" : "Vosk"), textoVosk);
            if (grabacion != null) {
                guardarDepuracion(grabacion.toByteArray(), formato); // K
            } // K
            return cancelado.get() ? Transcripcion.VACIA : resultado;
        }
    } // K

    /** El micrófono configurado ({@code app.atlas.voz.microfono}) o el predeterminado de Windows. */
    private TargetDataLine abrirLinea(DataLine.Info info, AudioFormat formato) throws LineUnavailableException {
        String buscado = propiedades.getVoz().getMicrofono();
        if (buscado != null && !buscado.isBlank()) { // Y
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                Mixer mixer = AudioSystem.getMixer(mi);
                if (mi.getName().toLowerCase(Locale.ROOT).contains(buscado.toLowerCase(Locale.ROOT).strip())
                        && mixer.isLineSupported(info)) {
                    log.info("Micrófono: {}", mi.getName());
                    return (TargetDataLine) mixer.getLine(info); // K
                }
            } // Y
            log.warn("No encontré un micrófono que contenga «{}»; uso el predeterminado", buscado); // Y
        }
        return (TargetDataLine) AudioSystem.getLine(info);
    } // K

    private void guardarDepuracion(byte[] pcm, AudioFormat formato) { // K
        try {
            Path carpeta = propiedades.getDirectorioBase().resolve("voz-debug");
            Files.createDirectories(carpeta);
            try (AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(pcm), formato, pcm.length / 2)) {
                AudioSystem.write(audio, AudioFileFormat.Type.WAVE, carpeta.resolve("ultima.wav").toFile()); // K
            }
        } catch (IOException e) {
            log.warn("No se pudo guardar el audio de depuración", e); // Y
        }
    } // Y

    @PreDestroy
    void liberar() {
        detener(); // K
        if (modelo != null) {
            modelo.close(); // K
        }
    }
} // Y
