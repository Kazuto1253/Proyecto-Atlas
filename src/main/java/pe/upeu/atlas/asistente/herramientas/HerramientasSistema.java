// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente.herramientas;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.plataforma.Lanzador;
import pe.upeu.atlas.plataforma.SistemaOperativo;
import pe.upeu.atlas.seguridad.ConfirmadorAcciones;
import pe.upeu.atlas.service.Calculadora;
import pe.upeu.atlas.service.MonitorSistema;
import pe.upeu.atlas.service.ServicioActividad;
import pe.upeu.atlas.service.ServicioDocumentos;

import javax.imageio.ImageIO;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Herramientas que el modelo puede invocar. Reglas de seguridad:
 * <ul>
 *   <li>Solo se tocan carpetas de {@code app.atlas.carpetas-permitidas}.</li>
 *   <li>Los programas se eligen de una lista fija; el modelo nunca escribe comandos.</li>
 *   <li>Con {@code app.atlas.modo-solo-lectura=true} se bloquean las acciones que modifican el equipo.</li>
 *   <li>Bloquear la pantalla exige confirmación del usuario en un diálogo.</li>
 *   <li>Cada uso queda en la bitácora de actividad.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HerramientasSistema {

    private static final Map<String, String[]> APLICACIONES_WINDOWS = Map.of(
            "calculadora", new String[]{"calc.exe"},
            "bloc de notas", new String[]{"notepad.exe"},
            "notas", new String[]{"notepad.exe"},
            "paint", new String[]{"mspaint.exe"},
            "explorador", new String[]{"explorer.exe"},
            "navegador", new String[]{"cmd.exe", "/c", "start", "", "https://www.google.com"});

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-PE"));

    private final PropiedadesAtlas propiedades; // Y
    private final MonitorSistema monitor;
    private final Lanzador lanzador;
    private final ConfirmadorAcciones confirmador;
    private final ServicioActividad actividad; // Y
    private final ServicioDocumentos documentos; // K

    /**
     * Cancelación del turno en curso: el orquestador la fija en el hilo que ejecuta al modelo. Si el usuario
     * interrumpió, ninguna herramienta debe actuar aunque el modelo la pida tarde.
     */
    public static final ThreadLocal<java.util.concurrent.atomic.AtomicBoolean> TURNO = new ThreadLocal<>();
    static final String MENSAJE_CANCELADO = "El usuario interrumpió la petición. No hagas nada más con esa petición.";

    static boolean cancelado() { // Y
        java.util.concurrent.atomic.AtomicBoolean c = TURNO.get();
        return c != null && c.get(); // K
    }

    private volatile Consumer<String> notificador = t -> { }; // Y

    /** La interfaz se entera de cada herramienta usada para mostrarla en el chat. */
    public void setNotificador(Consumer<String> notificador) {
        this.notificador = notificador == null ? t -> { } : notificador;
    }

    // ===== Lectura =====

    @Tool("Dice la fecha y la hora actuales. Úsala cuando pregunten qué hora o qué día es.")
    public String fechaYHora() {
        if (cancelado()) {
            return MENSAJE_CANCELADO; // K
        }
        String texto = LocalDateTime.now().format(FECHA_HORA);
        usado("Consultó la fecha y hora", texto);
        return texto;
    }

    @Tool("Informa el estado del equipo: uso de procesador, memoria, disco y batería.")
    public String estadoDelEquipo() {
        if (cancelado()) {
            return MENSAJE_CANCELADO; // K
        } // Y
        try {
            MonitorSistema.Metricas m = monitor.medir(); // K
            StringBuilder sb = new StringBuilder()
                    .append("Procesador al ").append(Math.round(m.cpu() * 100)).append("%. ")
                    .append("Memoria: ").append(MonitorSistema.bytes(m.ramUsada())).append(" usados de ")
                    .append(MonitorSistema.bytes(m.ramTotal())).append(". ")
                    .append("Disco: ").append(MonitorSistema.bytes(m.discoUsado())).append(" usados de ")
                    .append(MonitorSistema.bytes(m.discoTotal())).append(". "); // K
            if (m.hayBateria()) {
                sb.append("Batería al ").append(Math.round(m.bateria() * 100)).append("%")
                        .append(m.cargando() ? " y cargando." : "."); // K
            } else { // Y
                sb.append("El equipo no tiene batería."); // Y
            } // K
            usado("Consultó el estado del equipo", null);
            return sb.toString(); // K
        } catch (RuntimeException e) {
            return fallo("leer el estado del equipo", e); // Y
        } // K
    } // Y

    @Tool("Calcula una operación matemática con números, + - * / % ^, paréntesis y raiz(x). "
            + "Úsala siempre para cualquier cuenta; no calcules de memoria.")
    public String calcular(@P("La operación, por ejemplo 15*12+3") String operacion) {
        if (cancelado()) { // Y
            return MENSAJE_CANCELADO;
        }
        try {
            String resultado = Calculadora.formatear(Calculadora.evaluar(operacion));
            usado("Calculó", operacion + " = " + resultado);
            return operacion + " = " + resultado;
        } catch (NegocioException e) { // Y
            return "No pude calcularlo: " + e.getMessage();
        } // Y
    }

    @Tool("Busca información en los documentos que el usuario importó (apuntes, PDF, Word, imágenes). "
            + "Úsala cuando pregunten por el contenido de sus documentos, apuntes o tareas. "
            + "Responde solo con lo que devuelva; si no hay nada relevante, dilo.")
    public String buscarEnDocumentos(@P("La pregunta o el tema a buscar") String consulta) {
        if (cancelado()) {
            return MENSAJE_CANCELADO; // Y
        }
        try {
            List<ServicioDocumentos.Resultado> resultados = documentos.buscar(consulta, 3); // K
            usado("Buscó en tus documentos", consulta);
            if (resultados.isEmpty()) {
                return "No tienes documentos importados. Puedes agregarlos en la vista Documentos."; // K
            }
            StringBuilder sb = new StringBuilder("Fragmentos encontrados:\n");
            int n = 1;
            for (ServicioDocumentos.Resultado r : resultados) {
                String t = r.texto().length() > 700 ? r.texto().substring(0, 700) + "…" : r.texto();
                sb.append('[').append(n++).append("] (").append(r.documento()).append(") ").append(t).append('\n');
            }
            return sb.toString(); // Y
        } catch (NegocioException | PermisoDenegadoException e) {
            return e.getMessage();
        } catch (RuntimeException e) {
            return fallo("buscar en tus documentos", e); // K
        }
    }

    // ===== Acciones sobre el equipo =====

    @Tool("Abre una aplicación de una lista fija: calculadora, bloc de notas, paint, explorador o navegador.")
    public String abrirAplicacion(@P("Nombre de la aplicación: calculadora, bloc de notas, paint, explorador o navegador") String nombre) {
        if (cancelado()) {
            return MENSAJE_CANCELADO; // K
        } // Y
        String bloqueo = bloqueoSoloLectura();
        if (bloqueo != null) { // Y
            return bloqueo; // Y
        }
        if (lanzador.sistema() != SistemaOperativo.WINDOWS) {
            return "Abrir aplicaciones solo está disponible en Windows por ahora.";
        }
        String clave = normalizar(nombre);
        String[] comando = APLICACIONES_WINDOWS.get(clave);
        if (comando == null) {
            return "Solo puedo abrir: " + String.join(", ", APLICACIONES_WINDOWS.keySet()) + ".";
        }
        try {
            lanzador.ejecutar(comando);
            usado("Abrió la aplicación " + clave, null); // K
            return "Abrí " + clave + ".";
        } catch (RuntimeException e) {
            return fallo("abrir la aplicación", e); // K
        }
    }

    @Tool("Cambia el volumen del equipo. Acción: subir, bajar o silenciar. Cada paso mueve el volumen un 2%.")
    public String controlarVolumen(@P("subir, bajar o silenciar") String accion,
                                   @P("Cantidad de pasos de 1 a 25; si no se indica, 5") Integer pasos) {
        if (cancelado()) { // Y
            return MENSAJE_CANCELADO;
        } // Y
        String bloqueo = bloqueoSoloLectura();
        if (bloqueo != null) {
            return bloqueo;
        }
        if (lanzador.sistema() != SistemaOperativo.WINDOWS) {
            return "El control de volumen solo está disponible en Windows por ahora.";
        }
        int n = Math.max(1, Math.min(25, pasos == null ? 5 : pasos)); // Y
        String a = normalizar(accion);
        int tecla;
        switch (a) {
            case "subir", "sube", "aumentar" -> tecla = 175; // Y
            case "bajar", "baja", "disminuir" -> tecla = 174;
            case "silenciar", "silencio", "mute" -> { // Y
                tecla = 173;
                n = 1; // Y
            }
            default -> {
                return "Puedo subir, bajar o silenciar el volumen.";
            }
        }
        try {
            lanzador.ejecutarYEsperar(15, "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "$w = New-Object -ComObject WScript.Shell; 1.." + n + " | ForEach-Object { $w.SendKeys([char]" + tecla + ") }");
            usado("Volumen: " + a, n + " pasos");
            return tecla == 173 ? "Listo, alterné el silencio."
                    : "Listo, " + (tecla == 175 ? "subí" : "bajé") + " el volumen.";
        } catch (RuntimeException e) { // Y
            return fallo("cambiar el volumen", e);
        } // Y
    }

    @Tool("Toma una captura de toda la pantalla y la guarda en la carpeta de capturas de Atlas.")
    public String tomarCaptura() { // K
        if (cancelado()) { // K
            return MENSAJE_CANCELADO;
        }
        String bloqueo = bloqueoSoloLectura();
        if (bloqueo != null) {
            return bloqueo;
        } // Y
        if (GraphicsEnvironment.isHeadless()) {
            return "Este equipo no tiene pantalla disponible para capturar.";
        } // K
        try {
            Rectangle area = new Rectangle();
            for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) { // Y
                area = area.union(d.getDefaultConfiguration().getBounds()); // Y
            } // Y
            BufferedImage imagen = new Robot().createScreenCapture(area);
            Path carpeta = propiedades.getCarpetaCapturas();
            Files.createDirectories(carpeta);
            Path archivo = carpeta.resolve("captura-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png"); // K
            ImageIO.write(imagen, "png", archivo.toFile());
            usado("Tomó una captura de pantalla", archivo.toString());
            return "Guardé la captura en la carpeta de capturas de Atlas: " + archivo.getFileName() + ".";
        } catch (Exception e) {
            return fallo("tomar la captura", e);
        }
    }

    @Tool("Guarda una nota de texto en la carpeta de documentos de Atlas.")
    public String guardarNota(@P("Título corto de la nota") String titulo, @P("Contenido de la nota") String texto) { // Y
        if (cancelado()) { // K
            return MENSAJE_CANCELADO;
        }
        String bloqueo = bloqueoSoloLectura(); // K
        if (bloqueo != null) {
            return bloqueo;
        }
        if (texto == null || texto.isBlank()) {
            return "La nota está vacía; dime qué quieres guardar."; // K
        }
        try { // K
            Path carpeta = propiedades.getDirectorioBase().resolve("documentos").resolve("notas");
            Files.createDirectories(carpeta);
            String base = normalizar(titulo == null || titulo.isBlank() ? "nota" : titulo)
                    .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (base.isEmpty()) { // Y
                base = "nota";
            }
            base = base.substring(0, Math.min(base.length(), 40));
            Path archivo = carpeta.resolve(base + "-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt"); // Y
            Files.writeString(archivo, (titulo == null ? "" : titulo.strip() + "\n\n") + texto.strip() + "\n",
                    StandardCharsets.UTF_8);
            usado("Guardó una nota", archivo.toString());
            return "Guardé la nota en " + archivo.getFileName() + ".";
        } catch (IOException | RuntimeException e) {
            return fallo("guardar la nota", e);
        } // Y
    }

    @Tool("Bloquea la pantalla del equipo. Solo úsala si el usuario lo pidió de forma explícita; antes pide su confirmación.")
    public String bloquearPantalla() {
        if (cancelado()) { // Y
            return MENSAJE_CANCELADO; // Y
        }
        String bloqueo = bloqueoSoloLectura();
        if (bloqueo != null) {
            return bloqueo; // Y
        }
        if (lanzador.sistema() != SistemaOperativo.WINDOWS) {
            return "Bloquear la pantalla solo está disponible en Windows por ahora."; // K
        } // K
        if (!confirmador.confirmar("¿Bloquear la pantalla?", "Atlas quiere bloquear la pantalla del equipo.")) {
            usado("Bloqueo de pantalla cancelado", null); // Y
            return "Cancelé el bloqueo porque no lo confirmaste.";
        } // K
        try { // Y
            lanzador.ejecutar("rundll32.exe", "user32.dll,LockWorkStation");
            usado("Bloqueó la pantalla", null); // K
            return "Pantalla bloqueada.";
        } catch (RuntimeException e) { // K
            return fallo("bloquear la pantalla", e);
        }
    } // K

    // ===== Utilidades =====

    static String normalizar(String texto) { // K
        if (texto == null) {
            return "";
        } // Y
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .strip();
    } // K

    private String bloqueoSoloLectura() {
        return propiedades.isModoSoloLectura()
                ? "El modo solo lectura está activo, así que no puedo hacer cambios en el equipo."
                : null;
    }

    private void usado(String accion, String detalle) {
        actividad.registrar(accion, detalle); // K
        try { // K
            notificador.accept(accion);
        } catch (RuntimeException e) { // K
            log.debug("El notificador falló", e);
        } // Y
    }

    private String fallo(String que, Exception e) {
        log.warn("Falló la herramienta al {}", que, e);
        return "No pude " + que + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
