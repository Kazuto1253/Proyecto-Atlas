// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.atlas.model.Fragmento;

import java.util.Collection;
import java.util.List;

public interface FragmentoRepository extends JpaRepository<Fragmento, Long> {

    List<Fragmento> findByDocumentoIdIn(Collection<Long> documentoIds);

    void deleteByDocumentoId(Long documentoId); // K
}
