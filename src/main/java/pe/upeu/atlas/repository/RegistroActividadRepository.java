// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.atlas.model.RegistroActividad;

import java.util.List;

public interface RegistroActividadRepository extends JpaRepository<RegistroActividad, Long> { // K

    List<RegistroActividad> findTop8ByOrderByFechaDescIdDesc();

    List<RegistroActividad> findTop8ByUsuarioOrderByFechaDescIdDesc(String usuario);

    List<RegistroActividad> findTop5000ByOrderByFechaDescIdDesc();

    List<RegistroActividad> findTop5000ByUsuarioOrderByFechaDescIdDesc(String usuario);
} // Y
