// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import pe.upeu.atlas.config.PropiedadesAtlas;

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

/**
 * Prueba el procesador de voz de punta a punta con audio sintetizado por Piper (la misma voz que usa Atlas),
 * degradado a propósito: volumen bajo, ruido, pausa a mitad de frase y "pop" de arranque del micrófono.
 * Se omite si no están instalados el modelo de Vosk y Piper (~/.atlas/modelos).
 */
class ProcesadorFraseTest {

    private static final int FRECUENCIA = 16000;
    private static final Path MODELOS = Path.of(System.getProperty("user.home"), ".atlas", "modelos");
    private static Model modelo;
    private static final Random AZAR = new Random(42);

    @BeforeAll
    static void cargar() throws IOException {
        Path vosk = MODELOS.resolve("vosk").resolve("vosk-model-small-es-0.42");
        assumeTrue(Files.isDirectory(vosk) && Files.isRegularFile(MODELOS.resolve("piper").resolve("piper.exe")),
                "Faltan el modelo de Vosk o Piper en ~/.atlas/modelos");
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        modelo = new Model(vosk.toString());
    }

    @AfterAll
    static void cerrar() {
        if (modelo != null) {
            modelo.close();
        }
    }

    // ===== Casos =====

    @Test
    void reconoceUnComandoComoComandoConAudioNormal() throws Exception {
        short[] voz = normalizarA(sintetizar("abre la carpeta de descargas"), 1000);
        Transcripcion t = procesar(conSilencio(voz), true);

        assertThat(t.comando()).isTrue();
        assertThat(t.texto()).isEqualTo("abre la carpeta de descargas");
        assertThat(t.confianza()).isGreaterThan(0.75);
    }

    @Test
    void entiendeUnMicrofonoMuyBajo() throws Exception {
        // Nivel ~60 de 32768: parecido a un micrófono casi mudo
        short[] voz = normalizarA(sintetizar("sube el volumen"), 60);
        Transcripcion t = procesar(conSilencio(voz), true);

        assertThat(t.texto()).contains("volumen");
    }

    @Test
    void conservaLaFraseCuandoHayUnaPausaAMitad() throws Exception {
        // Antes de la corrección, Vosk cerraba el primer trozo en la pausa y ese texto se perdía
        short[] a = normalizarA(sintetizar("quiero estudiar"), 1200);
        short[] b = normalizarA(sintetizar("redes de computadoras"), 1200);
        short[] audio = unir(a, ceros(900), b);
        Transcripcion t = procesar(conSilencio(audio), false);

        assertThat(t.texto()).contains("estudiar").contains("redes");
        assertThat(t.comando()).isFalse();
    }

    @Test
    void elPopDeArranqueNoCortaLaEscucha() throws Exception {
        // El micrófono real emite un golpe en los primeros 300 ms y el usuario tarda 2 s en hablar
        short[] pop = new short[FRECUENCIA / 5];
        for (int i = 0; i < pop.length; i++) {
            pop[i] = (short) (i % 2 == 0 ? 3000 : -3000);
        }
        short[] voz = normalizarA(sintetizar("que hora es"), 1000);
        short[] audio = unir(ceros(FRECUENCIA / 10), pop, ceros(2 * FRECUENCIA), voz, ceros(3 * FRECUENCIA));
        Transcripcion t = procesar(audio, true);

        assertThat(t.texto()).as(t.toString()).isEqualTo("que hora es");
    }

    @Test
    void elClicDelRatonYHablarNadaMasPulsarNoRompenLaDeteccion() throws Exception {
        // Caso real: el micrófono capta el clic (~3000) entre 200 y 400 ms y el usuario ya está hablando a los 450 ms
        short[] clic = new short[FRECUENCIA / 5];
        for (int i = 0; i < clic.length; i++) {
            clic[i] = (short) (AZAR.nextGaussian() * 2500);
        }
        short[] voz = normalizarA(sintetizar("abre la carpeta de descargas"), 700);
        short[] audio = unir(ceros(FRECUENCIA / 5), clic, ceros(FRECUENCIA / 20), voz, ceros(3 * FRECUENCIA));
        PropiedadesAtlas.Voz config = new PropiedadesAtlas().getVoz();
        try (ProcesadorFrase p = new ProcesadorFrase(modelo, config, GramaticaComandos.json())) {
            byte[] pcm = aBytes(audio);
            for (int i = 0; i < pcm.length; i += 3200) {
                if (p.alimentar(java.util.Arrays.copyOfRange(pcm, i, Math.min(i + 3200, pcm.length)), Math.min(3200, pcm.length - i))) {
                    break;
                }
            }
            Transcripcion t = p.finalizar();
            assertThat(t.texto()).as(t.toString()).isEqualTo("abre la carpeta de descargas");
            assertThat(p.umbral()).as("el clic no debe disparar el umbral").isLessThan(150);
        }
    }

