package pe.upeu.atlas.voz;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GramaticaYNormalizadorTest {

    @Test
    void laGramaticaUsaSoloLetrasSinTildesNiMayusculas() {
        List<String> frases = SanchezGramaticaComandos.marcafrases();
        assertThat(frases).hasSizeGreaterThan(80);
        assertThat(new HashSet<>(frases)).hasSameSizeAs(frases);
        assertThat(frases).allMatch(f -> f.matches("[a-z ]+"), "solo a-z y espacios");
        assertThat(frases).contains("abre la carpeta de descargas", "que hora es", "sube el volumen",
                "toma una captura de pantalla", "bloquea la pantalla", "abre la calculadora");
    }

    @Test
    void generaUnJsonValidoQueTerminaEnUnk() {
        String json = SanchezGramaticaComandos.marcajson();
        assertThat(json).startsWith("[\"").endsWith(",\"[unk]\"]");
        assertThat(json).contains("\"abre la carpeta de descargas\"").doesNotContain("\"\"");
    }

    @Test
    void conviertePreguntasHabladasEnTextoNatural() {
        assertThat(SanchezNormalizadorTranscripcion.sanchezformatear("que hora es")).isEqualTo("¿Qué hora es?");
        assertThat(SanchezNormalizadorTranscripcion.sanchezformatear("como esta mi equipo")).isEqualTo("¿Cómo está mi equipo?");
        assertThat(SanchezNormalizadorTranscripcion.sanchezformatear("cuanto es quince por doce mas tres"))
                .isEqualTo("¿Cuánto es quince por doce mas tres?");
        assertThat(SanchezNormalizadorTranscripcion.sanchezformatear("por que el cielo es azul")).isEqualTo("¿Por qué el cielo es azul?");
        assertThat(SanchezNormalizadorTranscripcion.sanchezformatear("segun mis documentos que es una red lan"))
                .isEqualTo("Segun mis documentos que es una red lan");
    }

    @Test
    void noInventaNadaConTextoVacioOAfirmaciones() {
        assertThat(SanchezNormalizadorTranscripcion.sanchezformatear(null)).isEmpty();
        assertThat(SanchezNormalizadorTranscripcion.sanchezformatear("   ")).isEmpty();
        assertThat(SanchezNormalizadorTranscripcion.sanchezformatear("abre la carpeta de descargas")).isEqualTo("Abre la carpeta de descargas");
        assertThat(SanchezNormalizadorTranscripcion.sanchezformatear("  hola   atlas ")).isEqualTo("Hola atlas");
    }
}
