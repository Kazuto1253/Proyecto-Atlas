// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.plataforma.CarpetasConocidas;
import pe.upeu.atlas.seguridad.GuardiaRutas.Operacion;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Qué puede y qué no puede tocar el asistente. Simula un usuario dentro de una carpeta temporal. */
class GuardiaRutasTest {

    @TempDir Path casa;

    private GuardiaRutas guardia;
    private Path documentos;

    @BeforeEach
    void preparar() {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"), "Pruebas pensadas para Windows");
        documentos = casa.resolve("Documents");
        PropiedadesAtlas props = new PropiedadesAtlas();
        props.setDirectorioBase(casa.resolve(".atlas"));
        props.setCarpetasProtegidas(java.util.List.of(casa.resolve("Privado")));
        CarpetasConocidas conocidas = mock(CarpetasConocidas.class);
        when(conocidas.principales()).thenReturn(Map.of("documentos", documentos, "usuario", casa));
        guardia = new GuardiaRutas(props, conocidas, casa);
    }

    @Test
    void permiteTrabajarEnTusCarpetasYEnOtrosDiscos() {
        assertThat(guardia.verificar(documentos.resolve("redes/tarea.txt"), Operacion.ESCRIBIR)).isNotNull();
        assertThat(guardia.verificar(casa.resolve("Desktop"), Operacion.ELIMINAR)).isNotNull();
        assertThat(guardia.verificar(Path.of("D:\\Proyectos\\atlas\\notas.md"), Operacion.ESCRIBIR)).isNotNull();
        assertThat(guardia.verificar(Path.of("D:\\"), Operacion.LEER)).isNotNull();
    }

    @Test
    void bloqueaElSistemaYLasCredenciales() {
        for (String ruta : new String[]{"C:\\Windows\\System32\\drivers", "C:\\Program Files\\Java", "C:\\ProgramData\\x"}) {
            assertThatThrownBy(() -> guardia.verificar(Path.of(ruta), Operacion.LEER))
                    .as(ruta).isInstanceOf(PermisoDenegadoException.class).hasMessageContaining("protegida");
        }
        assertThatThrownBy(() -> guardia.verificar(casa.resolve(".ssh/id_rsa"), Operacion.LEER)).isInstanceOf(PermisoDenegadoException.class);
        assertThatThrownBy(() -> guardia.verificar(casa.resolve("AppData/Local/Google"), Operacion.LEER)).isInstanceOf(PermisoDenegadoException.class);
        assertThatThrownBy(() -> guardia.verificar(casa.resolve("Privado/secreto.txt"), Operacion.LEER)).isInstanceOf(PermisoDenegadoException.class);
    }

    @Test
    void noSeSaltaLaProteccionConPuntosPuntos() {
        assertThatThrownBy(() -> guardia.verificar(documentos.resolve("..\\AppData\\Local\\x"), Operacion.ESCRIBIR))
                .isInstanceOf(PermisoDenegadoException.class);
        assertThatThrownBy(() -> guardia.verificar(Path.of("C:\\Users\\Alguien\\..\\..\\Windows\\System32"), Operacion.LEER))
                .isInstanceOf(PermisoDenegadoException.class);
    }

    @Test
    void protegeLosDatosInternosDeAtlas() {
        assertThat(guardia.verificar(casa.resolve(".atlas/logs/atlas.log"), Operacion.LEER)).isNotNull();
        assertThatThrownBy(() -> guardia.verificar(casa.resolve(".atlas/modelos/x"), Operacion.ESCRIBIR))
                .isInstanceOf(PermisoDenegadoException.class).hasMessageContaining("Atlas");
        assertThat(guardia.verificar(casa.resolve(".atlas/documentos/notas/a.txt"), Operacion.ESCRIBIR)).isNotNull();
        assertThatThrownBy(() -> guardia.verificar(casa.resolve("Documents/atlas.db"), Operacion.LEER))
                .isInstanceOf(PermisoDenegadoException.class);
    }

    @Test
    void noBorraCarpetasPrincipalesNiTuUsuarioNiUnaUnidad() {
        assertThatThrownBy(() -> guardia.verificar(documentos, Operacion.ELIMINAR)).isInstanceOf(PermisoDenegadoException.class)
                .hasMessageContaining("principales");
        assertThatThrownBy(() -> guardia.verificar(casa, Operacion.ELIMINAR)).isInstanceOf(PermisoDenegadoException.class);
        assertThatThrownBy(() -> guardia.verificar(Path.of("D:\\"), Operacion.ELIMINAR)).isInstanceOf(PermisoDenegadoException.class);
        assertThat(guardia.verificar(documentos.resolve("viejo.txt"), Operacion.ELIMINAR)).isNotNull();
    }

    @Test
    void noEscribeEnLaRaizDelDiscoDelSistema() {
        Path raiz = casa.getRoot();
        assertThatThrownBy(() -> guardia.verificar(raiz, Operacion.ESCRIBIR)).isInstanceOf(PermisoDenegadoException.class);
    }

    @Test
    void noEntraEnElPerfilDeOtroUsuario() {
        Path vecino = casa.getParent().resolve("OtraPersona").resolve("Documents");
        assertThatThrownBy(() -> guardia.verificar(vecino, Operacion.LEER)).isInstanceOf(PermisoDenegadoException.class)
                .hasMessageContaining("otro usuario");
        assertThat(guardia.verificar(casa.getParent().resolve("Public").resolve("x.txt"), Operacion.LEER)).isNotNull();
    }

    @Test
    void noCreaNiAbreProgramasNiScripts() {
        for (String nombre : new String[]{"a.exe", "run.BAT", "x.ps1", "setup.msi", "m.vbs", "j.jar", "atajo.lnk", "app.js"}) {
            assertThatThrownBy(() -> guardia.verificarNoEjecutable(documentos.resolve(nombre))).as(nombre)
                    .isInstanceOf(PermisoDenegadoException.class);
        }
        guardia.verificarNoEjecutable(documentos.resolve("notas.txt"));
        guardia.verificarNoEjecutable(documentos.resolve("tarea.docx"));
        guardia.verificarNoEjecutable(documentos.resolve("README"));
    }

    @Test
    void pideConfirmacionSoloParaEscribirFueraDeTuUsuarioEnElDiscoDelSistema() {
        Path enSistema = casa.getRoot().resolve("Proyectos").resolve("a.txt");
        assertThat(guardia.pideConfirmacionPorUbicacion(enSistema)).isTrue();
        assertThat(guardia.pideConfirmacionPorUbicacion(documentos.resolve("a.txt"))).isFalse();
        assertThat(guardia.pideConfirmacionPorUbicacion(Path.of("D:\\Proyectos\\a.txt"))).isFalse();
    }

    @Test
    void labusquedaOmiteZonasProtegidasYOcultas() {
        assertThat(guardia.debeOmitirseAlBuscar(casa.resolve("AppData"))).isTrue();
        assertThat(guardia.debeOmitirseAlBuscar(casa.resolve(".git"))).isTrue();
        assertThat(guardia.debeOmitirseAlBuscar(documentos.resolve("node_modules"))).isTrue();
        assertThat(guardia.debeOmitirseAlBuscar(documentos.resolve("redes"))).isFalse();
    }
}
