// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

import org.springframework.stereotype.Component;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.model.MarcaUsuario;

import java.util.Optional;

/** Usuario que tiene la sesión abierta en esta ejecución (aplicación de escritorio: uno a la vez). */
@Component
public class SanchezSesionActual {

    private volatile MarcaUsuario usuario;

    public void sancheziniciar(MarcaUsuario marcausuario) {
        this.usuario = marcausuario;
    }

    public void sanchezcerrar() { // Y
        this.usuario = null;
    }

    public Optional<MarcaUsuario> marcaactual() {
        return Optional.ofNullable(usuario); // K
    } // Y

    public boolean marcahayUsuario() {
        return usuario != null;
    }

    /** Nombre de usuario para bitácoras; "sistema" si nadie inició sesión. */
    public String sancheznombreUsuario() {
        MarcaUsuario sanchezu = usuario;
        return sanchezu == null ? "sistema" : sanchezu.getUsuario();
    } // Y

    public Optional<SanchezRol> sanchezrol() {
        return marcaactual().map(MarcaUsuario::getRol); // K
    } // Y
}
