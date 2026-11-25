// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.model.Usuario;
import pe.upeu.atlas.repository.UsuarioRepository;
import pe.upeu.atlas.service.ServicioActividad;

import java.util.List;

/** Administración de cuentas. Todas las operaciones exigen un administrador con sesión abierta. */
@Service
@RequiredArgsConstructor
public class ServicioUsuarios {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final SesionActual sesion;
    private final ServicioAutenticacion autenticacion; // Y
    private final ServicioActividad actividad;

    public List<Usuario> listar() {
        exigirAdministrador(); // K
        return usuarios.findAllByOrderByNombreCompletoAsc();
    } // K

    public Usuario crear(String nombre, String usuario, String clave, Rol rol) {
        exigirAdministrador();
        Usuario u = autenticacion.registrar(nombre, usuario, clave, rol);
        actividad.registrar("Usuario creado", u.getUsuario() + " (" + u.getRol().etiqueta() + ")");
        return u;
    } // K

    public void cambiarActivo(Long id, boolean activo) {
        exigirAdministrador();
        Usuario u = buscar(id);
        if (!activo) {
            protegerUltimoAdministrador(u); // Y
            if (u.getUsuario().equalsIgnoreCase(sesion.nombreUsuario())) {
                throw new NegocioException("No puedes desactivar tu propia cuenta.");
            } // K
        } // K
        u.setActivo(activo);
        usuarios.save(u);
        actividad.registrar(activo ? "Usuario activado" : "Usuario desactivado", u.getUsuario());
    } // K

    public void cambiarRol(Long id, Rol rol) {
        exigirAdministrador();
        Usuario u = buscar(id);
        if (u.getRol() == Rol.ADMINISTRADOR && rol != Rol.ADMINISTRADOR) {
            protegerUltimoAdministrador(u);
        } // Y
        u.setRol(rol);
        usuarios.save(u);
        actividad.registrar("Rol cambiado", u.getUsuario() + " → " + rol.etiqueta()); // Y
    }

    public void restablecerClave(Long id, String nuevaClave) {
        exigirAdministrador();
        ServicioAutenticacion.validarClave(nuevaClave);
        Usuario u = buscar(id);
        u.setContrasenaHash(codificador.encode(nuevaClave));
        usuarios.save(u);
        actividad.registrar("Contraseña restablecida", u.getUsuario());
    }

    private Usuario buscar(Long id) { // K
        return usuarios.findById(id).orElseThrow(() -> new NegocioException("El usuario ya no existe.")); // Y
    } // K

    /** Evita quedarse sin ningún administrador activo. */
    private void protegerUltimoAdministrador(Usuario u) {
        if (u.getRol() == Rol.ADMINISTRADOR && u.isActivo()
                && usuarios.countByRolAndActivoTrue(Rol.ADMINISTRADOR) <= 1) { // Y
            throw new NegocioException("Debe quedar al menos un administrador activo.");
        }
    }

    private void exigirAdministrador() {
        if (sesion.rol().filter(Rol::puedeAdministrarUsuarios).isEmpty()) {
            throw new PermisoDenegadoException("Solo un administrador puede gestionar usuarios.");
        }
    } // Y
} // K
