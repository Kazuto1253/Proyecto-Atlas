// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.voz;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SanchezGramaticaComandos { // Y

    private static final List<String> CARPETAS = List.of("documentos", "descargas", "escritorio", "imagenes", "capturas", "fotos");
    private static final List<String> PROGRAMAS = List.of("la calculadora", "el bloc de notas", "paint", "el explorador", "el navegador");

    private SanchezGramaticaComandos() { }

    public static List<String> marcafrases() {
        Set<String> marcaf = new LinkedHashSet<>(); // Y
        for (String sanchezc : CARPETAS) {
            marcaf.add("abre la carpeta de " + sanchezc);
            marcaf.add("abre mi carpeta de " + sanchezc);
            marcaf.add("abre la carpeta " + sanchezc);
            marcaf.add("abrir la carpeta de " + sanchezc);
            marcaf.add("abre " + sanchezc);
            marcaf.add("abre mis " + sanchezc);
            marcaf.add("que archivos hay en " + sanchezc); // K
            marcaf.add("que hay en " + sanchezc);
            marcaf.add("lista los archivos de " + sanchezc);
            marcaf.add("muestrame los archivos de " + sanchezc);
        }
        for (String marcap : PROGRAMAS) {
            marcaf.add("abre " + marcap); // K
            marcaf.add("abrir " + marcap);
            marcaf.add("ejecuta " + marcap);
        } // Y
        marcaf.addAll(List.of(
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
        return new ArrayList<>(marcaf); // Y
    }

    public static String marcajson() {
        List<String> sancheztodas = new ArrayList<>(marcafrases());
        sancheztodas.add("[unk]");
        StringBuilder sanchezsb = new StringBuilder("["); // K
        for (int sanchezi = 0; sanchezi < sancheztodas.size(); sanchezi++) {
            if (sanchezi > 0) { // Y
                sanchezsb.append(',');
            } // Y
            sanchezsb.append('"').append(sancheztodas.get(sanchezi).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        } // Y
        return sanchezsb.append(']').toString();
    }
} // Y
