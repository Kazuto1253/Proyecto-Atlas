// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import org.junit.jupiter.api.Test;
import pe.upeu.atlas.exception.SanchezNegocioException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraTest {

    @Test
    void respetaLaPrecedenciaDeOperadores() {
        assertThat(SanchezCalculadora.marcaevaluar("15*12+3")).isEqualTo(183);
        assertThat(SanchezCalculadora.marcaevaluar("2+3*4")).isEqualTo(14);
        assertThat(SanchezCalculadora.marcaevaluar("(2+3)*4")).isEqualTo(20);
    }

    @Test
    void admitePotenciasRaicesNegativosYDecimales() {
        assertThat(SanchezCalculadora.marcaevaluar("2^3^2")).isEqualTo(512);
        assertThat(SanchezCalculadora.marcaevaluar("raiz(16)+1")).isEqualTo(5);
        assertThat(SanchezCalculadora.marcaevaluar("-4+10")).isEqualTo(6);
        assertThat(SanchezCalculadora.marcaevaluar("1,5*2")).isEqualTo(3);
        assertThat(SanchezCalculadora.marcaevaluar("10 % 4")).isEqualTo(2);
    }

    @Test
    void formateaSinCerosDeSobra() {
        assertThat(SanchezCalculadora.sanchezformatear(183.0)).isEqualTo("183");
        assertThat(SanchezCalculadora.sanchezformatear(2.5)).isEqualTo("2,5");
        assertThat(SanchezCalculadora.sanchezformatear(1.0 / 3)).isEqualTo("0,333333");
    }

    @Test
    void rechazaEntradasInvalidasOPeligrosas() {
        assertThatThrownBy(() -> SanchezCalculadora.marcaevaluar("5/0")).isInstanceOf(SanchezNegocioException.class);
        assertThatThrownBy(() -> SanchezCalculadora.marcaevaluar("2+")).isInstanceOf(SanchezNegocioException.class);
        assertThatThrownBy(() -> SanchezCalculadora.marcaevaluar("(2+3")).isInstanceOf(SanchezNegocioException.class);
        assertThatThrownBy(() -> SanchezCalculadora.marcaevaluar("raiz(-1)")).isInstanceOf(SanchezNegocioException.class);
        assertThatThrownBy(() -> SanchezCalculadora.marcaevaluar("System.exit(0)")).isInstanceOf(SanchezNegocioException.class);
        assertThatThrownBy(() -> SanchezCalculadora.marcaevaluar("  ")).isInstanceOf(SanchezNegocioException.class);
    }
}
