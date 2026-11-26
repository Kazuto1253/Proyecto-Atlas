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

@Slf4j
@Component
@RequiredArgsConstructor
public class MarcaReconocedorVoz {

    private final SanchezPropiedadesAtlas marcapropiedades;
    private final SanchezReconocedorWhisper marcawhisper;
    private final AtomicBoolean marcadetenerSolicitado = new AtomicBoolean(false);
    private final AtomicBoolean marcacancelado = new AtomicBoolean(false);
    private volatile Model modelo;

    public synchronized void marcacargarModelo() throws IOException {
        if (modelo != null) {
            return;
        }
        var marcaruta = marcapropiedades.getVoz().getModeloVosk();
        if (!Files.isDirectory(marcaruta)) {
            throw new IOException("No se encontró el modelo de Vosk en " + marcaruta
                    + ". Ejecuta scripts/instalar-dependencias (.ps1 o .sh)");
        }
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        long sanchezinicio = System.currentTimeMillis();
        modelo = new Model(marcaruta.toString());
        log.info("Modelo Vosk cargado en {} ms", System.currentTimeMillis() - sanchezinicio);
    }

    public SanchezProcesadorFrase sancheznuevoProcesador() throws IOException {
        marcacargarModelo();
        var sanchezvoz = marcapropiedades.getVoz();
        return new SanchezProcesadorFrase(modelo, sanchezvoz, sanchezvoz.isUsarComandos() ? SanchezGramaticaComandos.marcajson() : null);
    }

    public void sanchezdetener() {
        marcadetenerSolicitado.set(true);
    }

    public void sanchezcancelar() {
        marcacancelado.set(true);
        marcadetenerSolicitado.set(true);
        marcawhisper.sanchezcancelar();
    }

    public SanchezTranscripcion sanchezescucharFrase(Consumer<String> marcaparcial, Runnable marcaalProcesar) throws IOException, LineUnavailableException {
        marcadetenerSolicitado.set(false);
        marcacancelado.set(false);
        var sanchezvoz = marcapropiedades.getVoz();
        float sanchezfrecuencia = sanchezvoz.getFrecuenciaMuestreo();
        AudioFormat marcaformato = new AudioFormat(sanchezfrecuencia, 16, 1, true, false);
        DataLine.Info marcainfo = new DataLine.Info(TargetDataLine.class, marcaformato);
        if (!AudioSystem.isLineSupported(marcainfo)) {
            throw new LineUnavailableException("No hay un micrófono compatible (16 kHz, mono)");
        }

        byte[] marcabloque = new byte[(int) (sanchezfrecuencia / 10) * 2];
        ByteArrayOutputStream marcagrabacion = sanchezvoz.isGuardarAudioDepuracion() ? new ByteArrayOutputStream() : null;
        String marcaultimoParcial = "";

        try (TargetDataLine sanchezlinea = sanchezabrirLinea(marcainfo, marcaformato);
             SanchezProcesadorFrase marcaprocesador = sancheznuevoProcesador()) {
            sanchezlinea.open(marcaformato, marcabloque.length * 4);
            sanchezlinea.start();

            boolean sanchezterminada = false;
            while (!sanchezterminada && !marcadetenerSolicitado.get()) {
                int marcaleidos = sanchezlinea.read(marcabloque, 0, marcabloque.length);
                if (marcaleidos <= 0) {
                    continue;
                }
                if (marcagrabacion != null) {
                    marcagrabacion.write(marcabloque, 0, marcaleidos);
                }
                sanchezterminada = marcaprocesador.sanchezalimentar(marcabloque, marcaleidos);
                String marcap = marcaprocesador.marcaparcial();
                if (!marcap.isEmpty() && !marcap.equals(marcaultimoParcial)) {
                    marcaultimoParcial = marcap;
                    marcaparcial.accept(marcap);
                }
            }
            sanchezlinea.stop();
            if (marcacancelado.get()) {
                log.info("Escucha cancelada por el usuario");
                return SanchezTranscripcion.VACIA;
            }
            marcaalProcesar.run();
            SanchezTranscripcion sanchezresultado = marcaprocesador.marcafinalizar();
            String sancheztextoVosk = sanchezresultado.texto();
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
                marcaguardarDepuracion(marcagrabacion.toByteArray(), marcaformato);
            }
            return marcacancelado.get() ? SanchezTranscripcion.VACIA : sanchezresultado;
        }
    }

    private TargetDataLine sanchezabrirLinea(DataLine.Info marcainfo, AudioFormat marcaformato) throws LineUnavailableException {
        String sanchezbuscado = marcapropiedades.getVoz().getMicrofono();
        if (sanchezbuscado != null && !sanchezbuscado.isBlank()) {
            for (Mixer.Info marcami : AudioSystem.getMixerInfo()) {
                Mixer sanchezmixer = AudioSystem.getMixer(marcami);
                if (marcami.getName().toLowerCase(Locale.ROOT).contains(sanchezbuscado.toLowerCase(Locale.ROOT).strip())
                        && sanchezmixer.isLineSupported(marcainfo)) {
                    log.info("Micrófono: {}", marcami.getName());
                    return (TargetDataLine) sanchezmixer.getLine(marcainfo);
                }
            }
            log.warn("No encontré un micrófono que contenga «{}»; uso el predeterminado", sanchezbuscado);
        }
        return (TargetDataLine) AudioSystem.getLine(marcainfo);
    }

    private void marcaguardarDepuracion(byte[] marcapcm, AudioFormat marcaformato) {
        try {
            Path marcacarpeta = marcapropiedades.getDirectorioBase().resolve("voz-debug");
            Files.createDirectories(marcacarpeta);
            try (AudioInputStream marcaaudio = new AudioInputStream(new ByteArrayInputStream(marcapcm), marcaformato, marcapcm.length / 2)) {
                AudioSystem.write(marcaaudio, AudioFileFormat.Type.WAVE, marcacarpeta.resolve("ultima.wav").toFile());
            }
        } catch (IOException sancheze) {
            log.warn("No se pudo guardar el audio de depuración", sancheze);
        }
    }

    @PreDestroy
    void sanchezliberar() {
        sanchezdetener();
        if (modelo != null) {
            modelo.close();
        }
    }
}
