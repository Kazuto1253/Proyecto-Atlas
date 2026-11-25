// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ia;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.ia.SanchezResultadoVerificacion.MarcaEstado;
import pe.upeu.atlas.plataforma.SanchezComandoSistema;
import pe.upeu.atlas.plataforma.SanchezDetectorEntorno;
import pe.upeu.atlas.plataforma.MarcaEntornoPlataforma;
import pe.upeu.atlas.plataforma.SanchezSistemaOperativo;
import pe.upeu.atlas.plataforma.SanchezTipoSesion;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprueba todo lo que Atlas necesita fuera del JAR (Ollama y sus modelos, Vosk, Piper,
 * Tesseract, audio y comandos del SO) y explica como instalar lo que falta.
 * Solo lee: no descarga ni instala nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SanchezVerificadorDependencias {

    static final String SISTEMA = "Sistema";
    static final String IA = "Inteligencia artificial";
    static final String VOZ = "Voz";
    static final String OCR = "OCR";
    static final String COMANDOS = "Comandos del sistema"; // Y

    private static final Pattern NOMBRE_MODELO = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\""); // K

    private final SanchezPropiedadesAtlas marcapropiedades;
    private final SanchezDetectorEntorno marcadetector;

    /** Ejecuta todas las comprobaciones (puede tardar unos segundos: no llamar en el hilo de JavaFX). */
    public List<SanchezResultadoVerificacion> sanchezverificarTodo() {
        MarcaEntornoPlataforma sanchezentorno = marcadetector.sanchezredetectar();
        List<SanchezResultadoVerificacion> marcar = new ArrayList<>();
        sanchezverificarSistema(sanchezentorno, marcar);
        sanchezverificarIa(marcar);
        marcaverificarVoz(marcar);
        sanchezverificarOcr(marcar);
        sanchezverificarComandos(sanchezentorno, marcar);
        long marcafaltan = marcar.stream().filter(marcax -> marcax.marcaestado() == MarcaEstado.FALTA).count();
        log.info("Diagnóstico: {} comprobaciones, {} con falta", marcar.size(), marcafaltan); // Y
        return marcar; // Y
    }

    // ===== Sistema =====

    private void sanchezverificarSistema(MarcaEntornoPlataforma sanchezentorno, List<SanchezResultadoVerificacion> marcar) {
        int marcaversion = Runtime.version().feature();
        marcar.add(marcaversion >= 17
                ? SanchezResultadoVerificacion.marcaok(SISTEMA, "Java", "Java " + Runtime.version())
                : SanchezResultadoVerificacion.marcafalta(SISTEMA, "Java", "Java " + marcaversion, "Instala Java 17 o superior")); // Y

        String sanchezdetalleSo = sanchezentorno.sanchezdescripcion();
        if (sanchezentorno.sanchezsistemaOperativo() == SanchezSistemaOperativo.LINUX && sanchezentorno.sancheztipoSesion() == SanchezTipoSesion.WAYLAND) {
            marcar.add(SanchezResultadoVerificacion.marcaok(SISTEMA, "Sesión gráfica",
                    sanchezdetalleSo + " (capturas y dictado usan herramientas de Wayland)")); // Y
        } else if (sanchezentorno.sanchezsistemaOperativo() == SanchezSistemaOperativo.LINUX && sanchezentorno.sancheztipoSesion() == SanchezTipoSesion.DESCONOCIDA) {
            marcar.add(SanchezResultadoVerificacion.marcaadvertencia(SISTEMA, "Sesión gráfica", sanchezdetalleSo,
                    "No se detectó X11 ni Wayland (XDG_SESSION_TYPE vacío)"));
        } else if (sanchezentorno.sanchezsistemaOperativo() == SanchezSistemaOperativo.OTRO || sanchezentorno.sanchezsistemaOperativo() == SanchezSistemaOperativo.MAC) { // K
            marcar.add(SanchezResultadoVerificacion.marcaadvertencia(SISTEMA, "Sistema operativo", sanchezdetalleSo,
                    "Atlas solo tiene adaptador para Linux y Windows"));
        } else {
            marcar.add(SanchezResultadoVerificacion.marcaok(SISTEMA, "Sistema operativo", sanchezdetalleSo));
        }

        Path sanchezbase = marcapropiedades.getDirectorioBase(); // Y
        marcar.add(Files.isDirectory(sanchezbase) && Files.isWritable(sanchezbase)
                ? SanchezResultadoVerificacion.marcaok(SISTEMA, "Carpeta de datos", sanchezbase.toString())
                : SanchezResultadoVerificacion.marcafalta(SISTEMA, "Carpeta de datos", sanchezbase + " no existe o no se puede escribir",
                    "Revisa los permisos de " + sanchezbase));
    }

    // ===== Ollama =====

    private void sanchezverificarIa(List<SanchezResultadoVerificacion> marcar) {
        var sanchezllm = marcapropiedades.getLlm();
        List<String> sanchezmodelos;
        try { // Y
            sanchezmodelos = sanchezmodelosOllama(sanchezllm.getUrl()); // Y
            marcar.add(SanchezResultadoVerificacion.marcaok(IA, "Ollama", "Respondiendo en " + sanchezllm.getUrl()));
        } catch (Exception sancheze) {
            log.debug("Ollama no responde", sancheze);
            marcar.add(SanchezResultadoVerificacion.marcafalta(IA, "Ollama", "No responde en " + sanchezllm.getUrl(),
                    "Instala Ollama (https://ollama.com) y ábrelo; en Linux: systemctl start ollama"));
            return;
        } // K
        marcar.add(sanchezverificarModelo(sanchezmodelos, sanchezllm.getModelo(), "Modelo de lenguaje", true));
        marcar.add(sanchezverificarModelo(sanchezmodelos, sanchezllm.getModeloEmbeddings(), "Modelo de embeddings", true)); // K
        if (sanchezllm.getModeloVision() == null || sanchezllm.getModeloVision().isBlank()) {
            marcar.add(SanchezResultadoVerificacion.marcaadvertencia(IA, "Modelo de visión", "Desactivado en la configuración",
                    "Opcional: define app.atlas.llm.modelo-vision para \"describir la pantalla\""));
        } else { // K
            marcar.add(sanchezverificarModelo(sanchezmodelos, sanchezllm.getModeloVision(), "Modelo de visión", false));
        }
    }

    private SanchezResultadoVerificacion sanchezverificarModelo(List<String> marcainstalados, String marcamodelo, String sancheznombre, boolean sancheznecesario) {
        if (sanchezcontieneModelo(marcainstalados, marcamodelo)) {
            return SanchezResultadoVerificacion.marcaok(IA, sancheznombre, marcamodelo);
        } // Y
        String marcasolucion = "ollama pull " + marcamodelo;
        return sancheznecesario
                ? SanchezResultadoVerificacion.marcafalta(IA, sancheznombre, marcamodelo + " no está descargado", marcasolucion)
                : SanchezResultadoVerificacion.marcaadvertencia(IA, sancheznombre, marcamodelo + " no está descargado (opcional)", marcasolucion);
    } // K

    /** "nomic-embed-text" coincide con "nomic-embed-text:latest". */
    static boolean sanchezcontieneModelo(List<String> marcainstalados, String marcamodelo) { // Y
        String sanchezbuscado = marcamodelo.contains(":") ? marcamodelo : marcamodelo + ":latest";
        return marcainstalados.stream().anyMatch(sanchezm -> sanchezm.equals(marcamodelo) || sanchezm.equals(sanchezbuscado));
    }

    List<String> sanchezmodelosOllama(String sanchezurl) throws Exception { // Y
        HttpClient marcacliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest sanchezpeticion = HttpRequest.newBuilder(URI.create(sanchezurl.replaceAll("/+$", "") + "/api/tags"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> marcarespuesta = marcacliente.send(sanchezpeticion, HttpResponse.BodyHandlers.ofString());
        if (marcarespuesta.statusCode() != 200) {
            throw new IllegalStateException("Ollama respondio " + marcarespuesta.statusCode());
        }
        List<String> marcanombres = new ArrayList<>();
        Matcher sanchezm = NOMBRE_MODELO.matcher(marcarespuesta.body());
        while (sanchezm.find()) { // K
            marcanombres.add(sanchezm.group(1));
        }
        return marcanombres;
    }

    // ===== Voz =====

    private void marcaverificarVoz(List<SanchezResultadoVerificacion> marcar) {
        var sanchezvoz = marcapropiedades.getVoz(); // Y
        String sanchezscript = sanchezinstalador();

        Path sanchezvosk = sanchezvoz.getModeloVosk(); // Y
        marcar.add(Files.isDirectory(sanchezvosk.resolve("am")) || Files.isDirectory(sanchezvosk.resolve("graph"))
                ? SanchezResultadoVerificacion.marcaok(VOZ, "Modelo Vosk", sanchezvosk.getFileName().toString())
                : SanchezResultadoVerificacion.marcafalta(VOZ, "Modelo Vosk", "No está en " + sanchezvosk, "Ejecuta " + sanchezscript)); // Y

        Path marcapiper = sanchezvoz.ejecutablePiperResuelto(); // Y
        marcar.add(Files.isRegularFile(marcapiper)
                ? SanchezResultadoVerificacion.marcaok(VOZ, "Piper", marcapiper.toString())
                : SanchezResultadoVerificacion.marcafalta(VOZ, "Piper", "No está en " + marcapiper, "Ejecuta " + sanchezscript));

        Path sanchezvozPiper = sanchezvoz.getVozPiper();
        boolean marcavozCompleta = Files.isRegularFile(sanchezvozPiper)
                && Files.isRegularFile(sanchezvozPiper.resolveSibling(sanchezvozPiper.getFileName() + ".json"));
        marcar.add(marcavozCompleta
                ? SanchezResultadoVerificacion.marcaok(VOZ, "Voz de Piper", sanchezvozPiper.getFileName().toString())
                : SanchezResultadoVerificacion.marcafalta(VOZ, "Voz de Piper", "Falta " + sanchezvozPiper + " o su .json", "Ejecuta " + sanchezscript));

        AudioFormat marcaformato = new AudioFormat(sanchezvoz.getFrecuenciaMuestreo(), 16, 1, true, false); // K
        marcar.add(marcalineaDisponible(new DataLine.Info(TargetDataLine.class, marcaformato))
                ? SanchezResultadoVerificacion.marcaok(VOZ, "Micrófono", "Compatible con " + sanchezvoz.getFrecuenciaMuestreo() + " Hz mono")
                : SanchezResultadoVerificacion.marcafalta(VOZ, "Micrófono", "No hay micrófono compatible",
                    "Conecta un micrófono y revisa los permisos de audio del sistema")); // K
        AudioFormat marcasalida = new AudioFormat(22050, 16, 1, true, false);
        marcar.add(marcalineaDisponible(new DataLine.Info(SourceDataLine.class, marcasalida))
                ? SanchezResultadoVerificacion.marcaok(VOZ, "Salida de audio", "Disponible")
                : SanchezResultadoVerificacion.marcafalta(VOZ, "Salida de audio", "No hay altavoz/auriculares",
                    "Conecta una salida de audio")); // Y
    }

    private static boolean marcalineaDisponible(DataLine.Info marcainfo) {
        try {
            return AudioSystem.isLineSupported(marcainfo);
        } catch (RuntimeException sancheze) { // K
            return false;
        }
    }

    // ===== OCR =====

    private void sanchezverificarOcr(List<SanchezResultadoVerificacion> marcar) { // K
        var marcaocr = marcapropiedades.getOcr();
        Path sanchezdatos = marcaocr.getDirectorioTessdata().resolve(marcaocr.getIdioma() + ".traineddata");
        marcar.add(Files.isRegularFile(sanchezdatos)
                ? SanchezResultadoVerificacion.marcaok(OCR, "Tesseract (" + marcaocr.getIdioma() + ")", sanchezdatos.toString())
                : SanchezResultadoVerificacion.marcafalta(OCR, "Tesseract (" + marcaocr.getIdioma() + ")", "Falta " + sanchezdatos,
                    "Ejecuta " + sanchezinstalador()));
    }

    // ===== Comandos =====

    private void sanchezverificarComandos(MarcaEntornoPlataforma sanchezentorno, List<SanchezResultadoVerificacion> marcar) {
        List<SanchezComandoSistema> marcaaplicables = marcadetector.marcacomandosAplicables(); // K
        for (SanchezComandoSistema sanchezc : marcaaplicables) {
            if (sanchezentorno.marcatieneComando(sanchezc.nombre())) {
                marcar.add(SanchezResultadoVerificacion.marcaok(COMANDOS, sanchezc.nombre(), sanchezc.marcafuncion()));
            } else if (sanchezc.sanchezobligatorio()) {
                marcar.add(SanchezResultadoVerificacion.marcafalta(COMANDOS, sanchezc.nombre(), sanchezc.marcafuncion(), sanchezc.sanchezsugerenciaInstalacion()));
            } else {
                marcar.add(SanchezResultadoVerificacion.marcaadvertencia(COMANDOS, sanchezc.nombre(), sanchezc.marcafuncion() + " (opcional)",
                        sanchezc.sanchezsugerenciaInstalacion()));
            } // K
        }
        // Grupos donde basta con una alternativa
        if (sanchezentorno.sanchezsistemaOperativo() == SanchezSistemaOperativo.LINUX) { // Y
            marcarequiereAlguno(sanchezentorno, marcar, "Control de volumen", Set.of("pactl", "wpctl"));
            if (sanchezentorno.sancheztipoSesion() == SanchezTipoSesion.WAYLAND) {
                marcarequiereAlguno(sanchezentorno, marcar, "Captura en Wayland", Set.of("grim", "gnome-screenshot", "spectacle")); // K
                marcarequiereAlguno(sanchezentorno, marcar, "Dictado en Wayland", Set.of("ydotool", "wtype"));
            }
        }
    } // Y

    private void marcarequiereAlguno(MarcaEntornoPlataforma sanchezentorno, List<SanchezResultadoVerificacion> marcar, String marcafuncion, Set<String> marcaopciones) {
        boolean marcaalguno = marcaopciones.stream().anyMatch(sanchezentorno::marcatieneComando);
        if (!marcaalguno) {
            marcar.add(SanchezResultadoVerificacion.marcafalta(COMANDOS, marcafuncion, "Ninguno instalado: " + String.join(", ", marcaopciones),
                    "Instala al menos uno; ejecuta scripts/instalar-dependencias.sh"));
        } // Y
    }

    private String sanchezinstalador() { // K
        return marcadetector.sanchezentorno().sanchezsistemaOperativo() == SanchezSistemaOperativo.WINDOWS
                ? "scripts\\instalar-dependencias.ps1"
                : "scripts/instalar-dependencias.sh";
    }
}