    @Test
    void toleraRuidoDeFondo() throws Exception {
        short[] voz = normalizarA(sintetizar("toma una captura de pantalla"), 900);
        short[] ruidoso = conRuido(conSilencio(voz), 40);
        Transcripcion t = procesar(ruidoso, true);

        assertThat(t.texto()).contains("captura");
    }

    @Test
    void elTextoLibreNoSeConfundeConUnComando() throws Exception {
        short[] voz = normalizarA(sintetizar("necesito aprender sobre redes de computadoras"), 1100);
        Transcripcion t = procesar(conSilencio(voz), true);

        assertThat(t.comando()).as(t.toString()).isFalse();
        assertThat(t.texto()).as(t.toString()).contains("redes");
    }

    @Test
    void sinVozDevuelveVacioAlAgotarLaEspera() throws Exception {
        Transcripcion t = procesar(conRuido(ceros(10 * FRECUENCIA), 4), true);
        assertThat(t.vacia()).isTrue();
    }

    @Test
    void terminaSoloAlRecibirSilencioTrasHablar() throws Exception {
        PropiedadesAtlas.Voz config = new PropiedadesAtlas().getVoz();
        try (ProcesadorFrase p = new ProcesadorFrase(modelo, config, GramaticaComandos.json())) {
            short[] voz = normalizarA(sintetizar("hola atlas"), 1000);
            byte[] pcm = aBytes(unir(ceros(FRECUENCIA / 2), voz));
            boolean fin = false;
            for (int i = 0; i < pcm.length; i += 3200) {
                fin |= p.alimentar(java.util.Arrays.copyOfRange(pcm, i, Math.min(i + 3200, pcm.length)), Math.min(3200, pcm.length - i));
            }
            assertThat(fin).as("no debe terminar mientras todavía se habla").isFalse();
            assertThat(p.ganancia()).isGreaterThanOrEqualTo(1.0).isLessThanOrEqualTo(config.getGananciaMaxima());
        }
    }

    /** Opcional: prueba con una grabación tuya. {@code -Datlas.wav=C:\ruta\mi-voz.wav} */
    @Test
    void grabacionPropiaOpcional() throws Exception {
        String ruta = System.getProperty("atlas.wav");
        assumeTrue(ruta != null && !ruta.isBlank(), "Sin -Datlas.wav");
        AudioInputStream audio = AudioSystem.getAudioInputStream(new File(ruta));
        byte[] datos = audio.readAllBytes();
        Transcripcion t = procesar(aShorts(datos), true);
        System.out.println("GRABACION -> «" + t.texto() + "» (confianza " + Math.round(t.confianza() * 100)
                + "%, " + (t.comando() ? "comando" : "texto libre") + ") | libre: «" + t.libre() + "»");
        assertThat(t.vacia()).isFalse();
    }

    // ===== Utilidades =====

    private static Transcripcion procesar(short[] audio, boolean comandos) throws IOException {
        PropiedadesAtlas.Voz config = new PropiedadesAtlas().getVoz();
        try (ProcesadorFrase p = new ProcesadorFrase(modelo, config, comandos ? GramaticaComandos.json() : null)) {
            byte[] pcm = aBytes(audio);
            for (int i = 0; i < pcm.length; i += 3200) {
                if (p.alimentar(java.util.Arrays.copyOfRange(pcm, i, Math.min(i + 3200, pcm.length)), Math.min(3200, pcm.length - i))) {
                    break;
                }
            }
            return p.finalizar();
        }
    }

    /** Habla con Piper y devuelve PCM de 16 kHz. */
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

    /** Silencio al final para que el procesador pueda cerrar la frase. */
    private static short[] conSilencio(short[] x) {
        return unir(ceros(FRECUENCIA / 2), x, ceros(3 * FRECUENCIA));
    }

    private static short[] conRuido(short[] x, double rms) {
        short[] y = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = (short) Math.max(-32768, Math.min(32767, x[i] + AZAR.nextGaussian() * rms));
        }
        return y;
    }

    private static short[] unir(short[]... partes) {
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
