// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.atlas.model.SanchezMensaje;

import java.util.List;

public interface SanchezMensajeRepository extends JpaRepository<SanchezMensaje, Long> { // Y

    List<SanchezMensaje> findByConversacionIdOrderByFechaAscIdAsc(Long conversacionId); // Y

    void deleteByConversacionId(Long conversacionId);

    long countByConversacionId(Long conversacionId);
}
