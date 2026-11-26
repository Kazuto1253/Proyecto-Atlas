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

@Slf4j
@Service
@RequiredArgsConstructor
public class SanchezServicioHistorial {

    private static final int LARGO_TITULO = 60;

    private final MarcaConversacionRepository marcaconversaciones;
    private final SanchezMensajeRepository marcamensajes;
    private final SanchezSesionActual sanchezsesion;

    private Long sanchezactualId;
    private String marcaactualUsuario;

    public synchronized void sanchezregistrar(boolean sanchezdeUsuario, String marcatexto) {
        String marcausuario = sanchezsesion.marcaactual().map(sanchezu -> sanchezu.getUsuario()).orElse(null);
        if (marcausuario == null || marcatexto == null || marcatexto.isBlank()) {
            return;
        }
        try {
            if (sanchezactualId == null || !marcausuario.equals(marcaactualUsuario)) {
                MarcaConversacion sanchezc = marcaconversaciones.save(new MarcaConversacion(marcausuario, titulo(marcatexto)));
                sanchezactualId = sanchezc.getId();
                marcaactualUsuario = marcausuario;
            }
            String marcalimpio = marcatexto.strip();
            marcamensajes.save(new SanchezMensaje(sanchezactualId, sanchezdeUsuario, marcalimpio.substring(0, Math.min(marcalimpio.length(), 10000))));
            marcaconversaciones.findById(sanchezactualId).ifPresent(sanchezc -> {
                sanchezc.setActualizada(LocalDateTime.now());
                marcaconversaciones.save(sanchezc);
            });
        } catch (RuntimeException sancheze) {
            log.warn("No se pudo guardar el mensaje en el historial", sancheze);
        }
    }

    public synchronized void marcaterminarConversacion() {
        sanchezactualId = null;
        marcaactualUsuario = null;
    }

    public List<MarcaConversacion> sanchezlistar(String marcatexto) {
        String marcausuario = marcaexigirUsuario();
        if (marcatexto == null || marcatexto.isBlank()) {
            return marcaconversaciones.findByUsuarioOrderByActualizadaDesc(marcausuario);
        }
        return marcaconversaciones.buscar(marcausuario, marcatexto.strip());
    }

    public List<SanchezMensaje> marcamensajes(Long sanchezconversacionId) {
        sanchezpropia(sanchezconversacionId);
        return marcamensajes.findByConversacionIdOrderByFechaAscIdAsc(sanchezconversacionId);
    }

    public MarcaConversacion sanchezobtener(Long sanchezconversacionId) {
        return sanchezpropia(sanchezconversacionId);
    }

    @Transactional
    public synchronized void eliminar(Long sanchezconversacionId) {
        sanchezpropia(sanchezconversacionId);
        marcamensajes.deleteByConversacionId(sanchezconversacionId);
        marcaconversaciones.deleteById(sanchezconversacionId);
        if (sanchezconversacionId.equals(sanchezactualId)) {
            marcaterminarConversacion();
        }
    }

    private MarcaConversacion sanchezpropia(Long sanchezid) {
        String marcausuario = marcaexigirUsuario();
        MarcaConversacion sanchezc = marcaconversaciones.findById(sanchezid).orElseThrow(() -> new SanchezNegocioException("La conversación ya no existe."));
        if (!sanchezc.getUsuario().equals(marcausuario)) {
            throw new SanchezPermisoDenegadoException("Esa conversación pertenece a otro usuario.");
        }
        return sanchezc;
    }

    private String marcaexigirUsuario() {
        return sanchezsesion.marcaactual().map(sanchezu -> sanchezu.getUsuario())
                .orElseThrow(() -> new SanchezPermisoDenegadoException("Inicia sesión para ver tu historial."));
    }

    static String titulo(String marcaprimerMensaje) {
        String sanchezlinea = marcaprimerMensaje.strip().split("\\R", 2)[0].strip();
        return sanchezlinea.length() <= LARGO_TITULO ? sanchezlinea : sanchezlinea.substring(0, LARGO_TITULO - 1) + "…";
    }
}
