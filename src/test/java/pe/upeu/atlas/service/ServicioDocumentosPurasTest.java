// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServicioDocumentosPurasTest {

    @Test
    void troceaCortandoEnEspaciosYConSolape() {
        String texto = "palabra ".repeat(300);
        List<String> trozos = SanchezServicioDocumentos.marcatrocear(texto, 200, 40);

        assertThat(trozos).hasSizeGreaterThan(5);
        assertThat(trozos).allSatisfy(t -> {
            assertThat(t.length()).isLessThanOrEqualTo(200);
            assertThat(t).doesNotStartWith(" ").doesNotEndWith(" ");
        });
        // el solape hace que el final de un trozo reaparezca al inicio del siguiente
        assertThat(trozos.get(1)).startsWith("palabra");
    }

    @Test
    void textoCortoOVacio() {
        assertThat(SanchezServicioDocumentos.marcatrocear("Hola mundo", 900, 120)).containsExactly("Hola mundo");
        assertThat(SanchezServicioDocumentos.marcatrocear("   \n\t ", 900, 120)).isEmpty();
        assertThat(SanchezServicioDocumentos.marcatrocear(null, 900, 120)).isEmpty();
    }

    @Test
    void siempreAvanzaAunqueNoHayaEspacios() {
        List<String> trozos = SanchezServicioDocumentos.marcatrocear("x".repeat(1000), 100, 20);
        assertThat(trozos).hasSizeGreaterThan(9);
        assertThat(String.join("", trozos).length()).isGreaterThanOrEqualTo(1000);
    }

    @Test
    void similitudCoseno() {
        float[] a = {1, 0, 0};
        assertThat(SanchezServicioDocumentos.sanchezcoseno(a, new float[]{2, 0, 0})).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(SanchezServicioDocumentos.sanchezcoseno(a, new float[]{0, 1, 0})).isZero();
        assertThat(SanchezServicioDocumentos.sanchezcoseno(a, new float[]{0, 0, 0})).isZero();
        assertThat(SanchezServicioDocumentos.sanchezcoseno(a, new float[]{-1, 0, 0})).isCloseTo(-1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void serializaYLeeVectores() {
        float[] v = {0.5f, -1.25f, 3f, 1e-5f};
        assertThat(SanchezServicioDocumentos.marcaleer(SanchezServicioDocumentos.marcaserializar(v))).containsExactly(v);
    }
}
