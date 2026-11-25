// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente.herramientas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.plataforma.SanchezLanzador;
import pe.upeu.atlas.plataforma.SanchezSistemaOperativo;
import pe.upeu.atlas.seguridad.SanchezConfirmadorAcciones;
import pe.upeu.atlas.service.MarcaMonitorSistema;
import pe.upeu.atlas.service.SanchezServicioActividad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HerramientasSistemaTest {

    @TempDir Path base;

    private SanchezPropiedadesAtlas propiedades;
    private SanchezLanzador lanzador;
    private SanchezConfirmadorAcciones confirmador;
    private SanchezServicioActividad actividad;
    private SanchezHerramientasSistema herramientas;
    private Path documentos;

    @BeforeEach
    void preparar() throws IOException {
        documentos = Files.createDirectories(base.resolve("Documentos"));
        Files.createDirectories(base.resolve("Descargas"));
        Files.createFile(documentos.resolve("tarea.pdf"));

        propiedades = new SanchezPropiedadesAtlas();
        propiedades.setDirectorioBase(base.resolve(".atlas"));
        propiedades.setCarpetaCapturas(base.resolve("capturas"));
        propiedades.setCarpetasPermitidas(List.of(documentos, base.resolve("Descargas")));

        lanzador = mock(SanchezLanzador.class);
        when(lanzador.marcasistema()).thenReturn(SanchezSistemaOperativo.WINDOWS);
        confirmador = mock(SanchezConfirmadorAcciones.class);
        actividad = mock(SanchezServicioActividad.class);
        herramientas = new SanchezHerramientasSistema(propiedades, mock(MarcaMonitorSistema.class), lanzador, confirmador, actividad,
                mock(pe.upeu.atlas.service.SanchezServicioDocumentos.class));
    }

    @Test
    void calculaYRegistraLaActividad() {
        assertThat(herramientas.calcular("15*12+3")).contains("183");
        verify(actividad).sanchezregistrar(anyString(), any());
    }

    @Test
    void modoSoloLecturaBloqueaLasAccionesQueCambianElEquipo() {
        propiedades.setModoSoloLectura(true);

        assertThat(herramientas.tomarCaptura()).contains("solo lectura");
        assertThat(herramientas.guardarNota("t", "texto")).contains("solo lectura");
        assertThat(herramientas.controlarVolumen("subir", 3)).contains("solo lectura");
        assertThat(herramientas.abrirAplicacion("calculadora")).contains("solo lectura");
        assertThat(herramientas.bloquearPantalla()).contains("solo lectura");
        verify(lanzador, never()).sanchezejecutar(any(String[].class));
        // Lo que solo lee sigue funcionando
        assertThat(herramientas.calcular("1+1")).contains("2");
    }

    @Test
    void noBloqueaLaPantallaSinConfirmacion() {
        when(confirmador.sanchezconfirmar(anyString(), anyString())).thenReturn(false);

        assertThat(herramientas.bloquearPantalla()).contains("Cancelé");
        verify(lanzador, never()).sanchezejecutar(any(String[].class));

        when(confirmador.sanchezconfirmar(anyString(), anyString())).thenReturn(true);
        assertThat(herramientas.bloquearPantalla()).contains("bloqueada");
        verify(lanzador).sanchezejecutar("rundll32.exe", "user32.dll,LockWorkStation");
    }

    @Test
    void soloAbreAplicacionesDeLaListaFija() {
        assertThat(herramientas.abrirAplicacion("cmd.exe /c del *")).contains("Solo puedo abrir");
        verify(lanzador, never()).sanchezejecutar(any(String[].class));

        assertThat(herramientas.abrirAplicacion("Calculadora")).contains("Abrí");
        verify(lanzador).sanchezejecutar("calc.exe");
    }

    @Test
    void validaElVolumenAntesDeEjecutar() {
        assertThat(herramientas.controlarVolumen("explotar", 5)).contains("subir, bajar o silenciar");
        verify(lanzador, never()).marcaejecutarYEsperar(org.mockito.ArgumentMatchers.anyInt(), any(String[].class));
    }

    @Test
    void siElUsuarioInterrumpioNingunaHerramientaActua() {
        java.util.concurrent.atomic.AtomicBoolean cancelado = new java.util.concurrent.atomic.AtomicBoolean(true);
        SanchezHerramientasSistema.TURNO.set(cancelado);
        try {
            assertThat(herramientas.abrirAplicacion("calculadora")).contains("interrumpió");
            assertThat(herramientas.controlarVolumen("subir", 3)).contains("interrumpió");
            assertThat(herramientas.tomarCaptura()).contains("interrumpió");
            assertThat(herramientas.guardarNota("t", "texto")).contains("interrumpió");
            assertThat(herramientas.bloquearPantalla()).contains("interrumpió");
            assertThat(herramientas.calcular("1+1")).contains("interrumpió");
            verify(lanzador, never()).marcaabrir(any());
            verify(lanzador, never()).sanchezejecutar(any(String[].class));
            verify(confirmador, never()).sanchezconfirmar(anyString(), anyString());
        } finally {
            SanchezHerramientasSistema.TURNO.remove();
        }
        // Sin cancelación todo vuelve a funcionar
        assertThat(herramientas.calcular("1+1")).contains("2");
    }

    @Test
    void guardaNotasDentroDeLaCarpetaDeAtlas() throws IOException {
        assertThat(herramientas.guardarNota("Mi tarea ../../x", "Estudiar redes")).contains("Guardé");
        try (var s = Files.walk(propiedades.getDirectorioBase())) {
            List<Path> notas = s.filter(Files::isRegularFile).toList();
            assertThat(notas).hasSize(1);
            assertThat(notas.get(0).getFileName().toString()).endsWith(".txt").doesNotContain("..");
            assertThat(Files.readString(notas.get(0))).contains("Estudiar redes");
        }
    }
}
