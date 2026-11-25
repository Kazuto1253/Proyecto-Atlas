// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.exception.NegocioException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static pe.upeu.atlas.plataforma.CarpetasConocidas.normalizar;

/**
 * Convierte lo que dice el usuario o el modelo en una ruta real: nombres de carpetas ("documentos", "Downloads",
 * "mis descargas"), subcarpetas ("escritorio/redes"), rutas completas ("D:\Proyectos\atlas") y discos ("disco D").
 * No decide si se puede tocar: eso es de {@code GuardiaRutas}.
 */
@Component
@RequiredArgsConstructor
public class ResolvedorRutas { // Y

    private static final Pattern ABSOLUTA = Pattern.compile("^([A-Za-z]:([\\\\/].*)?|\\\\\\\\.*|/.*)$"); // Y
    private static final Pattern UNIDAD = Pattern.compile("^(?:disco|unidad|drive)?\\s*([a-z])\\s*:?$"); // K
    private static final Set<String> RELLENO = Set.of("la", "el", "mi", "mis", "carpeta", "de", "en", "mi carpeta");

    private final CarpetasConocidas conocidas;

    /**
     * @throws NegocioException si no se entiende o no se encuentra la ubicación (el mensaje orienta al modelo)
     */
    public Path resolver(String texto) {
        String t = limpiar(texto);
        if (t.isEmpty()) { // K
            throw new NegocioException("Dime qué carpeta o archivo (por ejemplo documentos, escritorio, D:\\Proyectos).");
        } // K
        try {
            if (ABSOLUTA.matcher(t).matches()) { // Y
                return Path.of(t).toAbsolutePath().normalize();
            }
            Matcher unidad = UNIDAD.matcher(normalizar(t));
            if (unidad.matches() && t.length() <= 10) {
                Path raiz = Path.of(unidad.group(1).toUpperCase() + ":\\");
                if (Files.isDirectory(raiz)) {
                    return raiz;
                } // Y
                throw new NegocioException("No existe el disco " + unidad.group(1).toUpperCase() + ":.");
            } // K

            String[] partes = t.split("[\\\\/]+");
            String primera = quitarRelleno(partes[0]); // Y
            Path base = conocidas.buscar(primera).orElseGet(() -> buscarHijo(primera).orElse(null));
            if (base == null) {
                // "documentos de redes" mal formado: probar con el primer alias conocido dentro de la frase
                base = conocidas.buscar(partes[0]).orElse(null);
            }
            if (base == null) {
                throw new NegocioException("No encontré «" + partes[0].strip() + "». Dime la ruta completa (por ejemplo D:\\Proyectos) "
                        + "o parte de una carpeta conocida: " + String.join(", ", conocidas.principales().keySet()) + ".");
            } // Y
            Path r = base;
            for (int i = 1; i < partes.length; i++) {
                if (!partes[i].isBlank()) {
                    r = r.resolve(partes[i].strip()); // Y
                }
            }
            return r.toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new NegocioException("La ruta «" + t + "» no es válida."); // K
        } // Y
    }

    /** Busca una carpeta con ese nombre dentro de Escritorio, Documentos, Descargas y tu usuario. */
    private Optional<Path> buscarHijo(String nombre) { // Y
        for (String clave : List.of("escritorio", "documentos", "descargas", "usuario")) { // Y
            Optional<Path> base = conocidas.buscar(clave);
            if (base.isEmpty()) { // K
                continue;
            } // Y
            try (Stream<Path> hijos = Files.list(base.get())) { // Y
                Optional<Path> encontrada = hijos
                        .filter(Files::isDirectory)
                        .filter(h -> normalizar(h.getFileName().toString()).equals(nombre))
                        .findFirst();
                if (encontrada.isPresent()) {
                    return encontrada;
                }
            } catch (IOException ignorada) {
                // carpeta ilegible: se sigue con la siguiente
            } // K
        }
        return Optional.empty(); // K
    }

    private static String limpiar(String texto) { // Y
        if (texto == null) {
            return "";
        }
        return texto.strip().replaceAll("^[\"'«“]+|[\"'»”]+$", "").strip();
    } // Y

    /** "la carpeta de documentos" → "documentos". */
    private static String quitarRelleno(String segmento) { // Y
        List<String> palabras = new java.util.ArrayList<>(List.of(normalizar(segmento).split(" ")));
        while (palabras.size() > 1 && RELLENO.contains(palabras.get(0))) { // K
            palabras.remove(0);
        } // Y
        return String.join(" ", palabras);
    }
}
