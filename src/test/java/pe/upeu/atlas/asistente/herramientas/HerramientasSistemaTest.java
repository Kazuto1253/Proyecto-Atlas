// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente.herramientas;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.plataforma.Lanzador;
import pe.upeu.atlas.plataforma.SistemaOperativo;
import pe.upeu.atlas.seguridad.ConfirmadorAcciones;
import pe.upeu.atlas.service.MonitorSistema;
import pe.upeu.atlas.service.ServicioActividad;

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

    private PropiedadesAtlas propiedades;
    private Lanzador lanzador;
    private ConfirmadorAcciones confirmador;
    private ServicioActividad actividad;
    private HerramientasSistema herramientas;
    private Path documentos;

    @BeforeEach
    void preparar() throws IOException {
        documentos = Files.createDirectories(base.resolve("Documentos"));
        Files.createDirectories(base.resolve("Descargas"));
        Files.createFile(documentos.resolve("tarea.pdf"));

        propiedades = new PropiedadesAtlas();
        propiedades.setDirectorioBase(base.resolve(".atlas"));
        propiedades.setCarpetaCapturas(base.resolve("capturas"));
        propiedades.setCarpetasPermitidas(List.of(documentos, base.resolve("Descargas")));

        lanzador = mock(Lanzador.class);
        when(lanzador.sistema()).thenReturn(SistemaOperativo.WINDOWS);
        confirmador = mock(ConfirmadorAcciones.class);
        actividad = mock(ServicioActividad.class);
        herramientas = new HerramientasSistema(propiedades, mock(MonitorSistema.class), lanzador, confirmador, actividad,
                mock(pe.upeu.atlas.service.ServicioDocumentos.class));
    }

    @Test
    void calculaYRegistraLaActividad() {
        assertThat(herramientas.calcular("15*12+3")).contains("183");
        verify(actividad).registrar(anyString(), any());
    }

    @Test
    void modoSoloLecturaBloqueaLasAccionesQueCambianElEquipo() {
        propiedades.setModoSoloLectura(true);

        assertThat(herramientas.tomarCaptura()).contains("solo lectura");
        assertThat(herramientas.guardarNota("t", "texto")).contains("solo lectura");
        assertThat(herramientas.controlarVolumen("subir", 3)).contains("solo lectura");
        assertThat(herramientas.abrirAplicacion("calculadora")).contains("solo lectura");
        assertThat(herramientas.bloquearPantalla()).contains("solo lectura");
        verify(lanzador, never()).ejecutar(any(String[].class));
        // Lo que solo lee sigue funcionando
        assertThat(herramientas.calcular("1+1")).contains("2");
    }

    @Test
    void noBloqueaLaPantallaSinConfirmacion() {
        when(confirmador.confirmar(anyString(), anyString())).thenReturn(false);

        assertThat(herramientas.bloquearPantalla()).contains("Cancelé");
        verify(lanzador, never()).ejecutar(any(String[].class));

        when(confirmador.confirmar(anyString(), anyString())).thenReturn(true);
        assertThat(herramientas.bloquearPantalla()).contains("bloqueada");
        verify(lanzador).ejecutar("rundll32.exe", "user32.dll,LockWorkStation");
    }

    @Test
    void soloAbreAplicacionesDeLaListaFija() {
        assertThat(herramientas.abrirAplicacion("cmd.exe /c del *")).contains("Solo puedo abrir");
        verify(lanzador, never()).ejecutar(any(String[].class));

        assertThat(herramientas.abrirAplicacion("Calculadora")).contains("Abrí");
        verify(lanzador).ejecutar("calc.exe");
    }

    @Test
    void validaElVolumenAntesDeEjecutar() {
        assertThat(herramientas.controlarVolumen("explotar", 5)).contains("subir, bajar o silenciar");
        verify(lanzador, never()).ejecutarYEsperar(org.mockito.ArgumentMatchers.anyInt(), any(String[].class));
    }

    @Test
    void siElUsuarioInterrumpioNingunaHerramientaActua() {
        java.util.concurrent.atomic.AtomicBoolean cancelado = new java.util.concurrent.atomic.AtomicBoolean(true);
        HerramientasSistema.TURNO.set(cancelado);
        try {
            assertThat(herramientas.abrirAplicacion("calculadora")).contains("interrumpió");
            assertThat(herramientas.controlarVolumen("subir", 3)).contains("interrumpió");
            assertThat(herramientas.tomarCaptura()).contains("interrumpió");
            assertThat(herramientas.guardarNota("t", "texto")).contains("interrumpió");
            assertThat(herramientas.bloquearPantalla()).contains("interrumpió");
            assertThat(herramientas.calcular("1+1")).contains("interrumpió");
            verify(lanzador, never()).abrir(any());
            verify(lanzador, never()).ejecutar(any(String[].class));
            verify(confirmador, never()).confirmar(anyString(), anyString());
        } finally {
            HerramientasSistema.TURNO.remove();
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
