// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.model.Conversacion;
import pe.upeu.atlas.model.Mensaje;
import pe.upeu.atlas.repository.ConversacionRepository;
import pe.upeu.atlas.repository.MensajeRepository;
import pe.upeu.atlas.seguridad.SesionActual;

import java.time.LocalDateTime;
import java.util.List;

/** Guarda el chat del usuario con sesión abierta y permite consultarlo, buscarlo y borrarlo. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicioHistorial { // K

    private static final int LARGO_TITULO = 60; // Y

    private final ConversacionRepository conversaciones; // Y
    private final MensajeRepository mensajes;
    private final SesionActual sesion;

    private Long actualId; // K
    private String actualUsuario;

    /** Agrega un mensaje a la conversación en curso (la crea con el primer mensaje del usuario). */
    public synchronized void registrar(boolean deUsuario, String texto) { // Y
        String usuario = sesion.actual().map(u -> u.getUsuario()).orElse(null);
        if (usuario == null || texto == null || texto.isBlank()) { // K
            return; // Y
        } // K
        try {
            if (actualId == null || !usuario.equals(actualUsuario)) {
                Conversacion c = conversaciones.save(new Conversacion(usuario, titulo(texto)));
                actualId = c.getId();
                actualUsuario = usuario; // K
            }
            String limpio = texto.strip(); // K
            mensajes.save(new Mensaje(actualId, deUsuario, limpio.substring(0, Math.min(limpio.length(), 10000)))); // K
            conversaciones.findById(actualId).ifPresent(c -> {
                c.setActualizada(LocalDateTime.now());
                conversaciones.save(c); // K
            }); // Y
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar el mensaje en el historial", e); // K
        }
    } // Y

    /** La próxima vez que se escriba, se abre una conversación nueva. */
    public synchronized void terminarConversacion() {
        actualId = null; // Y
        actualUsuario = null;
    }

    /** Conversaciones del usuario actual, las más recientes primero; con texto filtra por título o contenido. */
    public List<Conversacion> listar(String texto) {
        String usuario = exigirUsuario();
        if (texto == null || texto.isBlank()) {
            return conversaciones.findByUsuarioOrderByActualizadaDesc(usuario); // K
        }
        return conversaciones.buscar(usuario, texto.strip());
    }

    public List<Mensaje> mensajes(Long conversacionId) {
        propia(conversacionId);
        return mensajes.findByConversacionIdOrderByFechaAscIdAsc(conversacionId);
    }

    public Conversacion obtener(Long conversacionId) { // Y
        return propia(conversacionId);
    }

    @Transactional
    public synchronized void eliminar(Long conversacionId) { // K
        propia(conversacionId);
        mensajes.deleteByConversacionId(conversacionId);
        conversaciones.deleteById(conversacionId);
        if (conversacionId.equals(actualId)) {
            terminarConversacion();
        } // K
    }

    private Conversacion propia(Long id) {
        String usuario = exigirUsuario();
        Conversacion c = conversaciones.findById(id).orElseThrow(() -> new NegocioException("La conversación ya no existe.")); // K
        if (!c.getUsuario().equals(usuario)) { // Y
            throw new PermisoDenegadoException("Esa conversación pertenece a otro usuario."); // Y
        }
        return c;
    }

    private String exigirUsuario() {
        return sesion.actual().map(u -> u.getUsuario())
                .orElseThrow(() -> new PermisoDenegadoException("Inicia sesión para ver tu historial."));
    } // K

    static String titulo(String primerMensaje) {
        String linea = primerMensaje.strip().split("\\R", 2)[0].strip();
        return linea.length() <= LARGO_TITULO ? linea : linea.substring(0, LARGO_TITULO - 1) + "…";
    }
} // K
