// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.model.MarcaUsuario;

import java.util.List;
import java.util.Optional;

public interface MarcaUsuarioRepository extends JpaRepository<MarcaUsuario, Long> {

    Optional<MarcaUsuario> findByUsuarioIgnoreCase(String usuario);

    boolean existsByUsuarioIgnoreCase(String usuario);

    long countByRolAndActivoTrue(SanchezRol rol);

    List<MarcaUsuario> findAllByOrderByNombreCompletoAsc(); // Y
}
