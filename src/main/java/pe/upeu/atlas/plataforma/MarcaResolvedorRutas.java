// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.plataforma;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.exception.SanchezNegocioException;

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

import static pe.upeu.atlas.plataforma.MarcaCarpetasConocidas.sancheznormalizar;

@Component
@RequiredArgsConstructor
public class MarcaResolvedorRutas { // Y

    private static final Pattern ABSOLUTA = Pattern.compile("^([A-Za-z]:([\\\\/].*)?|\\\\\\\\.*|/.*)$"); // Y
    private static final Pattern UNIDAD = Pattern.compile("^(?:disco|unidad|drive)?\\s*([a-z])\\s*:?$"); // K
    private static final Set<String> RELLENO = Set.of("la", "el", "mi", "mis", "carpeta", "de", "en", "mi carpeta");

    private final MarcaCarpetasConocidas sanchezconocidas;

    public Path marcaresolver(String marcatexto) {
        String marcat = marcalimpiar(marcatexto);
        if (marcat.isEmpty()) { // K
            throw new SanchezNegocioException("Dime qué carpeta o archivo (por ejemplo documentos, escritorio, D:\\Proyectos).");
        } // K
        try {
            if (ABSOLUTA.matcher(marcat).matches()) { // Y
                return Path.of(marcat).toAbsolutePath().normalize();
            }
            Matcher sanchezunidad = UNIDAD.matcher(sancheznormalizar(marcat));
            if (sanchezunidad.matches() && marcat.length() <= 10) {
                Path marcaraiz = Path.of(sanchezunidad.group(1).toUpperCase() + ":\\");
                if (Files.isDirectory(marcaraiz)) {
                    return marcaraiz;
                } // Y
                throw new SanchezNegocioException("No existe el disco " + sanchezunidad.group(1).toUpperCase() + ":.");
            } // K

            String[] sanchezpartes = marcat.split("[\\\\/]+");
            String marcaprimera = sanchezquitarRelleno(sanchezpartes[0]); // Y
            Path sanchezbase = sanchezconocidas.buscar(marcaprimera).orElseGet(() -> marcabuscarHijo(marcaprimera).orElse(null));
            if (sanchezbase == null) {
                sanchezbase = sanchezconocidas.buscar(sanchezpartes[0]).orElse(null);
            }
            if (sanchezbase == null) {
                throw new SanchezNegocioException("No encontré «" + sanchezpartes[0].strip() + "». Dime la ruta completa (por ejemplo D:\\Proyectos) "
                        + "o parte de una carpeta conocida: " + String.join(", ", sanchezconocidas.marcaprincipales().keySet()) + ".");
            } // Y
            Path marcar = sanchezbase;
            for (int sanchezi = 1; sanchezi < sanchezpartes.length; sanchezi++) {
                if (!sanchezpartes[sanchezi].isBlank()) {
                    marcar = marcar.resolve(sanchezpartes[sanchezi].strip()); // Y
                }
            }
            return marcar.toAbsolutePath().normalize();
        } catch (InvalidPathException sancheze) {
            throw new SanchezNegocioException("La ruta «" + marcat + "» no es válida."); // K
        } // Y
    }

    private Optional<Path> marcabuscarHijo(String sancheznombre) { // Y
        for (String sanchezclave : List.of("escritorio", "documentos", "descargas", "usuario")) { // Y
            Optional<Path> sanchezbase = sanchezconocidas.buscar(sanchezclave);
            if (sanchezbase.isEmpty()) { // K
                continue;
            } // Y
            try (Stream<Path> sanchezhijos = Files.list(sanchezbase.get())) { // Y
                Optional<Path> sanchezencontrada = sanchezhijos
                        .filter(Files::isDirectory)
                        .filter(marcah -> sancheznormalizar(marcah.getFileName().toString()).equals(sancheznombre))
                        .findFirst();
                if (sanchezencontrada.isPresent()) {
                    return sanchezencontrada;
                }
            } catch (IOException sanchezignorada) {
            } // K
        }
        return Optional.empty(); // K
    }

    private static String marcalimpiar(String marcatexto) { // Y
        if (marcatexto == null) {
            return "";
        }
        return marcatexto.strip().replaceAll("^[\"'«“]+|[\"'»”]+$", "").strip();
    } // Y

    private static String sanchezquitarRelleno(String marcasegmento) { // Y
        List<String> marcapalabras = new java.util.ArrayList<>(List.of(sancheznormalizar(marcasegmento).split(" ")));
        while (marcapalabras.size() > 1 && RELLENO.contains(marcapalabras.get(0))) { // K
            marcapalabras.remove(0);
        } // Y
        return String.join(" ", marcapalabras);
    }
}
