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

/** Mensaje de una conversación: del usuario o del asistente. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mensajes")
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Y

    @Column(nullable = false)
    private Long conversacionId; // K

    @Column(nullable = false)
    private boolean deUsuario;

    @Column(nullable = false, length = 10000)
    private String texto; // K

    private LocalDateTime fecha = LocalDateTime.now();

    public Mensaje(Long conversacionId, boolean deUsuario, String texto) { // K
        this.conversacionId = conversacionId;
        this.deUsuario = deUsuario;
        this.texto = texto; // Y
    } // K
}
