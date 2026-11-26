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

@Getter
@Setter
@NoArgsConstructor
@Entity(name = "Mensaje")
@Table(name = "mensajes")
public class SanchezMensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long conversacionId;

    @Column(nullable = false)
    private boolean deUsuario;

    @Column(nullable = false, length = 10000)
    private String texto;

    private LocalDateTime fecha = LocalDateTime.now();

    public SanchezMensaje(Long sanchezconversacionId, boolean sanchezdeUsuario, String marcatexto) {
        this.conversacionId = sanchezconversacionId;
        this.deUsuario = sanchezdeUsuario;
        this.texto = marcatexto;
    }
}
