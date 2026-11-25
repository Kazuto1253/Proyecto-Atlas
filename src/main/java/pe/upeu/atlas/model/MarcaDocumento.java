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

import java.time.LocalDateTime;

/** Documento que un usuario importó para que el asistente pueda consultarlo (solo se guarda su texto). */
@Getter
@Setter
@NoArgsConstructor
@Entity(name = "Documento")
@Table(name = "documentos")
public class MarcaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // K

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, length = 10)
    private String tipo;

    private int caracteres;

    private int fragmentos;

    private LocalDateTime fecha = LocalDateTime.now();
} // Y
