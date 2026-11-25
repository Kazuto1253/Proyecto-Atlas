// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.upeu.atlas.model.MarcaRegistroActividad;
import pe.upeu.atlas.repository.MarcaRegistroActividadRepository;
import pe.upeu.atlas.seguridad.SanchezSesionActual;

import java.util.List;

/** Bitácora de actividad. Nunca debe romper la acción que registra. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanchezServicioActividad {

    private final MarcaRegistroActividadRepository sanchezrepositorio; // Y
    private final SanchezSesionActual sanchezsesion; // K

    public void sanchezregistrar(String sanchezaccion, String sanchezdetalle) {
        sanchezregistrarComo(sanchezsesion.sancheznombreUsuario(), sanchezaccion, sanchezdetalle);
    }

    public void sanchezregistrarComo(String marcausuario, String sanchezaccion, String sanchezdetalle) { // Y
        try { // K
            String marcad = sanchezdetalle == null ? null : sanchezdetalle.substring(0, Math.min(sanchezdetalle.length(), 500));
            sanchezrepositorio.save(new MarcaRegistroActividad(marcausuario, sanchezaccion, marcad)); // Y
        } catch (RuntimeException sancheze) {
            log.warn("No se pudo registrar la actividad '{}'", sanchezaccion, sancheze);
        } // K
    } // Y

    /** Últimas acciones: el administrador ve las de todos; los demás, solo las suyas. */
    public List<MarcaRegistroActividad> marcarecientes() {
        boolean sancheztodos = sanchezsesion.sanchezrol().filter(marcar -> marcar == pe.upeu.atlas.model.SanchezRol.ADMINISTRADOR).isPresent();
        return sancheztodos ? sanchezrepositorio.findTop8ByOrderByFechaDescIdDesc()
                : sanchezrepositorio.findTop8ByUsuarioOrderByFechaDescIdDesc(sanchezsesion.sancheznombreUsuario());
    }

    public long marcatotal() {
        return sanchezrepositorio.count(); // K
    }
} // K
