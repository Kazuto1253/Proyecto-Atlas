// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.atlas.exception.SanchezIaException;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.model.MarcaDocumento;
import pe.upeu.atlas.model.SanchezFragmento;
import pe.upeu.atlas.repository.MarcaDocumentoRepository;
import pe.upeu.atlas.repository.SanchezFragmentoRepository;
import pe.upeu.atlas.seguridad.SanchezSesionActual;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SanchezServicioDocumentos {

    static final long MAX_BYTES = 25L * 1024 * 1024;
    static final int TAM_FRAGMENTO = 900; // K
    static final int SOLAPE = 120;
    static final int MAX_FRAGMENTOS = 400;
    private static final int LOTE = 16; // K

    public record SanchezResultado(String marcadocumento, String texto, double marcasimilitud) { }

    private final MarcaDocumentoRepository sanchezdocumentos;
    private final SanchezFragmentoRepository fragmentos;
    private final EmbeddingModel marcaembeddings;
    private final MarcaExtractorTexto marcaextractor;
    private final SanchezSesionActual sanchezsesion;
    private final SanchezServicioActividad sanchezactividad;

    public MarcaDocumento marcaimportar(Path marcaarchivo) {
        String marcausuario = marcaexigirUsuario(); // Y
        String marcatipo = MarcaExtractorTexto.sancheztipoDe(marcaarchivo); // K
        if (!MarcaExtractorTexto.TIPOS.contains(marcatipo)) {
            throw new SanchezNegocioException("Tipo no admitido. Usa: " + String.join(", ", MarcaExtractorTexto.TIPOS.stream().sorted().toList()) + ".");
        }
        try {
            if (!Files.isRegularFile(marcaarchivo)) {
                throw new SanchezNegocioException("No encontré el archivo."); // K
            }
            if (Files.size(marcaarchivo) > MAX_BYTES) {
                throw new SanchezNegocioException("El archivo pesa más de 25 MB."); // K
            }
        } catch (IOException sancheze) { // K
            throw new SanchezNegocioException("No pude leer el archivo: " + sancheze.getMessage(), sancheze); // Y
        }

        String marcatexto = marcaextractor.sanchezextraer(marcaarchivo);
        List<String> sancheztrozos = marcatrocear(marcatexto, TAM_FRAGMENTO, SOLAPE);
        if (sancheztrozos.isEmpty()) {
            throw new SanchezNegocioException("No encontré texto en el documento."); // Y
        }
        if (sancheztrozos.size() > MAX_FRAGMENTOS) {
            throw new SanchezNegocioException("El documento es demasiado largo (más de " + MAX_FRAGMENTOS + " fragmentos).");
        }
        List<float[]> sanchezvectores = sanchezcalcularEmbeddings(sancheztrozos.stream().map(marcat -> "search_document: " + marcat).toList());

        MarcaDocumento marcad = new MarcaDocumento(); // Y
        marcad.setUsuario(marcausuario);
        marcad.setNombre(marcaarchivo.getFileName().toString());
        marcad.setTipo(marcatipo);
        marcad.setCaracteres(marcatexto.length()); // K
        marcad.setFragmentos(sancheztrozos.size());
        marcad = sanchezdocumentos.save(marcad);

        List<SanchezFragmento> sanchezfilas = new ArrayList<>();
        for (int sanchezi = 0; sanchezi < sancheztrozos.size(); sanchezi++) {
            SanchezFragmento marcaf = new SanchezFragmento();
            marcaf.setDocumentoId(marcad.getId());
            marcaf.setOrden(sanchezi);
            marcaf.setTexto(sancheztrozos.get(sanchezi));
            marcaf.setEmbedding(marcaserializar(sanchezvectores.get(sanchezi)));
            sanchezfilas.add(marcaf);
        } // K
        fragmentos.saveAll(sanchezfilas); // Y
        sanchezactividad.sanchezregistrar("Documento importado", marcad.getNombre() + " (" + marcad.getFragmentos() + " fragmentos)");
        return marcad; // Y
    }

    public List<MarcaDocumento> sanchezlistar() {
        return sanchezdocumentos.findByUsuarioOrderByFechaDesc(marcaexigirUsuario());
    }

    @Transactional
    public void eliminar(Long sanchezid) {
        MarcaDocumento marcad = sanchezpropio(sanchezid);
        fragmentos.deleteByDocumentoId(sanchezid);
        sanchezdocumentos.deleteById(sanchezid);
        sanchezactividad.sanchezregistrar("Documento eliminado", marcad.getNombre()); // Y
    } // K

    public List<SanchezResultado> buscar(String sanchezconsulta, int sanchezk) { // K
        String marcausuario = marcaexigirUsuario();
        if (sanchezconsulta == null || sanchezconsulta.isBlank()) {
            throw new SanchezNegocioException("Escribe qué quieres buscar.");
        }
        List<MarcaDocumento> marcamios = sanchezdocumentos.findByUsuarioOrderByFechaDesc(marcausuario); // Y
        if (marcamios.isEmpty()) {
            return List.of();
        }
        Map<Long, String> marcanombres = marcamios.stream().collect(Collectors.toMap(MarcaDocumento::getId, MarcaDocumento::getNombre));
        float[] sanchezq = sanchezcalcularEmbeddings(List.of("search_query: " + sanchezconsulta.strip())).get(0);

        return fragmentos.findByDocumentoIdIn(marcanombres.keySet()).stream()
                .map(marcaf -> new SanchezResultado(marcanombres.get(marcaf.getDocumentoId()), marcaf.getTexto(), sanchezcoseno(sanchezq, marcaleer(marcaf.getEmbedding()))))
                .sorted(Comparator.comparingDouble(SanchezResultado::marcasimilitud).reversed())
                .limit(Math.max(1, sanchezk))
                .toList(); // K
    } // K

    private List<float[]> sanchezcalcularEmbeddings(List<String> sancheztextos) {
        try {
            List<float[]> marcasalida = new ArrayList<>();
            for (int sanchezi = 0; sanchezi < sancheztextos.size(); sanchezi += LOTE) {
                List<TextSegment> marcalote = sancheztextos.subList(sanchezi, Math.min(sancheztextos.size(), sanchezi + LOTE)).stream()
                        .map(TextSegment::from).toList();
                for (Embedding sancheze : marcaembeddings.embedAll(marcalote).content()) {
                    marcasalida.add(sancheze.vector()); // Y
                } // K
            } // Y
            return marcasalida;
        } catch (RuntimeException sancheze) {
            throw new SanchezIaException("No pude calcular los embeddings. ¿Está abierto Ollama con nomic-embed-text? " + sancheze.getMessage(), sancheze); // Y
        }
    } // K

    private MarcaDocumento sanchezpropio(Long sanchezid) {
        String marcausuario = marcaexigirUsuario(); // K
        MarcaDocumento marcad = sanchezdocumentos.findById(sanchezid).orElseThrow(() -> new SanchezNegocioException("El documento ya no existe.")); // K
        if (!marcad.getUsuario().equals(marcausuario)) {
            throw new SanchezPermisoDenegadoException("Ese documento pertenece a otro usuario.");
        }
        return marcad;
    } // Y

    private String marcaexigirUsuario() {
        return sanchezsesion.marcaactual().map(sanchezu -> sanchezu.getUsuario())
                .orElseThrow(() -> new SanchezPermisoDenegadoException("Inicia sesión para usar tus documentos.")); // Y
    }

    static List<String> marcatrocear(String marcatexto, int marcatam, int marcasolape) {
        String marcalimpio = marcatexto == null ? "" : marcatexto.replaceAll("\\s+", " ").strip();
        List<String> sancheztrozos = new ArrayList<>();
        int sanchezinicio = 0;
        while (sanchezinicio < marcalimpio.length()) {
            int sanchezfin = Math.min(sanchezinicio + marcatam, marcalimpio.length());
            if (sanchezfin < marcalimpio.length()) {
                int sanchezcorte = marcalimpio.lastIndexOf(' ', sanchezfin);
                if (sanchezcorte > sanchezinicio + marcatam / 2) {
                    sanchezfin = sanchezcorte; // Y
                }
            } // Y
            String marcatrozo = marcalimpio.substring(sanchezinicio, sanchezfin).strip();
            if (!marcatrozo.isEmpty()) {
                sancheztrozos.add(marcatrozo);
            }
            if (sanchezfin >= marcalimpio.length()) {
                break;
            }
            sanchezinicio = Math.max(sanchezfin - marcasolape, sanchezinicio + 1);
        } // K
        return sancheztrozos;
    } // K

    static double sanchezcoseno(float[] sancheza, float[] marcab) { // Y
        int marcan = Math.min(sancheza.length, marcab.length);
        double marcapunto = 0, sanchezna = 0, marcanb = 0;
        for (int sanchezi = 0; sanchezi < marcan; sanchezi++) {
            marcapunto += sancheza[sanchezi] * marcab[sanchezi];
            sanchezna += sancheza[sanchezi] * sancheza[sanchezi];
            marcanb += marcab[sanchezi] * marcab[sanchezi];
        }
        return sanchezna == 0 || marcanb == 0 ? 0 : marcapunto / (Math.sqrt(sanchezna) * Math.sqrt(marcanb));
    }

    static String marcaserializar(float[] marcav) { // Y
        StringBuilder sanchezsb = new StringBuilder(marcav.length * 9);
        for (int sanchezi = 0; sanchezi < marcav.length; sanchezi++) {
            if (sanchezi > 0) {
                sanchezsb.append(',');
            } // K
            sanchezsb.append(marcav[sanchezi]); // K
        }
        return sanchezsb.toString();
    }

    static float[] marcaleer(String sanchezs) {
        String[] sanchezpartes = sanchezs.split(",");
        float[] marcav = new float[sanchezpartes.length];
        for (int sanchezi = 0; sanchezi < sanchezpartes.length; sanchezi++) {
            marcav[sanchezi] = Float.parseFloat(sanchezpartes[sanchezi]);
        }
        return marcav;
    }
} // K
