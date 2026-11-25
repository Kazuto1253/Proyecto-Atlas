// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

import org.springframework.stereotype.Component;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.model.Usuario;

import java.util.Optional;

/** Usuario que tiene la sesión abierta en esta ejecución (aplicación de escritorio: uno a la vez). */
@Component
public class SesionActual {

    private volatile Usuario usuario;

    public void iniciar(Usuario usuario) {
        this.usuario = usuario;
    }

    public void cerrar() { // Y
        this.usuario = null;
    }

    public Optional<Usuario> actual() {
        return Optional.ofNullable(usuario); // K
    } // Y

    public boolean hayUsuario() {
        return usuario != null;
    }

    /** Nombre de usuario para bitácoras; "sistema" si nadie inició sesión. */
    public String nombreUsuario() {
        Usuario u = usuario;
        return u == null ? "sistema" : u.getUsuario();
    } // Y

    public Optional<Rol> rol() {
        return actual().map(Usuario::getRol); // K
    } // Y
}
