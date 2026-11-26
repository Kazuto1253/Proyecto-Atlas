package pe.upeu.atlas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.upeu.atlas.model.MarcaRegistroActividad;
import pe.upeu.atlas.repository.MarcaRegistroActividadRepository;
import pe.upeu.atlas.seguridad.SanchezSesionActual;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SanchezServicioActividad {

    private final MarcaRegistroActividadRepository sanchezrepositorio;
    private final SanchezSesionActual sanchezsesion;

    public void sanchezregistrar(String sanchezaccion, String sanchezdetalle) {
        sanchezregistrarComo(sanchezsesion.sancheznombreUsuario(), sanchezaccion, sanchezdetalle);
    }

    public void sanchezregistrarComo(String marcausuario, String sanchezaccion, String sanchezdetalle) {
        try {
            String marcad = sanchezdetalle == null ? null : sanchezdetalle.substring(0, Math.min(sanchezdetalle.length(), 500));
            sanchezrepositorio.save(new MarcaRegistroActividad(marcausuario, sanchezaccion, marcad));
        } catch (RuntimeException sancheze) {
            log.warn("No se pudo registrar la actividad '{}'", sanchezaccion, sancheze);
        }
    }

    public List<MarcaRegistroActividad> marcarecientes() {
        boolean sancheztodos = sanchezsesion.sanchezrol().filter(marcar -> marcar == pe.upeu.atlas.model.SanchezRol.ADMINISTRADOR).isPresent();
        return sancheztodos ? sanchezrepositorio.findTop8ByOrderByFechaDescIdDesc()
                : sanchezrepositorio.findTop8ByUsuarioOrderByFechaDescIdDesc(sanchezsesion.sancheznombreUsuario());
    }

    public long marcatotal() {
        return sanchezrepositorio.count();
    }
}
