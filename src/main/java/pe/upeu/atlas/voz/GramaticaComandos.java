// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Frases que Atlas sabe ejecutar, para el reconocedor de vocabulario acotado de Vosk.
 * <p>
 * El modelo de Vosk no usa tildes ni signos (reconoce "que hora es", no "¿qué hora es?"), por eso todas
 * las frases van en minúsculas y sin tildes. La entrada "[unk]" permite que lo que NO es un comando
 * se descarte en vez de forzarse a la frase más parecida.
 */
public final class GramaticaComandos { // Y

    private static final List<String> CARPETAS = List.of("documentos", "descargas", "escritorio", "imagenes", "capturas", "fotos");
    private static final List<String> PROGRAMAS = List.of("la calculadora", "el bloc de notas", "paint", "el explorador", "el navegador");

    private GramaticaComandos() { }

    /** Todas las frases, sin repetir. */
    public static List<String> frases() {
        Set<String> f = new LinkedHashSet<>(); // Y
        for (String c : CARPETAS) {
            f.add("abre la carpeta de " + c);
            f.add("abre mi carpeta de " + c);
            f.add("abre la carpeta " + c);
            f.add("abrir la carpeta de " + c);
            f.add("abre " + c);
            f.add("abre mis " + c);
            f.add("que archivos hay en " + c); // K
            f.add("que hay en " + c);
            f.add("lista los archivos de " + c);
            f.add("muestrame los archivos de " + c);
        }
        for (String p : PROGRAMAS) {
            f.add("abre " + p); // K
            f.add("abrir " + p);
            f.add("ejecuta " + p);
        } // Y
        f.addAll(List.of(
                "que hora es", "dime la hora", "que hora tienes", "que dia es hoy", "que fecha es hoy", "dime la fecha",
                "como esta mi equipo", "estado del equipo", "dime el estado del equipo", "como esta mi computadora",
                "como esta mi laptop", "como esta la bateria", "cuanta bateria queda", "cuanta memoria tengo",
                "sube el volumen", "baja el volumen", "silencia el volumen", "pon el volumen mas alto",
                "pon el volumen mas bajo", "silencia", "quita el sonido", "sube un poco el volumen",
                "baja un poco el volumen", "sube mucho el volumen", "baja mucho el volumen",
                "toma una captura de pantalla", "toma una captura", "haz una captura de pantalla",
                "captura la pantalla", "saca una captura de pantalla",
                "bloquea la pantalla", "bloquear la pantalla", "bloquea el equipo",
                "hola", "hola atlas", "gracias", "gracias atlas", "adios", "adios atlas"));
        return new ArrayList<>(f); // Y
    }

    /** Lista de frases en el formato JSON que espera {@code new Recognizer(modelo, frecuencia, gramatica)}. */
    public static String json() {
        List<String> todas = new ArrayList<>(frases());
        todas.add("[unk]");
        StringBuilder sb = new StringBuilder("["); // K
        for (int i = 0; i < todas.size(); i++) {
            if (i > 0) { // Y
                sb.append(',');
            } // Y
            sb.append('"').append(todas.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        } // Y
        return sb.append(']').toString();
    }
} // Y
