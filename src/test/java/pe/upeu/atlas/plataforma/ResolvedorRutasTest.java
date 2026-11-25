// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.exception.SanchezNegocioException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolvedorRutasTest {

    @TempDir Path casa;

    private MarcaResolvedorRutas resolvedor;
    private Path documentos;
    private Path escritorio;
    private Path descargas;
    private Path imagenes;

    @BeforeEach
    void preparar() throws IOException {
        documentos = Files.createDirectories(casa.resolve("Documents"));
        escritorio = Files.createDirectories(casa.resolve("Desktop"));
        descargas = Files.createDirectories(casa.resolve("Downloads"));
        imagenes = Files.createDirectories(casa.resolve("Pictures"));

        Map<String, Path> mapa = new LinkedHashMap<>();
        for (String a : new String[]{"documentos", "documents", "mis documentos"}) {
            mapa.put(a, documentos);
        }
        for (String a : new String[]{"escritorio", "desktop"}) {
            mapa.put(a, escritorio);
        }
        for (String a : new String[]{"descargas", "downloads", "mis descargas"}) {
            mapa.put(a, descargas);
        }
        for (String a : new String[]{"imagenes", "pictures", "fotos"}) {
            mapa.put(a, imagenes);
        }
        mapa.put("usuario", casa);

        MarcaCarpetasConocidas conocidas = mock(MarcaCarpetasConocidas.class);
        when(conocidas.buscar(anyString())).thenAnswer(inv -> Optional.ofNullable(mapa.get(MarcaCarpetasConocidas.sancheznormalizar(inv.getArgument(0)))));
        when(conocidas.marcaprincipales()).thenReturn(Map.of("documentos", documentos, "escritorio", escritorio));
        resolvedor = new MarcaResolvedorRutas(conocidas);
    }

    @Test
    void entiendeNombresEnEspanolEIngles() {
        assertThat(resolvedor.marcaresolver("documentos")).isEqualTo(documentos);
        assertThat(resolvedor.marcaresolver("Documents")).isEqualTo(documentos);
        assertThat(resolvedor.marcaresolver("Mis Descargas")).isEqualTo(descargas);
        assertThat(resolvedor.marcaresolver("imágenes")).isEqualTo(imagenes);
        assertThat(resolvedor.marcaresolver("la carpeta de descargas")).isEqualTo(descargas);
        assertThat(resolvedor.marcaresolver("  \"escritorio\"  ")).isEqualTo(escritorio);
    }

    @Test
    void resuelveSubcarpetasConCualquierSeparador() {
        assertThat(resolvedor.marcaresolver("escritorio/redes/tareas")).isEqualTo(escritorio.resolve("redes").resolve("tareas"));
        assertThat(resolvedor.marcaresolver("documentos\\clase\\apuntes.txt")).isEqualTo(documentos.resolve("clase").resolve("apuntes.txt"));
        assertThat(resolvedor.marcaresolver("la carpeta de documentos/redes")).isEqualTo(documentos.resolve("redes"));
    }

    @Test
    void aceptaRutasCompletasYDiscos() {
        assertThat(resolvedor.marcaresolver("D:\\Proyectos\\atlas")).isEqualTo(Path.of("D:\\Proyectos\\atlas"));
        assertThat(resolvedor.marcaresolver("d:/Proyectos")).isEqualTo(Path.of("D:\\Proyectos"));
        assertThat(resolvedor.marcaresolver("disco D")).isEqualTo(Path.of("D:\\"));
        assertThat(resolvedor.marcaresolver("unidad d:")).isEqualTo(Path.of("D:\\"));
        assertThat(resolvedor.marcaresolver("D:\\a\\..\\b")).isEqualTo(Path.of("D:\\b"));
    }

    @Test
    void encuentraUnaCarpetaPorSuNombreDentroDeTusSitiosHabituales() throws IOException {
        Files.createDirectories(escritorio.resolve("Redes de Computadoras"));
        assertThat(resolvedor.marcaresolver("redes de computadoras")).isEqualTo(escritorio.resolve("Redes de Computadoras"));
        assertThat(resolvedor.marcaresolver("redes de computadoras/tarea1")).isEqualTo(escritorio.resolve("Redes de Computadoras").resolve("tarea1"));
    }

    @Test
    void siNoLaEncuentraExplicaQueHacer() {
        assertThatThrownBy(() -> resolvedor.marcaresolver("carpeta fantasma"))
                .isInstanceOf(SanchezNegocioException.class).hasMessageContaining("No encontré").hasMessageContaining("ruta completa");
        assertThatThrownBy(() -> resolvedor.marcaresolver("   ")).isInstanceOf(SanchezNegocioException.class);
        assertThatThrownBy(() -> resolvedor.marcaresolver(null)).isInstanceOf(SanchezNegocioException.class);
        assertThatThrownBy(() -> resolvedor.marcaresolver("disco Q")).isInstanceOf(SanchezNegocioException.class).hasMessageContaining("Q:");
    }
}
