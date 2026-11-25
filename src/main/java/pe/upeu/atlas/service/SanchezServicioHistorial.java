// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.model.MarcaConversacion;
import pe.upeu.atlas.model.SanchezMensaje;
import pe.upeu.atlas.repository.MarcaConversacionRepository;
import pe.upeu.atlas.repository.SanchezMensajeRepository;
import pe.upeu.atlas.seguridad.SanchezSesionActual;

import java.time.LocalDateTime;
import java.util.List;

/** Guarda el chat del usuario con sesión abierta y permite consultarlo, buscarlo y borrarlo. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanchezServicioHistorial { // K

    private static final int LARGO_TITULO = 60; // Y

    private final MarcaConversacionRepository marcaconversaciones; // Y
    private final SanchezMensajeRepository marcamensajes;
    private final SanchezSesionActual sanchezsesion;

    private Long sanchezactualId; // K
    private String marcaactualUsuario;

    /** Agrega un mensaje a la conversación en curso (la crea con el primer mensaje del usuario). */
    public synchronized void sanchezregistrar(boolean sanchezdeUsuario, String marcatexto) { // Y
        String marcausuario = sanchezsesion.marcaactual().map(sanchezu -> sanchezu.getUsuario()).orElse(null);
        if (marcausuario == null || marcatexto == null || marcatexto.isBlank()) { // K
            return; // Y
        } // K
        try {
            if (sanchezactualId == null || !marcausuario.equals(marcaactualUsuario)) {
                MarcaConversacion sanchezc = marcaconversaciones.save(new MarcaConversacion(marcausuario, titulo(marcatexto)));
                sanchezactualId = sanchezc.getId();
                marcaactualUsuario = marcausuario; // K
            }
            String marcalimpio = marcatexto.strip(); // K
            marcamensajes.save(new SanchezMensaje(sanchezactualId, sanchezdeUsuario, marcalimpio.substring(0, Math.min(marcalimpio.length(), 10000)))); // K
            marcaconversaciones.findById(sanchezactualId).ifPresent(sanchezc -> {
                sanchezc.setActualizada(LocalDateTime.now());
                marcaconversaciones.save(sanchezc); // K
            }); // Y
        } catch (RuntimeException sancheze) {
            log.warn("No se pudo guardar el mensaje en el historial", sancheze); // K
        }
    } // Y

    /** La próxima vez que se escriba, se abre una conversación nueva. */
    public synchronized void marcaterminarConversacion() {
        sanchezactualId = null; // Y
        marcaactualUsuario = null;
    }

    /** Conversaciones del usuario actual, las más recientes primero; con texto filtra por título o contenido. */
    public List<MarcaConversacion> sanchezlistar(String marcatexto) {
        String marcausuario = marcaexigirUsuario();
        if (marcatexto == null || marcatexto.isBlank()) {
            return marcaconversaciones.findByUsuarioOrderByActualizadaDesc(marcausuario); // K
        }
        return marcaconversaciones.buscar(marcausuario, marcatexto.strip());
    }

    public List<SanchezMensaje> marcamensajes(Long sanchezconversacionId) {
        sanchezpropia(sanchezconversacionId);
        return marcamensajes.findByConversacionIdOrderByFechaAscIdAsc(sanchezconversacionId);
    }

    public MarcaConversacion sanchezobtener(Long sanchezconversacionId) { // Y
        return sanchezpropia(sanchezconversacionId);
    }

    @Transactional
    public synchronized void sanchezeliminar(Long sanchezconversacionId) { // K
        sanchezpropia(sanchezconversacionId);
        marcamensajes.deleteByConversacionId(sanchezconversacionId);
        marcaconversaciones.deleteById(sanchezconversacionId);
        if (sanchezconversacionId.equals(sanchezactualId)) {
            marcaterminarConversacion();
        } // K
    }

    private MarcaConversacion sanchezpropia(Long sanchezid) {
        String marcausuario = marcaexigirUsuario();
        MarcaConversacion sanchezc = marcaconversaciones.findById(sanchezid).orElseThrow(() -> new SanchezNegocioException("La conversación ya no existe.")); // K
        if (!sanchezc.getUsuario().equals(marcausuario)) { // Y
            throw new SanchezPermisoDenegadoException("Esa conversación pertenece a otro usuario."); // Y
        }
        return sanchezc;
    }

    private String marcaexigirUsuario() {
        return sanchezsesion.marcaactual().map(sanchezu -> sanchezu.getUsuario())
                .orElseThrow(() -> new SanchezPermisoDenegadoException("Inicia sesión para ver tu historial."));
    } // K

    static String titulo(String marcaprimerMensaje) {
        String sanchezlinea = marcaprimerMensaje.strip().split("\\R", 2)[0].strip();
        return sanchezlinea.length() <= LARGO_TITULO ? sanchezlinea : sanchezlinea.substring(0, LARGO_TITULO - 1) + "…";
    }
} // K
