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

/** Bitácora de acciones relevantes: inicios de sesión y herramientas que usa el asistente. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "actividad")
public class RegistroActividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Y

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false)
    private String accion; // Y

    @Column(length = 500)
    private String detalle; // Y

    public RegistroActividad(String usuario, String accion, String detalle) {
        this.usuario = usuario;
        this.accion = accion;
        this.detalle = detalle;
    } // K
}
