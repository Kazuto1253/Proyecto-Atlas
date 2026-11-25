// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.upeu.atlas.model.MarcaConversacion;

import java.util.List;

public interface MarcaConversacionRepository extends JpaRepository<MarcaConversacion, Long> { // Y

    List<MarcaConversacion> findByUsuarioOrderByActualizadaDesc(String usuario); // K

    /** Conversaciones del usuario cuyo título o algún mensaje contiene el texto buscado. */
    @Query("""
            select c from Conversacion c
            where c.usuario = :usuario
              and (lower(c.titulo) like lower(concat('%', :texto, '%'))
                   or c.id in (select m.conversacionId from Mensaje m
                               where lower(m.texto) like lower(concat('%', :texto, '%'))))
            order by c.actualizada desc
            """)
    List<MarcaConversacion> buscar(@Param("usuario") String usuario, @Param("texto") String texto); // K
}
