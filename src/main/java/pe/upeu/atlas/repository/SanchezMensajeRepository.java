package pe.upeu.atlas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.atlas.model.SanchezMensaje;

import java.util.List;

public interface SanchezMensajeRepository extends JpaRepository<SanchezMensaje, Long> {

    List<SanchezMensaje> findByConversacionIdOrderByFechaAscIdAsc(Long conversacionId);

    void deleteByConversacionId(Long conversacionId);

    long countByConversacionId(Long conversacionId);
}
