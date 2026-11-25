// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.model.Usuario;
import pe.upeu.atlas.repository.UsuarioRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ServicioAutenticacionTest {

    @Autowired private ServicioAutenticacion autenticacion;
    @Autowired private ServicioUsuarios usuarios;
    @Autowired private SesionActual sesion;
    @Autowired private UsuarioRepository repositorio;

    private static String nombreUnico(String base) {
        return base + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void registraConHashBcryptEIniciaSesion() {
        String id = nombreUnico("ana");
        autenticacion.registrar("Ana Prueba", id, "secreto1", Rol.ESTUDIANTE);

        Usuario guardado = repositorio.findByUsuarioIgnoreCase(id).orElseThrow();
        assertThat(guardado.getContrasenaHash()).isNotEqualTo("secreto1").startsWith("$2");

        Usuario u = autenticacion.iniciarSesion(id.toUpperCase(), "secreto1");
        assertThat(u.getRol()).isEqualTo(Rol.ESTUDIANTE);
        assertThat(sesion.actual()).isPresent();
        assertThat(u.getUltimoAcceso()).isNotNull();

        autenticacion.cerrarSesion();
        assertThat(sesion.hayUsuario()).isFalse();
    }

    @Test
    void rechazaClaveIncorrectaYUsuarioInexistenteConElMismoMensaje() {
        String id = nombreUnico("beto");
        autenticacion.registrar("Beto Prueba", id, "secreto1", Rol.DOCENTE);

        String m1 = mensajeDe(() -> autenticacion.iniciarSesion(id, "otra-clave"));
        String m2 = mensajeDe(() -> autenticacion.iniciarSesion(nombreUnico("fantasma"), "secreto1"));
        assertThat(m1).isEqualTo(m2).contains("incorrectos");
        assertThat(sesion.hayUsuario()).isFalse();
    }

    @Test
    void validaLosDatosDeRegistro() {
        String id = nombreUnico("carla");
        assertThatThrownBy(() -> autenticacion.registrar("Carla", "a b", "secreto1", Rol.ESTUDIANTE))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> autenticacion.registrar("Carla Prueba", id, "123", Rol.ESTUDIANTE))
                .isInstanceOf(NegocioException.class).hasMessageContaining("al menos");
        autenticacion.registrar("Carla Prueba", id, "secreto1", Rol.ESTUDIANTE);
        assertThatThrownBy(() -> autenticacion.registrar("Otra Carla", id, "secreto1", Rol.ESTUDIANTE))
                .isInstanceOf(NegocioException.class).hasMessageContaining("ya existe");
    }

    @Test
    void bloqueaTrasVariosIntentosFallidos() {
        String id = nombreUnico("dino");
        autenticacion.registrar("Dino Prueba", id, "secreto1", Rol.ESTUDIANTE);
        for (int i = 0; i < ServicioAutenticacion.MAX_INTENTOS; i++) {
            assertThatThrownBy(() -> autenticacion.iniciarSesion(id, "mala")).isInstanceOf(NegocioException.class);
        }
        // Ni siquiera la clave correcta entra mientras dura el bloqueo
        assertThatThrownBy(() -> autenticacion.iniciarSesion(id, "secreto1"))
                .isInstanceOf(NegocioException.class).hasMessageContaining("bloqueado");
    }

    @Test
    void soloUnAdministradorGestionaUsuarios() {
        String est = nombreUnico("eva");
        autenticacion.registrar("Eva Prueba", est, "secreto1", Rol.ESTUDIANTE);
        autenticacion.iniciarSesion(est, "secreto1");
        assertThatThrownBy(usuarios::listar).isInstanceOf(PermisoDenegadoException.class);
        autenticacion.cerrarSesion();

        String adm = nombreUnico("admin");
        autenticacion.registrar("Admin Prueba", adm, "secreto1", Rol.ADMINISTRADOR);
        autenticacion.iniciarSesion(adm, "secreto1");
        String nuevo = nombreUnico("fer");
        Usuario creado = usuarios.crear("Fer Prueba", nuevo, "secreto1", Rol.DOCENTE);

        usuarios.cambiarActivo(creado.getId(), false);
        autenticacion.cerrarSesion();
        assertThatThrownBy(() -> autenticacion.iniciarSesion(nuevo, "secreto1"))
                .isInstanceOf(NegocioException.class).hasMessageContaining("desactivada");

        autenticacion.iniciarSesion(adm, "secreto1");
        Usuario yo = repositorio.findByUsuarioIgnoreCase(adm).orElseThrow();
        assertThatThrownBy(() -> usuarios.cambiarActivo(yo.getId(), false))
                .isInstanceOf(NegocioException.class);
        autenticacion.cerrarSesion();
    }

    private static String mensajeDe(Runnable accion) {
        try {
            accion.run();
        } catch (NegocioException e) {
            return e.getMessage();
        }
        throw new AssertionError("Se esperaba una NegocioException");
    }
}
