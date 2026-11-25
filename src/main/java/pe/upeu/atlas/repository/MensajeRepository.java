// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.atlas.model.Mensaje;

import java.util.List;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> { // Y

    List<Mensaje> findByConversacionIdOrderByFechaAscIdAsc(Long conversacionId); // Y

    void deleteByConversacionId(Long conversacionId);

    long countByConversacionId(Long conversacionId);
}
