// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import org.junit.jupiter.api.Test;
import pe.upeu.atlas.config.PropiedadesAtlas;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReconocedorWhisperTest {

    private static final Path DIR_WHISPER = Path.of(System.getProperty("atlas.whisper.dir", "D:/Atlas-modelos/whisper"));

    @Test
    void limpiaEtiquetasYDescartaAlucinaciones() {
        assertThat(ReconocedorWhisper.limpiar("  Quiero que me hagas un resumen.  ")).isEqualTo("Quiero que me hagas un resumen.");
        assertThat(ReconocedorWhisper.limpiar("[Música] Hola atlas (risas)")).isEqualTo("Hola atlas");
        assertThat(ReconocedorWhisper.limpiar("Gracias por ver el video")).isEmpty();
        assertThat(ReconocedorWhisper.limpiar("Subtítulos realizados por la comunidad de Amara.org")).isEmpty();
        assertThat(ReconocedorWhisper.limpiar("[ruido]")).isEmpty();
        assertThat(ReconocedorWhisper.limpiar(".")).isEmpty();
        assertThat(ReconocedorWhisper.limpiar(null)).isEmpty();
    }

    @Test
    void noEstaDisponibleSiFaltanLosArchivos() {
        PropiedadesAtlas p = new PropiedadesAtlas();
        p.getVoz().setWhisperEjecutable(Path.of("no/existe/whisper-cli.exe"));
        p.getVoz().setWhisperModelo(Path.of("no/existe/ggml-base.bin"));
        ReconocedorWhisper w = new ReconocedorWhisper(p);

        assertThat(w.disponible()).isFalse();
        assertThat(w.transcribir(new byte[64000])).isEmpty();
    }

    @Test
    void estaDesactivadoSiLaConfiguracionLoPide() {
        PropiedadesAtlas p = configuradaConWhisper();
        assumeTrue(p != null, "Whisper no está instalado en " + DIR_WHISPER);
        p.getVoz().setUsarWhisper(false);

        assertThat(new ReconocedorWhisper(p).disponible()).isFalse();
    }

    /** Integración real: Piper habla y Whisper transcribe. */
    @Test
    void transcribeUnaFraseLibreDePiper() throws Exception {
        PropiedadesAtlas p = configuradaConWhisper();
        assumeTrue(p != null, "Whisper no está instalado en " + DIR_WHISPER);
        Path piper = Path.of(System.getProperty("user.home"), ".atlas", "modelos", "piper", "piper.exe");
        assumeTrue(Files.isRegularFile(piper), "Falta Piper");

        byte[] pcm = sintetizar(piper, "Quiero que me hagas un resumen de las redes de computadoras");
        var texto = new ReconocedorWhisper(p).transcribir(pcm);

        System.out.println("WHISPER -> " + texto.orElse("(nada)"));
        assertThat(texto).isPresent();
        // Whisper base varía un poco entre ejecuciones ("resumen"/"resumo"): se comprueba que el circuito funciona
        assertThat(texto.get().toLowerCase()).contains("resum").contains("redes");
    }

    private static PropiedadesAtlas configuradaConWhisper() {
        Path exe = DIR_WHISPER.resolve("bin/Release/whisper-cli.exe");
        Path modelo = DIR_WHISPER.resolve("ggml-base.bin");
        if (!Files.isRegularFile(exe) || !Files.isRegularFile(modelo)) {
            return null;
        }
        PropiedadesAtlas p = new PropiedadesAtlas();
        p.getVoz().setWhisperEjecutable(exe);
        p.getVoz().setWhisperModelo(modelo);
        return p;
    }

    /** Habla con Piper y devuelve PCM de 16 kHz mono. */
    private static byte[] sintetizar(Path piper, String texto) throws Exception {
        Path voz = piper.getParent().getParent().resolve("voces").resolve("es_ES-davefx-medium.onnx");
        Path wav = Files.createTempFile("atlas-prueba-", ".wav");
        try {
            Process proc = new ProcessBuilder(piper.toString(), "--model", voz.toString(), "--output_file", wav.toString())
                    .directory(piper.getParent().toFile()).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            proc.getOutputStream().write((texto + "\n").getBytes(StandardCharsets.UTF_8));
            proc.getOutputStream().close();
            assertThat(proc.waitFor(60, TimeUnit.SECONDS)).isTrue();
            try (AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile())) {
                double origen = in.getFormat().getSampleRate();
                byte[] b = in.readAllBytes();
                int n = b.length / 2;
                short[] x = new short[n];
                for (int i = 0; i < n; i++) {
                    x[i] = (short) ((b[2 * i + 1] << 8) | (b[2 * i] & 0xFF));
                }
                int m = (int) (n * 16000 / origen);
                ByteArrayOutputStream out = new ByteArrayOutputStream(m * 2);
                for (int i = 0; i < m; i++) {
                    double pos = i * origen / 16000;
                    int a = (int) pos;
                    double f = pos - a;
                    short v = (short) (x[Math.min(a, n - 1)] * (1 - f) + x[Math.min(a + 1, n - 1)] * f);
                    out.write(v & 0xFF);
                    out.write((v >> 8) & 0xFF);
                }
                return out.toByteArray();
            }
        } finally {
            Files.deleteIfExists(wav);
        }
    }
}
