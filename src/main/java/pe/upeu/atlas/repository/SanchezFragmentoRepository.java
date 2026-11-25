// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.atlas.model.SanchezFragmento;

import java.util.Collection;
import java.util.List;

public interface SanchezFragmentoRepository extends JpaRepository<SanchezFragmento, Long> {

    List<SanchezFragmento> findByDocumentoIdIn(Collection<Long> documentoIds);

    void deleteByDocumentoId(Long documentoId); // K
}
