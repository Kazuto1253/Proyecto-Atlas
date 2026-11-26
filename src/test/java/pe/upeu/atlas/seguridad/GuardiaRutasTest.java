package pe.upeu.atlas.seguridad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.plataforma.MarcaCarpetasConocidas;
import pe.upeu.atlas.seguridad.MarcaGuardiaRutas.MarcaOperacion;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuardiaRutasTest {

    @TempDir Path casa;

    private MarcaGuardiaRutas guardia;
    private Path documentos;

    @BeforeEach
    void preparar() {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"), "Pruebas pensadas para Windows");
        documentos = casa.resolve("Documents");
        SanchezPropiedadesAtlas props = new SanchezPropiedadesAtlas();
        props.setDirectorioBase(casa.resolve(".atlas"));
        props.setCarpetasProtegidas(java.util.List.of(casa.resolve("Privado")));
        MarcaCarpetasConocidas conocidas = mock(MarcaCarpetasConocidas.class);
        when(conocidas.marcaprincipales()).thenReturn(Map.of("documentos", documentos, "usuario", casa));
        guardia = new MarcaGuardiaRutas(props, conocidas, casa);
    }

    @Test
    void permiteTrabajarEnTusCarpetasYEnOtrosDiscos() {
        assertThat(guardia.sanchezverificar(documentos.resolve("redes/tarea.txt"), MarcaOperacion.ESCRIBIR)).isNotNull();
        assertThat(guardia.sanchezverificar(casa.resolve("Desktop"), MarcaOperacion.ELIMINAR)).isNotNull();
        assertThat(guardia.sanchezverificar(Path.of("D:\\Proyectos\\atlas\\notas.md"), MarcaOperacion.ESCRIBIR)).isNotNull();
        assertThat(guardia.sanchezverificar(Path.of("D:\\"), MarcaOperacion.LEER)).isNotNull();
    }

    @Test
    void bloqueaElSistemaYLasCredenciales() {
        for (String ruta : new String[]{"C:\\Windows\\System32\\drivers", "C:\\Program Files\\Java", "C:\\ProgramData\\x"}) {
            assertThatThrownBy(() -> guardia.sanchezverificar(Path.of(ruta), MarcaOperacion.LEER))
                    .as(ruta).isInstanceOf(SanchezPermisoDenegadoException.class).hasMessageContaining("protegida");
        }
        assertThatThrownBy(() -> guardia.sanchezverificar(casa.resolve(".ssh/id_rsa"), MarcaOperacion.LEER)).isInstanceOf(SanchezPermisoDenegadoException.class);
        assertThatThrownBy(() -> guardia.sanchezverificar(casa.resolve("AppData/Local/Google"), MarcaOperacion.LEER)).isInstanceOf(SanchezPermisoDenegadoException.class);
        assertThatThrownBy(() -> guardia.sanchezverificar(casa.resolve("Privado/secreto.txt"), MarcaOperacion.LEER)).isInstanceOf(SanchezPermisoDenegadoException.class);
    }

    @Test
    void noSeSaltaLaProteccionConPuntosPuntos() {
        assertThatThrownBy(() -> guardia.sanchezverificar(documentos.resolve("..\\AppData\\Local\\x"), MarcaOperacion.ESCRIBIR))
                .isInstanceOf(SanchezPermisoDenegadoException.class);
        assertThatThrownBy(() -> guardia.sanchezverificar(Path.of("C:\\Users\\Alguien\\..\\..\\Windows\\System32"), MarcaOperacion.LEER))
                .isInstanceOf(SanchezPermisoDenegadoException.class);
    }

    @Test
    void protegeLosDatosInternosDeAtlas() {
        assertThat(guardia.sanchezverificar(casa.resolve(".atlas/logs/atlas.log"), MarcaOperacion.LEER)).isNotNull();
        assertThatThrownBy(() -> guardia.sanchezverificar(casa.resolve(".atlas/modelos/x"), MarcaOperacion.ESCRIBIR))
                .isInstanceOf(SanchezPermisoDenegadoException.class).hasMessageContaining("Atlas");
        assertThat(guardia.sanchezverificar(casa.resolve(".atlas/documentos/notas/a.txt"), MarcaOperacion.ESCRIBIR)).isNotNull();
        assertThatThrownBy(() -> guardia.sanchezverificar(casa.resolve("Documents/atlas.db"), MarcaOperacion.LEER))
                .isInstanceOf(SanchezPermisoDenegadoException.class);
    }

    @Test
    void noBorraCarpetasPrincipalesNiTuUsuarioNiUnaUnidad() {
        assertThatThrownBy(() -> guardia.sanchezverificar(documentos, MarcaOperacion.ELIMINAR)).isInstanceOf(SanchezPermisoDenegadoException.class)
                .hasMessageContaining("principales");
        assertThatThrownBy(() -> guardia.sanchezverificar(casa, MarcaOperacion.ELIMINAR)).isInstanceOf(SanchezPermisoDenegadoException.class);
        assertThatThrownBy(() -> guardia.sanchezverificar(Path.of("D:\\"), MarcaOperacion.ELIMINAR)).isInstanceOf(SanchezPermisoDenegadoException.class);
        assertThat(guardia.sanchezverificar(documentos.resolve("viejo.txt"), MarcaOperacion.ELIMINAR)).isNotNull();
    }

    @Test
    void noEscribeEnLaRaizDelDiscoDelSistema() {
        Path raiz = casa.getRoot();
        assertThatThrownBy(() -> guardia.sanchezverificar(raiz, MarcaOperacion.ESCRIBIR)).isInstanceOf(SanchezPermisoDenegadoException.class);
    }

    @Test
    void noEntraEnElPerfilDeOtroUsuario() {
        Path vecino = casa.getParent().resolve("OtraPersona").resolve("Documents");
        assertThatThrownBy(() -> guardia.sanchezverificar(vecino, MarcaOperacion.LEER)).isInstanceOf(SanchezPermisoDenegadoException.class)
                .hasMessageContaining("otro usuario");
        assertThat(guardia.sanchezverificar(casa.getParent().resolve("Public").resolve("x.txt"), MarcaOperacion.LEER)).isNotNull();
    }

    @Test
    void noCreaNiAbreProgramasNiScripts() {
        for (String nombre : new String[]{"a.exe", "run.BAT", "x.ps1", "setup.msi", "m.vbs", "j.jar", "atajo.lnk", "app.js"}) {
            assertThatThrownBy(() -> guardia.marcaverificarNoEjecutable(documentos.resolve(nombre))).as(nombre)
                    .isInstanceOf(SanchezPermisoDenegadoException.class);
        }
        guardia.marcaverificarNoEjecutable(documentos.resolve("notas.txt"));
        guardia.marcaverificarNoEjecutable(documentos.resolve("tarea.docx"));
        guardia.marcaverificarNoEjecutable(documentos.resolve("README"));
    }

    @Test
    void pideConfirmacionSoloParaEscribirFueraDeTuUsuarioEnElDiscoDelSistema() {
        Path enSistema = casa.getRoot().resolve("Proyectos").resolve("a.txt");
        assertThat(guardia.marcapideConfirmacionPorUbicacion(enSistema)).isTrue();
        assertThat(guardia.marcapideConfirmacionPorUbicacion(documentos.resolve("a.txt"))).isFalse();
        assertThat(guardia.marcapideConfirmacionPorUbicacion(Path.of("D:\\Proyectos\\a.txt"))).isFalse();
    }

    @Test
    void labusquedaOmiteZonasProtegidasYOcultas() {
        assertThat(guardia.sanchezdebeOmitirseAlBuscar(casa.resolve("AppData"))).isTrue();
        assertThat(guardia.sanchezdebeOmitirseAlBuscar(casa.resolve(".git"))).isTrue();
        assertThat(guardia.sanchezdebeOmitirseAlBuscar(documentos.resolve("node_modules"))).isTrue();
        assertThat(guardia.sanchezdebeOmitirseAlBuscar(documentos.resolve("redes"))).isFalse();
    }
}
