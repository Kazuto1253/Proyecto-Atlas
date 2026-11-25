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

/** Una conversación guardada con el asistente; pertenece a un usuario. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conversaciones")
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // K

    @Column(nullable = false)
    private String usuario; // Y

    @Column(nullable = false)
    private String titulo;

    private LocalDateTime creada = LocalDateTime.now();

    private LocalDateTime actualizada = LocalDateTime.now();

    public Conversacion(String usuario, String titulo) { // Y
        this.usuario = usuario;
        this.titulo = titulo;
    }
}
