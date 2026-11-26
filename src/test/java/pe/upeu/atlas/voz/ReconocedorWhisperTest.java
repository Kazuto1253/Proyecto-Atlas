package pe.upeu.atlas.voz;

import org.junit.jupiter.api.Test;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;

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
        assertThat(SanchezReconocedorWhisper.marcalimpiar("  Quiero que me hagas un resumen.  ")).isEqualTo("Quiero que me hagas un resumen.");
        assertThat(SanchezReconocedorWhisper.marcalimpiar("[Música] Hola atlas (risas)")).isEqualTo("Hola atlas");
        assertThat(SanchezReconocedorWhisper.marcalimpiar("Gracias por ver el video")).isEmpty();
        assertThat(SanchezReconocedorWhisper.marcalimpiar("Subtítulos realizados por la comunidad de Amara.org")).isEmpty();
        assertThat(SanchezReconocedorWhisper.marcalimpiar("[ruido]")).isEmpty();
        assertThat(SanchezReconocedorWhisper.marcalimpiar(".")).isEmpty();
        assertThat(SanchezReconocedorWhisper.marcalimpiar(null)).isEmpty();
    }

    @Test
    void noEstaDisponibleSiFaltanLosArchivos() {
        SanchezPropiedadesAtlas p = new SanchezPropiedadesAtlas();
        p.getVoz().setWhisperEjecutable(Path.of("no/existe/whisper-cli.exe"));
        p.getVoz().setWhisperModelo(Path.of("no/existe/ggml-base.bin"));
        SanchezReconocedorWhisper w = new SanchezReconocedorWhisper(p);

        assertThat(w.sanchezdisponible()).isFalse();
        assertThat(w.sancheztranscribir(new byte[64000])).isEmpty();
    }

    @Test
    void estaDesactivadoSiLaConfiguracionLoPide() {
        SanchezPropiedadesAtlas p = configuradaConWhisper();
        assumeTrue(p != null, "Whisper no está instalado en " + DIR_WHISPER);
        p.getVoz().setUsarWhisper(false);

        assertThat(new SanchezReconocedorWhisper(p).sanchezdisponible()).isFalse();
    }

    @Test
    void transcribeUnaFraseLibreDePiper() throws Exception {
        SanchezPropiedadesAtlas p = configuradaConWhisper();
        assumeTrue(p != null, "Whisper no está instalado en " + DIR_WHISPER);
        Path piper = Path.of(System.getProperty("user.home"), ".atlas", "modelos", "piper", "piper.exe");
        assumeTrue(Files.isRegularFile(piper), "Falta Piper");

        byte[] pcm = sintetizar(piper, "Quiero que me hagas un resumen de las redes de computadoras");
        var texto = new SanchezReconocedorWhisper(p).sancheztranscribir(pcm);

        System.out.println("WHISPER -> " + texto.orElse("(nada)"));
        assertThat(texto).isPresent();
        assertThat(texto.get().toLowerCase()).contains("resum").contains("redes");
    }

    private static SanchezPropiedadesAtlas configuradaConWhisper() {
        Path exe = DIR_WHISPER.resolve("bin/Release/whisper-cli.exe");
        Path modelo = DIR_WHISPER.resolve("ggml-base.bin");
        if (!Files.isRegularFile(exe) || !Files.isRegularFile(modelo)) {
            return null;
        }
        SanchezPropiedadesAtlas p = new SanchezPropiedadesAtlas();
        p.getVoz().setWhisperEjecutable(exe);
        p.getVoz().setWhisperModelo(modelo);
        return p;
    }

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
