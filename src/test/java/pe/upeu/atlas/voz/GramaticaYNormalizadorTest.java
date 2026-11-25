// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GramaticaYNormalizadorTest {

    @Test
    void laGramaticaUsaSoloLetrasSinTildesNiMayusculas() {
        List<String> frases = GramaticaComandos.frases();
        assertThat(frases).hasSizeGreaterThan(80);
        assertThat(new HashSet<>(frases)).hasSameSizeAs(frases);
        // Vosk es no usa tildes: una palabra con tilde no está en su vocabulario y se ignoraría
        assertThat(frases).allMatch(f -> f.matches("[a-z ]+"), "solo a-z y espacios");
        assertThat(frases).contains("abre la carpeta de descargas", "que hora es", "sube el volumen",
                "toma una captura de pantalla", "bloquea la pantalla", "abre la calculadora");
    }

    @Test
    void generaUnJsonValidoQueTerminaEnUnk() {
        String json = GramaticaComandos.json();
        assertThat(json).startsWith("[\"").endsWith(",\"[unk]\"]");
        assertThat(json).contains("\"abre la carpeta de descargas\"").doesNotContain("\"\"");
    }

    @Test
    void conviertePreguntasHabladasEnTextoNatural() {
        assertThat(NormalizadorTranscripcion.formatear("que hora es")).isEqualTo("¿Qué hora es?");
        assertThat(NormalizadorTranscripcion.formatear("como esta mi equipo")).isEqualTo("¿Cómo está mi equipo?");
        assertThat(NormalizadorTranscripcion.formatear("cuanto es quince por doce mas tres"))
                .isEqualTo("¿Cuánto es quince por doce mas tres?");
        assertThat(NormalizadorTranscripcion.formatear("por que el cielo es azul")).isEqualTo("¿Por qué el cielo es azul?");
        assertThat(NormalizadorTranscripcion.formatear("segun mis documentos que es una red lan"))
                .isEqualTo("Segun mis documentos que es una red lan");
    }

    @Test
    void noInventaNadaConTextoVacioOAfirmaciones() {
        assertThat(NormalizadorTranscripcion.formatear(null)).isEmpty();
        assertThat(NormalizadorTranscripcion.formatear("   ")).isEmpty();
        assertThat(NormalizadorTranscripcion.formatear("abre la carpeta de descargas")).isEqualTo("Abre la carpeta de descargas");
        assertThat(NormalizadorTranscripcion.formatear("  hola   atlas ")).isEqualTo("Hola atlas");
    }
}
