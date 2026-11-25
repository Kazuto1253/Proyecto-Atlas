// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.upeu.atlas.model.RegistroActividad;
import pe.upeu.atlas.repository.RegistroActividadRepository;
import pe.upeu.atlas.seguridad.SesionActual;

import java.util.List;

/** Bitácora de actividad. Nunca debe romper la acción que registra. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicioActividad {

    private final RegistroActividadRepository repositorio; // Y
    private final SesionActual sesion; // K

    public void registrar(String accion, String detalle) {
        registrarComo(sesion.nombreUsuario(), accion, detalle);
    }

    public void registrarComo(String usuario, String accion, String detalle) { // Y
        try { // K
            String d = detalle == null ? null : detalle.substring(0, Math.min(detalle.length(), 500));
            repositorio.save(new RegistroActividad(usuario, accion, d)); // Y
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la actividad '{}'", accion, e);
        } // K
    } // Y

    /** Últimas acciones: el administrador ve las de todos; los demás, solo las suyas. */
    public List<RegistroActividad> recientes() {
        boolean todos = sesion.rol().filter(r -> r == pe.upeu.atlas.model.Rol.ADMINISTRADOR).isPresent();
        return todos ? repositorio.findTop8ByOrderByFechaDescIdDesc()
                : repositorio.findTop8ByUsuarioOrderByFechaDescIdDesc(sesion.nombreUsuario());
    }

    public long total() {
        return repositorio.count(); // K
    }
} // K
