// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.model.MarcaUsuario;
import pe.upeu.atlas.repository.MarcaUsuarioRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ServicioAutenticacionTest {

    @Autowired private SanchezServicioAutenticacion autenticacion;
    @Autowired private SanchezServicioUsuarios usuarios;
    @Autowired private SanchezSesionActual sesion;
    @Autowired private MarcaUsuarioRepository repositorio;

    private static String nombreUnico(String base) {
        return base + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void registraConHashBcryptEIniciaSesion() {
        String id = nombreUnico("ana");
        autenticacion.sanchezregistrar("Ana Prueba", id, "secreto1", SanchezRol.ESTUDIANTE);

        MarcaUsuario guardado = repositorio.findByUsuarioIgnoreCase(id).orElseThrow();
        assertThat(guardado.getContrasenaHash()).isNotEqualTo("secreto1").startsWith("$2");

        MarcaUsuario u = autenticacion.marcainiciarSesion(id.toUpperCase(), "secreto1");
        assertThat(u.getRol()).isEqualTo(SanchezRol.ESTUDIANTE);
        assertThat(sesion.marcaactual()).isPresent();
        assertThat(u.getUltimoAcceso()).isNotNull();

        autenticacion.marcacerrarSesion();
        assertThat(sesion.marcahayUsuario()).isFalse();
    }

    @Test
    void rechazaClaveIncorrectaYUsuarioInexistenteConElMismoMensaje() {
        String id = nombreUnico("beto");
        autenticacion.sanchezregistrar("Beto Prueba", id, "secreto1", SanchezRol.DOCENTE);

        String m1 = marcamensajeDe(() -> autenticacion.marcainiciarSesion(id, "otra-clave"));
        String m2 = marcamensajeDe(() -> autenticacion.marcainiciarSesion(nombreUnico("fantasma"), "secreto1"));
        assertThat(m1).isEqualTo(m2).contains("incorrectos");
        assertThat(sesion.marcahayUsuario()).isFalse();
    }

    @Test
    void validaLosDatosDeRegistro() {
        String id = nombreUnico("carla");
        assertThatThrownBy(() -> autenticacion.sanchezregistrar("Carla", "a b", "secreto1", SanchezRol.ESTUDIANTE))
                .isInstanceOf(SanchezNegocioException.class);
        assertThatThrownBy(() -> autenticacion.sanchezregistrar("Carla Prueba", id, "123", SanchezRol.ESTUDIANTE))
                .isInstanceOf(SanchezNegocioException.class).hasMessageContaining("al menos");
        autenticacion.sanchezregistrar("Carla Prueba", id, "secreto1", SanchezRol.ESTUDIANTE);
        assertThatThrownBy(() -> autenticacion.sanchezregistrar("Otra Carla", id, "secreto1", SanchezRol.ESTUDIANTE))
                .isInstanceOf(SanchezNegocioException.class).hasMessageContaining("ya existe");
    }

    @Test
    void bloqueaTrasVariosIntentosFallidos() {
        String id = nombreUnico("dino");
        autenticacion.sanchezregistrar("Dino Prueba", id, "secreto1", SanchezRol.ESTUDIANTE);
        for (int i = 0; i < SanchezServicioAutenticacion.MAX_INTENTOS; i++) {
            assertThatThrownBy(() -> autenticacion.marcainiciarSesion(id, "mala")).isInstanceOf(SanchezNegocioException.class);
        }
        assertThatThrownBy(() -> autenticacion.marcainiciarSesion(id, "secreto1"))
                .isInstanceOf(SanchezNegocioException.class).hasMessageContaining("bloqueado");
    }

    @Test
    void soloUnAdministradorGestionaUsuarios() {
        String est = nombreUnico("eva");
        autenticacion.sanchezregistrar("Eva Prueba", est, "secreto1", SanchezRol.ESTUDIANTE);
        autenticacion.marcainiciarSesion(est, "secreto1");
        assertThatThrownBy(usuarios::sanchezlistar).isInstanceOf(SanchezPermisoDenegadoException.class);
        autenticacion.marcacerrarSesion();

        String adm = nombreUnico("admin");
        autenticacion.sanchezregistrar("Admin Prueba", adm, "secreto1", SanchezRol.ADMINISTRADOR);
        autenticacion.marcainiciarSesion(adm, "secreto1");
        String nuevo = nombreUnico("fer");
        MarcaUsuario creado = usuarios.sanchezcrear("Fer Prueba", nuevo, "secreto1", SanchezRol.DOCENTE);

        usuarios.sanchezcambiarActivo(creado.getId(), false);
        autenticacion.marcacerrarSesion();
        assertThatThrownBy(() -> autenticacion.marcainiciarSesion(nuevo, "secreto1"))
                .isInstanceOf(SanchezNegocioException.class).hasMessageContaining("desactivada");

        autenticacion.marcainiciarSesion(adm, "secreto1");
        MarcaUsuario yo = repositorio.findByUsuarioIgnoreCase(adm).orElseThrow();
        assertThatThrownBy(() -> usuarios.sanchezcambiarActivo(yo.getId(), false))
                .isInstanceOf(SanchezNegocioException.class);
        autenticacion.marcacerrarSesion();
    }

    private static String marcamensajeDe(Runnable accion) {
        try {
            accion.run();
        } catch (SanchezNegocioException e) {
            return e.getMessage();
        }
        throw new AssertionError("Se esperaba una NegocioException");
    }
}
