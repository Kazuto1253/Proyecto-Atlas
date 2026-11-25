// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AtlasApplicationTests {

    @Autowired
    private SanchezPropiedadesAtlas propiedades;

    @Test
    void cargaElContextoYLasPropiedades() {
        assertThat(propiedades.getLlm().getModelo()).isNotBlank();
        assertThat(propiedades.getVoz().getFrecuenciaMuestreo()).isEqualTo(16000);
        assertThat(propiedades.getDirectorioModelos()).endsWith(java.nio.file.Path.of("modelos"));
        assertThat(propiedades.getOcr().getIdioma()).isEqualTo("spa");
        assertThat(propiedades.getCarpetasPermitidas()).isNotEmpty();
        // application.properties se lee como ISO-8859-1: las tildes deben ir como \u00e1
        assertThat(propiedades.getCarpetasPermitidas())
                .anyMatch(p -> p.getFileName().toString().equals("Imágenes"));
        assertThat(propiedades.isModoSoloLectura()).isFalse();
    }
}
