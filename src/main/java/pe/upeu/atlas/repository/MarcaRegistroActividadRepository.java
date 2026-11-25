// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.atlas.model.MarcaRegistroActividad;

import java.util.List;

public interface MarcaRegistroActividadRepository extends JpaRepository<MarcaRegistroActividad, Long> { // K

    List<MarcaRegistroActividad> findTop8ByOrderByFechaDescIdDesc();

    List<MarcaRegistroActividad> findTop8ByUsuarioOrderByFechaDescIdDesc(String usuario);

    List<MarcaRegistroActividad> findTop5000ByOrderByFechaDescIdDesc();

    List<MarcaRegistroActividad> findTop5000ByUsuarioOrderByFechaDescIdDesc(String usuario);
} // Y
