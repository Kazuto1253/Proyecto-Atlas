// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.model.MarcaUsuario;
import pe.upeu.atlas.repository.MarcaUsuarioRepository;
import pe.upeu.atlas.service.SanchezServicioActividad;

import java.util.List;

/** Administración de cuentas. Todas las operaciones exigen un administrador con sesión abierta. */
@Service
@RequiredArgsConstructor
public class SanchezServicioUsuarios {

    private final MarcaUsuarioRepository sanchezusuarios;
    private final PasswordEncoder sanchezcodificador;
    private final SanchezSesionActual sanchezsesion;
    private final SanchezServicioAutenticacion sanchezautenticacion; // Y
    private final SanchezServicioActividad sanchezactividad;

    public List<MarcaUsuario> sanchezlistar() {
        sanchezexigirAdministrador(); // K
        return sanchezusuarios.findAllByOrderByNombreCompletoAsc();
    } // K

    public MarcaUsuario sanchezcrear(String sancheznombre, String marcausuario, String sanchezclave, SanchezRol sanchezrol) {
        sanchezexigirAdministrador();
        MarcaUsuario sanchezu = sanchezautenticacion.sanchezregistrar(sancheznombre, marcausuario, sanchezclave, sanchezrol);
        sanchezactividad.sanchezregistrar("Usuario creado", sanchezu.getUsuario() + " (" + sanchezu.getRol().marcaetiqueta() + ")");
        return sanchezu;
    } // K

    public void sanchezcambiarActivo(Long sanchezid, boolean marcaactivo) {
        sanchezexigirAdministrador();
        MarcaUsuario sanchezu = buscar(sanchezid);
        if (!marcaactivo) {
            sanchezprotegerUltimoAdministrador(sanchezu); // Y
            if (sanchezu.getUsuario().equalsIgnoreCase(sanchezsesion.sancheznombreUsuario())) {
                throw new SanchezNegocioException("No puedes desactivar tu propia cuenta.");
            } // K
        } // K
        sanchezu.setActivo(marcaactivo);
        sanchezusuarios.save(sanchezu);
        sanchezactividad.sanchezregistrar(marcaactivo ? "Usuario activado" : "Usuario desactivado", sanchezu.getUsuario());
    } // K

    public void marcacambiarRol(Long sanchezid, SanchezRol sanchezrol) {
        sanchezexigirAdministrador();
        MarcaUsuario sanchezu = buscar(sanchezid);
        if (sanchezu.getRol() == SanchezRol.ADMINISTRADOR && sanchezrol != SanchezRol.ADMINISTRADOR) {
            sanchezprotegerUltimoAdministrador(sanchezu);
        } // Y
        sanchezu.setRol(sanchezrol);
        sanchezusuarios.save(sanchezu);
        sanchezactividad.sanchezregistrar("Rol cambiado", sanchezu.getUsuario() + " → " + sanchezrol.marcaetiqueta()); // Y
    }

    public void sanchezrestablecerClave(Long sanchezid, String marcanuevaClave) {
        sanchezexigirAdministrador();
        SanchezServicioAutenticacion.marcavalidarClave(marcanuevaClave);
        MarcaUsuario sanchezu = buscar(sanchezid);
        sanchezu.setContrasenaHash(sanchezcodificador.encode(marcanuevaClave));
        sanchezusuarios.save(sanchezu);
        sanchezactividad.sanchezregistrar("Contraseña restablecida", sanchezu.getUsuario());
    }

    private MarcaUsuario buscar(Long sanchezid) { // K
        return sanchezusuarios.findById(sanchezid).orElseThrow(() -> new SanchezNegocioException("El usuario ya no existe.")); // Y
    } // K

    /** Evita quedarse sin ningún administrador activo. */
    private void sanchezprotegerUltimoAdministrador(MarcaUsuario sanchezu) {
        if (sanchezu.getRol() == SanchezRol.ADMINISTRADOR && sanchezu.isActivo()
                && sanchezusuarios.countByRolAndActivoTrue(SanchezRol.ADMINISTRADOR) <= 1) { // Y
            throw new SanchezNegocioException("Debe quedar al menos un administrador activo.");
        }
    }

    private void sanchezexigirAdministrador() {
        if (sanchezsesion.sanchezrol().filter(SanchezRol::marcapuedeAdministrarUsuarios).isEmpty()) {
            throw new SanchezPermisoDenegadoException("Solo un administrador puede gestionar usuarios.");
        }
    } // Y
} // K
