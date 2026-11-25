// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ia;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.ia.ResultadoVerificacion.Estado;
import pe.upeu.atlas.plataforma.ComandoSistema;
import pe.upeu.atlas.plataforma.DetectorEntorno;
import pe.upeu.atlas.plataforma.EntornoPlataforma;
import pe.upeu.atlas.plataforma.SistemaOperativo;
import pe.upeu.atlas.plataforma.TipoSesion;

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
public class VerificadorDependencias {

    static final String SISTEMA = "Sistema";
    static final String IA = "Inteligencia artificial";
    static final String VOZ = "Voz";
    static final String OCR = "OCR";
    static final String COMANDOS = "Comandos del sistema"; // Y

    private static final Pattern NOMBRE_MODELO = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\""); // K

    private final PropiedadesAtlas propiedades;
    private final DetectorEntorno detector;

    /** Ejecuta todas las comprobaciones (puede tardar unos segundos: no llamar en el hilo de JavaFX). */
    public List<ResultadoVerificacion> verificarTodo() {
        EntornoPlataforma entorno = detector.redetectar();
        List<ResultadoVerificacion> r = new ArrayList<>();
        verificarSistema(entorno, r);
        verificarIa(r);
        verificarVoz(r);
        verificarOcr(r);
        verificarComandos(entorno, r);
        long faltan = r.stream().filter(x -> x.estado() == Estado.FALTA).count();
        log.info("Diagnóstico: {} comprobaciones, {} con falta", r.size(), faltan); // Y
        return r; // Y
    }

    // ===== Sistema =====

    private void verificarSistema(EntornoPlataforma entorno, List<ResultadoVerificacion> r) {
        int version = Runtime.version().feature();
        r.add(version >= 17
                ? ResultadoVerificacion.ok(SISTEMA, "Java", "Java " + Runtime.version())
                : ResultadoVerificacion.falta(SISTEMA, "Java", "Java " + version, "Instala Java 17 o superior")); // Y

        String detalleSo = entorno.descripcion();
        if (entorno.sistemaOperativo() == SistemaOperativo.LINUX && entorno.tipoSesion() == TipoSesion.WAYLAND) {
            r.add(ResultadoVerificacion.ok(SISTEMA, "Sesión gráfica",
                    detalleSo + " (capturas y dictado usan herramientas de Wayland)")); // Y
        } else if (entorno.sistemaOperativo() == SistemaOperativo.LINUX && entorno.tipoSesion() == TipoSesion.DESCONOCIDA) {
            r.add(ResultadoVerificacion.advertencia(SISTEMA, "Sesión gráfica", detalleSo,
                    "No se detectó X11 ni Wayland (XDG_SESSION_TYPE vacío)"));
        } else if (entorno.sistemaOperativo() == SistemaOperativo.OTRO || entorno.sistemaOperativo() == SistemaOperativo.MAC) { // K
            r.add(ResultadoVerificacion.advertencia(SISTEMA, "Sistema operativo", detalleSo,
                    "Atlas solo tiene adaptador para Linux y Windows"));
        } else {
            r.add(ResultadoVerificacion.ok(SISTEMA, "Sistema operativo", detalleSo));
        }

        Path base = propiedades.getDirectorioBase(); // Y
        r.add(Files.isDirectory(base) && Files.isWritable(base)
                ? ResultadoVerificacion.ok(SISTEMA, "Carpeta de datos", base.toString())
                : ResultadoVerificacion.falta(SISTEMA, "Carpeta de datos", base + " no existe o no se puede escribir",
                    "Revisa los permisos de " + base));
    }

    // ===== Ollama =====

    private void verificarIa(List<ResultadoVerificacion> r) {
        var llm = propiedades.getLlm();
        List<String> modelos;
        try { // Y
            modelos = modelosOllama(llm.getUrl()); // Y
            r.add(ResultadoVerificacion.ok(IA, "Ollama", "Respondiendo en " + llm.getUrl()));
        } catch (Exception e) {
            log.debug("Ollama no responde", e);
            r.add(ResultadoVerificacion.falta(IA, "Ollama", "No responde en " + llm.getUrl(),
                    "Instala Ollama (https://ollama.com) y ábrelo; en Linux: systemctl start ollama"));
            return;
        } // K
        r.add(verificarModelo(modelos, llm.getModelo(), "Modelo de lenguaje", true));
        r.add(verificarModelo(modelos, llm.getModeloEmbeddings(), "Modelo de embeddings", true)); // K
        if (llm.getModeloVision() == null || llm.getModeloVision().isBlank()) {
            r.add(ResultadoVerificacion.advertencia(IA, "Modelo de visión", "Desactivado en la configuración",
                    "Opcional: define app.atlas.llm.modelo-vision para \"describir la pantalla\""));
        } else { // K
            r.add(verificarModelo(modelos, llm.getModeloVision(), "Modelo de visión", false));
        }
    }

    private ResultadoVerificacion verificarModelo(List<String> instalados, String modelo, String nombre, boolean necesario) {
        if (contieneModelo(instalados, modelo)) {
            return ResultadoVerificacion.ok(IA, nombre, modelo);
        } // Y
        String solucion = "ollama pull " + modelo;
        return necesario
                ? ResultadoVerificacion.falta(IA, nombre, modelo + " no está descargado", solucion)
                : ResultadoVerificacion.advertencia(IA, nombre, modelo + " no está descargado (opcional)", solucion);
    } // K

    /** "nomic-embed-text" coincide con "nomic-embed-text:latest". */
    static boolean contieneModelo(List<String> instalados, String modelo) { // Y
        String buscado = modelo.contains(":") ? modelo : modelo + ":latest";
        return instalados.stream().anyMatch(m -> m.equals(modelo) || m.equals(buscado));
    }

    List<String> modelosOllama(String url) throws Exception { // Y
        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url.replaceAll("/+$", "") + "/api/tags"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() != 200) {
            throw new IllegalStateException("Ollama respondio " + respuesta.statusCode());
        }
        List<String> nombres = new ArrayList<>();
        Matcher m = NOMBRE_MODELO.matcher(respuesta.body());
        while (m.find()) { // K
            nombres.add(m.group(1));
        }
        return nombres;
    }

    // ===== Voz =====

    private void verificarVoz(List<ResultadoVerificacion> r) {
        var voz = propiedades.getVoz(); // Y
        String script = instalador();

        Path vosk = voz.getModeloVosk(); // Y
        r.add(Files.isDirectory(vosk.resolve("am")) || Files.isDirectory(vosk.resolve("graph"))
                ? ResultadoVerificacion.ok(VOZ, "Modelo Vosk", vosk.getFileName().toString())
                : ResultadoVerificacion.falta(VOZ, "Modelo Vosk", "No está en " + vosk, "Ejecuta " + script)); // Y

        Path piper = voz.ejecutablePiperResuelto(); // Y
        r.add(Files.isRegularFile(piper)
                ? ResultadoVerificacion.ok(VOZ, "Piper", piper.toString())
                : ResultadoVerificacion.falta(VOZ, "Piper", "No está en " + piper, "Ejecuta " + script));

        Path vozPiper = voz.getVozPiper();
        boolean vozCompleta = Files.isRegularFile(vozPiper)
                && Files.isRegularFile(vozPiper.resolveSibling(vozPiper.getFileName() + ".json"));
        r.add(vozCompleta
                ? ResultadoVerificacion.ok(VOZ, "Voz de Piper", vozPiper.getFileName().toString())
                : ResultadoVerificacion.falta(VOZ, "Voz de Piper", "Falta " + vozPiper + " o su .json", "Ejecuta " + script));

        AudioFormat formato = new AudioFormat(voz.getFrecuenciaMuestreo(), 16, 1, true, false); // K
        r.add(lineaDisponible(new DataLine.Info(TargetDataLine.class, formato))
                ? ResultadoVerificacion.ok(VOZ, "Micrófono", "Compatible con " + voz.getFrecuenciaMuestreo() + " Hz mono")
                : ResultadoVerificacion.falta(VOZ, "Micrófono", "No hay micrófono compatible",
                    "Conecta un micrófono y revisa los permisos de audio del sistema")); // K
        AudioFormat salida = new AudioFormat(22050, 16, 1, true, false);
        r.add(lineaDisponible(new DataLine.Info(SourceDataLine.class, salida))
                ? ResultadoVerificacion.ok(VOZ, "Salida de audio", "Disponible")
                : ResultadoVerificacion.falta(VOZ, "Salida de audio", "No hay altavoz/auriculares",
                    "Conecta una salida de audio")); // Y
    }

    private static boolean lineaDisponible(DataLine.Info info) {
        try {
            return AudioSystem.isLineSupported(info);
        } catch (RuntimeException e) { // K
            return false;
        }
    }

    // ===== OCR =====

    private void verificarOcr(List<ResultadoVerificacion> r) { // K
        var ocr = propiedades.getOcr();
        Path datos = ocr.getDirectorioTessdata().resolve(ocr.getIdioma() + ".traineddata");
        r.add(Files.isRegularFile(datos)
                ? ResultadoVerificacion.ok(OCR, "Tesseract (" + ocr.getIdioma() + ")", datos.toString())
                : ResultadoVerificacion.falta(OCR, "Tesseract (" + ocr.getIdioma() + ")", "Falta " + datos,
                    "Ejecuta " + instalador()));
    }

    // ===== Comandos =====

    private void verificarComandos(EntornoPlataforma entorno, List<ResultadoVerificacion> r) {
        List<ComandoSistema> aplicables = detector.comandosAplicables(); // K
        for (ComandoSistema c : aplicables) {
            if (entorno.tieneComando(c.nombre())) {
                r.add(ResultadoVerificacion.ok(COMANDOS, c.nombre(), c.funcion()));
            } else if (c.obligatorio()) {
                r.add(ResultadoVerificacion.falta(COMANDOS, c.nombre(), c.funcion(), c.sugerenciaInstalacion()));
            } else {
                r.add(ResultadoVerificacion.advertencia(COMANDOS, c.nombre(), c.funcion() + " (opcional)",
                        c.sugerenciaInstalacion()));
            } // K
        }
        // Grupos donde basta con una alternativa
        if (entorno.sistemaOperativo() == SistemaOperativo.LINUX) { // Y
            requiereAlguno(entorno, r, "Control de volumen", Set.of("pactl", "wpctl"));
            if (entorno.tipoSesion() == TipoSesion.WAYLAND) {
                requiereAlguno(entorno, r, "Captura en Wayland", Set.of("grim", "gnome-screenshot", "spectacle")); // K
                requiereAlguno(entorno, r, "Dictado en Wayland", Set.of("ydotool", "wtype"));
            }
        }
    } // Y

    private void requiereAlguno(EntornoPlataforma entorno, List<ResultadoVerificacion> r, String funcion, Set<String> opciones) {
        boolean alguno = opciones.stream().anyMatch(entorno::tieneComando);
        if (!alguno) {
            r.add(ResultadoVerificacion.falta(COMANDOS, funcion, "Ninguno instalado: " + String.join(", ", opciones),
                    "Instala al menos uno; ejecuta scripts/instalar-dependencias.sh"));
        } // Y
    }

    private String instalador() { // K
        return detector.entorno().sistemaOperativo() == SistemaOperativo.WINDOWS
                ? "scripts\\instalar-dependencias.ps1"
                : "scripts/instalar-dependencias.sh";
    }
}
