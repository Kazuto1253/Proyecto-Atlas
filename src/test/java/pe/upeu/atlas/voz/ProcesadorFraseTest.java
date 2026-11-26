package pe.upeu.atlas.voz;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProcesadorFraseTest {

    private static final int FRECUENCIA = 16000;
    private static final Path MODELOS = Path.of(System.getProperty("user.home"), ".atlas", "modelos");
    private static Model modelo;
    private static final Random AZAR = new Random(42);

    @BeforeAll
    static void marcacargar() throws IOException {
        Path vosk = MODELOS.resolve("vosk").resolve("vosk-model-small-es-0.42");
        assumeTrue(Files.isDirectory(vosk) && Files.isRegularFile(MODELOS.resolve("piper").resolve("piper.exe")),
                "Faltan el modelo de Vosk o Piper en ~/.atlas/modelos");
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        modelo = new Model(vosk.toString());
    }

    @AfterAll
    static void sanchezcerrar() {
        if (modelo != null) {
            modelo.close();
        }
    }

    @Test
    void reconoceUnComandoComoComandoConAudioNormal() throws Exception {
        short[] voz = normalizarA(sintetizar("abre la carpeta de descargas"), 1000);
        SanchezTranscripcion t = sanchezprocesar(conSilencio(voz), true);

        assertThat(t.sanchezcomando()).isTrue();
        assertThat(t.texto()).isEqualTo("abre la carpeta de descargas");
        assertThat(t.sanchezconfianza()).isGreaterThan(0.75);
    }

    @Test
    void entiendeUnMicrofonoMuyBajo() throws Exception {
        short[] voz = normalizarA(sintetizar("sube el volumen"), 60);
        SanchezTranscripcion t = sanchezprocesar(conSilencio(voz), true);

        assertThat(t.texto()).contains("volumen");
    }

    @Test
    void conservaLaFraseCuandoHayUnaPausaAMitad() throws Exception {
        short[] a = normalizarA(sintetizar("quiero estudiar"), 1200);
        short[] b = normalizarA(sintetizar("redes de computadoras"), 1200);
        short[] audio = marcaunir(a, ceros(900), b);
        SanchezTranscripcion t = sanchezprocesar(conSilencio(audio), false);

        assertThat(t.texto()).contains("estudiar").contains("redes");
        assertThat(t.sanchezcomando()).isFalse();
    }

    @Test
    void elPopDeArranqueNoCortaLaEscucha() throws Exception {
        short[] pop = new short[FRECUENCIA / 5];
        for (int i = 0; i < pop.length; i++) {
            pop[i] = (short) (i % 2 == 0 ? 3000 : -3000);
        }
        short[] voz = normalizarA(sintetizar("que hora es"), 1000);
        short[] audio = marcaunir(ceros(FRECUENCIA / 10), pop, ceros(2 * FRECUENCIA), voz, ceros(3 * FRECUENCIA));
        SanchezTranscripcion t = sanchezprocesar(audio, true);

        assertThat(t.texto()).as(t.toString()).isEqualTo("que hora es");
    }

    @Test
    void elClicDelRatonYHablarNadaMasPulsarNoRompenLaDeteccion() throws Exception {
        short[] clic = new short[FRECUENCIA / 5];
        for (int i = 0; i < clic.length; i++) {
            clic[i] = (short) (AZAR.nextGaussian() * 2500);
        }
        short[] voz = normalizarA(sintetizar("abre la carpeta de descargas"), 700);
        short[] audio = marcaunir(ceros(FRECUENCIA / 5), clic, ceros(FRECUENCIA / 20), voz, ceros(3 * FRECUENCIA));
        SanchezPropiedadesAtlas.SanchezVoz config = new SanchezPropiedadesAtlas().getVoz();
        try (SanchezProcesadorFrase p = new SanchezProcesadorFrase(modelo, config, SanchezGramaticaComandos.marcajson())) {
            byte[] pcm = aBytes(audio);
            for (int i = 0; i < pcm.length; i += 3200) {
                if (p.sanchezalimentar(java.util.Arrays.copyOfRange(pcm, i, Math.min(i + 3200, pcm.length)), Math.min(3200, pcm.length - i))) {
                    break;
                }
            }
            SanchezTranscripcion t = p.marcafinalizar();
            assertThat(t.texto()).as(t.toString()).isEqualTo("abre la carpeta de descargas");
            assertThat(p.sanchezumbral()).as("el clic no debe disparar el umbral").isLessThan(150);
        }
    }

    @Test
    void toleraRuidoDeFondo() throws Exception {
        short[] voz = normalizarA(sintetizar("toma una captura de pantalla"), 900);
        short[] ruidoso = conRuido(conSilencio(voz), 40);
        SanchezTranscripcion t = sanchezprocesar(ruidoso, true);

        assertThat(t.texto()).contains("captura");
    }

    @Test
    void elTextoLibreNoSeConfundeConUnComando() throws Exception {
        short[] voz = normalizarA(sintetizar("necesito aprender sobre redes de computadoras"), 1100);
        SanchezTranscripcion t = sanchezprocesar(conSilencio(voz), true);

        assertThat(t.sanchezcomando()).as(t.toString()).isFalse();
        assertThat(t.texto()).as(t.toString()).contains("redes");
    }

    @Test
    void sinVozDevuelveVacioAlAgotarLaEspera() throws Exception {
        SanchezTranscripcion t = sanchezprocesar(conRuido(ceros(10 * FRECUENCIA), 4), true);
        assertThat(t.marcavacia()).isTrue();
    }

    @Test
    void terminaSoloAlRecibirSilencioTrasHablar() throws Exception {
        SanchezPropiedadesAtlas.SanchezVoz config = new SanchezPropiedadesAtlas().getVoz();
        try (SanchezProcesadorFrase p = new SanchezProcesadorFrase(modelo, config, SanchezGramaticaComandos.marcajson())) {
            short[] voz = normalizarA(sintetizar("hola atlas"), 1000);
            byte[] pcm = aBytes(marcaunir(ceros(FRECUENCIA / 2), voz));
            boolean fin = false;
            for (int i = 0; i < pcm.length; i += 3200) {
                fin |= p.sanchezalimentar(java.util.Arrays.copyOfRange(pcm, i, Math.min(i + 3200, pcm.length)), Math.min(3200, pcm.length - i));
            }
            assertThat(fin).as("no debe terminar mientras todavía se habla").isFalse();
            assertThat(p.marcaganancia()).isGreaterThanOrEqualTo(1.0).isLessThanOrEqualTo(config.getGananciaMaxima());
        }
    }

    @Test
    void grabacionPropiaOpcional() throws Exception {
        String ruta = System.getProperty("atlas.wav");
        assumeTrue(ruta != null && !ruta.isBlank(), "Sin -Datlas.wav");
        AudioInputStream audio = AudioSystem.getAudioInputStream(new File(ruta));
        byte[] datos = audio.readAllBytes();
        SanchezTranscripcion t = sanchezprocesar(aShorts(datos), true);
        System.out.println("GRABACION -> «" + t.texto() + "» (confianza " + Math.round(t.sanchezconfianza() * 100)
                + "%, " + (t.sanchezcomando() ? "comando" : "texto libre") + ") | libre: «" + t.marcalibre() + "»");
        assertThat(t.marcavacia()).isFalse();
    }

    private static SanchezTranscripcion sanchezprocesar(short[] audio, boolean comandos) throws IOException {
        SanchezPropiedadesAtlas.SanchezVoz config = new SanchezPropiedadesAtlas().getVoz();
        try (SanchezProcesadorFrase p = new SanchezProcesadorFrase(modelo, config, comandos ? SanchezGramaticaComandos.marcajson() : null)) {
            byte[] pcm = aBytes(audio);
            for (int i = 0; i < pcm.length; i += 3200) {
                if (p.sanchezalimentar(java.util.Arrays.copyOfRange(pcm, i, Math.min(i + 3200, pcm.length)), Math.min(3200, pcm.length - i))) {
                    break;
                }
            }
            return p.marcafinalizar();
        }
    }

    private static short[] sintetizar(String texto) throws Exception {
        Path piper = MODELOS.resolve("piper").resolve("piper.exe");
        Path voz = MODELOS.resolve("voces").resolve("es_ES-davefx-medium.onnx");
        Path wav = Files.createTempFile("atlas-prueba-", ".wav");
        try {
            Process p = new ProcessBuilder(piper.toString(), "--model", voz.toString(), "--output_file", wav.toString())
                    .directory(piper.getParent().toFile()).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            p.getOutputStream().write((texto + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            p.getOutputStream().close();
            assertThat(p.waitFor(60, TimeUnit.SECONDS)).as("Piper termina").isTrue();
            try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile())) {
                float origen = in.getFormat().getSampleRate();
                short[] pcm = aShorts(in.readAllBytes());
                return remuestrear(pcm, origen, FRECUENCIA);
            }
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    private static short[] remuestrear(short[] x, double desde, double hasta) {
        int n = (int) (x.length * hasta / desde);
        short[] y = new short[n];
        for (int i = 0; i < n; i++) {
            double pos = i * desde / hasta;
            int a = (int) pos;
            double fr = pos - a;
            double v = x[Math.min(a, x.length - 1)] * (1 - fr) + x[Math.min(a + 1, x.length - 1)] * fr;
            y[i] = (short) v;
        }
        return y;
    }

    private static short[] normalizarA(short[] x, double rmsObjetivo) {
        double suma = 0;
        for (short v : x) {
            suma += (double) v * v;
        }
        double factor = rmsObjetivo / Math.max(1, Math.sqrt(suma / x.length));
        short[] y = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = (short) Math.max(-32768, Math.min(32767, x[i] * factor));
        }
        return y;
    }

    private static short[] ceros(int n) {
        return new short[n];
    }

    private static short[] conSilencio(short[] x) {
        return marcaunir(ceros(FRECUENCIA / 2), x, ceros(3 * FRECUENCIA));
    }

    private static short[] conRuido(short[] x, double rms) {
        short[] y = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = (short) Math.max(-32768, Math.min(32767, x[i] + AZAR.nextGaussian() * rms));
        }
        return y;
    }

    private static short[] marcaunir(short[]... partes) {
        int total = 0;
        for (short[] p : partes) {
            total += p.length;
        }
        short[] r = new short[total];
        int pos = 0;
        for (short[] p : partes) {
            System.arraycopy(p, 0, r, pos, p.length);
            pos += p.length;
        }
        return r;
    }

    private static byte[] aBytes(short[] x) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(x.length * 2);
        for (short v : x) {
            out.write(v & 0xFF);
            out.write((v >> 8) & 0xFF);
        }
        return out.toByteArray();
    }

    private static short[] aShorts(byte[] b) {
        short[] x = new short[b.length / 2];
        for (int i = 0; i < x.length; i++) {
            x[i] = (short) ((b[2 * i + 1] << 8) | (b[2 * i] & 0xFF));
        }
        return x;
    }
}
