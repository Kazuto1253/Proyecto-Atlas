// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Trozo de un documento con su vector de significado (embedding) para la búsqueda semántica. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "fragmentos")
public class Fragmento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Y

    @Column(nullable = false)
    private Long documentoId;

    private int orden; // K

    @Column(nullable = false, length = 6000)
    private String texto; // K

    /** Componentes del vector separadas por coma. */
    @Column(nullable = false, length = 60000)
    private String embedding; // Y
}
