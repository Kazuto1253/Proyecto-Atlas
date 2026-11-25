// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import org.junit.jupiter.api.Test;
import pe.upeu.atlas.exception.NegocioException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraTest {

    @Test
    void respetaLaPrecedenciaDeOperadores() {
        assertThat(Calculadora.evaluar("15*12+3")).isEqualTo(183);
        assertThat(Calculadora.evaluar("2+3*4")).isEqualTo(14);
        assertThat(Calculadora.evaluar("(2+3)*4")).isEqualTo(20);
    }

    @Test
    void admitePotenciasRaicesNegativosYDecimales() {
        assertThat(Calculadora.evaluar("2^3^2")).isEqualTo(512);
        assertThat(Calculadora.evaluar("raiz(16)+1")).isEqualTo(5);
        assertThat(Calculadora.evaluar("-4+10")).isEqualTo(6);
        assertThat(Calculadora.evaluar("1,5*2")).isEqualTo(3);
        assertThat(Calculadora.evaluar("10 % 4")).isEqualTo(2);
    }

    @Test
    void formateaSinCerosDeSobra() {
        assertThat(Calculadora.formatear(183.0)).isEqualTo("183");
        assertThat(Calculadora.formatear(2.5)).isEqualTo("2,5");
        assertThat(Calculadora.formatear(1.0 / 3)).isEqualTo("0,333333");
    }

    @Test
    void rechazaEntradasInvalidasOPeligrosas() {
        assertThatThrownBy(() -> Calculadora.evaluar("5/0")).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> Calculadora.evaluar("2+")).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> Calculadora.evaluar("(2+3")).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> Calculadora.evaluar("raiz(-1)")).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> Calculadora.evaluar("System.exit(0)")).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> Calculadora.evaluar("  ")).isInstanceOf(NegocioException.class);
    }
}
