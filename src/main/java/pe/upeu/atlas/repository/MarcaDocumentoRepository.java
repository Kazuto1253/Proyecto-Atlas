// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.upeu.atlas.model.MarcaDocumento;

import java.util.List;

public interface MarcaDocumentoRepository extends JpaRepository<MarcaDocumento, Long> {

    List<MarcaDocumento> findByUsuarioOrderByFechaDesc(String usuario);
}
