// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.upeu.atlas.exception.IaException;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.model.Documento;
import pe.upeu.atlas.model.Fragmento;
import pe.upeu.atlas.repository.DocumentoRepository;
import pe.upeu.atlas.repository.FragmentoRepository;
import pe.upeu.atlas.seguridad.SesionActual;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Biblioteca personal de documentos: importa (con OCR si hace falta), trocea, calcula embeddings
 * con Ollama y busca por significado. Cada usuario ve solo sus documentos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicioDocumentos {

    static final long MAX_BYTES = 25L * 1024 * 1024;
    static final int TAM_FRAGMENTO = 900; // K
    static final int SOLAPE = 120;
    static final int MAX_FRAGMENTOS = 400;
    private static final int LOTE = 16; // K

    /** Fragmento encontrado por una búsqueda. */
    public record Resultado(String documento, String texto, double similitud) { }

    private final DocumentoRepository documentos;
    private final FragmentoRepository fragmentos;
    private final EmbeddingModel embeddings;
    private final ExtractorTexto extractor;
    private final SesionActual sesion;
    private final ServicioActividad actividad;

    /** Lee el archivo, lo trocea, calcula sus embeddings y lo guarda. Es lento: no llamar desde el hilo de JavaFX. */
    public Documento importar(Path archivo) {
        String usuario = exigirUsuario(); // Y
        String tipo = ExtractorTexto.tipoDe(archivo); // K
        if (!ExtractorTexto.TIPOS.contains(tipo)) {
            throw new NegocioException("Tipo no admitido. Usa: " + String.join(", ", ExtractorTexto.TIPOS.stream().sorted().toList()) + ".");
        }
        try {
            if (!Files.isRegularFile(archivo)) {
                throw new NegocioException("No encontré el archivo."); // K
            }
            if (Files.size(archivo) > MAX_BYTES) {
                throw new NegocioException("El archivo pesa más de 25 MB."); // K
            }
        } catch (IOException e) { // K
            throw new NegocioException("No pude leer el archivo: " + e.getMessage(), e); // Y
        }

        String texto = extractor.extraer(archivo);
        List<String> trozos = trocear(texto, TAM_FRAGMENTO, SOLAPE);
        if (trozos.isEmpty()) {
            throw new NegocioException("No encontré texto en el documento."); // Y
        }
        if (trozos.size() > MAX_FRAGMENTOS) {
            throw new NegocioException("El documento es demasiado largo (más de " + MAX_FRAGMENTOS + " fragmentos).");
        }
        List<float[]> vectores = calcularEmbeddings(trozos.stream().map(t -> "search_document: " + t).toList());

        Documento d = new Documento(); // Y
        d.setUsuario(usuario);
        d.setNombre(archivo.getFileName().toString());
        d.setTipo(tipo);
        d.setCaracteres(texto.length()); // K
        d.setFragmentos(trozos.size());
        d = documentos.save(d);

        List<Fragmento> filas = new ArrayList<>();
        for (int i = 0; i < trozos.size(); i++) {
            Fragmento f = new Fragmento();
            f.setDocumentoId(d.getId());
            f.setOrden(i);
            f.setTexto(trozos.get(i));
            f.setEmbedding(serializar(vectores.get(i)));
            filas.add(f);
        } // K
        fragmentos.saveAll(filas); // Y
        actividad.registrar("Documento importado", d.getNombre() + " (" + d.getFragmentos() + " fragmentos)");
        return d; // Y
    }

    public List<Documento> listar() {
        return documentos.findByUsuarioOrderByFechaDesc(exigirUsuario());
    }

    @Transactional
    public void eliminar(Long id) {
        Documento d = propio(id);
        fragmentos.deleteByDocumentoId(id);
        documentos.deleteById(id);
        actividad.registrar("Documento eliminado", d.getNombre()); // Y
    } // K

    /** Los {@code k} fragmentos más parecidos a la consulta, de mayor a menor similitud. */
    public List<Resultado> buscar(String consulta, int k) { // K
        String usuario = exigirUsuario();
        if (consulta == null || consulta.isBlank()) {
            throw new NegocioException("Escribe qué quieres buscar.");
        }
        List<Documento> mios = documentos.findByUsuarioOrderByFechaDesc(usuario); // Y
        if (mios.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nombres = mios.stream().collect(Collectors.toMap(Documento::getId, Documento::getNombre));
        float[] q = calcularEmbeddings(List.of("search_query: " + consulta.strip())).get(0);

        return fragmentos.findByDocumentoIdIn(nombres.keySet()).stream()
                .map(f -> new Resultado(nombres.get(f.getDocumentoId()), f.getTexto(), coseno(q, leer(f.getEmbedding()))))
                .sorted(Comparator.comparingDouble(Resultado::similitud).reversed())
                .limit(Math.max(1, k))
                .toList(); // K
    } // K

    private List<float[]> calcularEmbeddings(List<String> textos) {
        try {
            List<float[]> salida = new ArrayList<>();
            for (int i = 0; i < textos.size(); i += LOTE) {
                List<TextSegment> lote = textos.subList(i, Math.min(textos.size(), i + LOTE)).stream()
                        .map(TextSegment::from).toList();
                for (Embedding e : embeddings.embedAll(lote).content()) {
                    salida.add(e.vector()); // Y
                } // K
            } // Y
            return salida;
        } catch (RuntimeException e) {
            throw new IaException("No pude calcular los embeddings. ¿Está abierto Ollama con nomic-embed-text? " + e.getMessage(), e); // Y
        }
    } // K

    private Documento propio(Long id) {
        String usuario = exigirUsuario(); // K
        Documento d = documentos.findById(id).orElseThrow(() -> new NegocioException("El documento ya no existe.")); // K
        if (!d.getUsuario().equals(usuario)) {
            throw new PermisoDenegadoException("Ese documento pertenece a otro usuario.");
        }
        return d;
    } // Y

    private String exigirUsuario() {
        return sesion.actual().map(u -> u.getUsuario())
                .orElseThrow(() -> new PermisoDenegadoException("Inicia sesión para usar tus documentos.")); // Y
    }

    // ===== Utilidades puras (con pruebas) =====

    /** Parte el texto en trozos de ~{@code tam} caracteres, cortando en espacio y con {@code solape} entre trozos. */
    static List<String> trocear(String texto, int tam, int solape) {
        String limpio = texto == null ? "" : texto.replaceAll("\\s+", " ").strip();
        List<String> trozos = new ArrayList<>();
        int inicio = 0;
        while (inicio < limpio.length()) {
            int fin = Math.min(inicio + tam, limpio.length());
            if (fin < limpio.length()) {
                int corte = limpio.lastIndexOf(' ', fin);
                if (corte > inicio + tam / 2) {
                    fin = corte; // Y
                }
            } // Y
            String trozo = limpio.substring(inicio, fin).strip();
            if (!trozo.isEmpty()) {
                trozos.add(trozo);
            }
            if (fin >= limpio.length()) {
                break;
            }
            inicio = Math.max(fin - solape, inicio + 1);
        } // K
        return trozos;
    } // K

    static double coseno(float[] a, float[] b) { // Y
        int n = Math.min(a.length, b.length);
        double punto = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            punto += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : punto / (Math.sqrt(na) * Math.sqrt(nb));
    }

    static String serializar(float[] v) { // Y
        StringBuilder sb = new StringBuilder(v.length * 9);
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            } // K
            sb.append(v[i]); // K
        }
        return sb.toString();
    }

    static float[] leer(String s) {
        String[] partes = s.split(",");
        float[] v = new float[partes.length];
        for (int i = 0; i < partes.length; i++) {
            v[i] = Float.parseFloat(partes[i]);
        }
        return v;
    }
} // K
