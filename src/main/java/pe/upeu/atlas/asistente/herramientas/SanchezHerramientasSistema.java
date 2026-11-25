// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.asistente.herramientas;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.plataforma.SanchezLanzador;
import pe.upeu.atlas.plataforma.SanchezSistemaOperativo;
import pe.upeu.atlas.seguridad.SanchezConfirmadorAcciones;
import pe.upeu.atlas.service.SanchezCalculadora;
import pe.upeu.atlas.service.MarcaMonitorSistema;
import pe.upeu.atlas.service.SanchezServicioActividad;
import pe.upeu.atlas.service.SanchezServicioDocumentos;

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
public class SanchezHerramientasSistema {

    private static final Map<String, String[]> APLICACIONES_WINDOWS = Map.of(
            "calculadora", new String[]{"calc.exe"},
            "bloc de notas", new String[]{"notepad.exe"},
            "notas", new String[]{"notepad.exe"},
            "paint", new String[]{"mspaint.exe"},
            "explorador", new String[]{"explorer.exe"},
            "navegador", new String[]{"cmd.exe", "/c", "start", "", "https://www.google.com"});

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es-PE"));

    private final SanchezPropiedadesAtlas marcapropiedades; // Y
    private final MarcaMonitorSistema marcamonitor;
    private final SanchezLanzador sanchezlanzador;
    private final SanchezConfirmadorAcciones marcaconfirmador;
    private final SanchezServicioActividad sanchezactividad; // Y
    private final SanchezServicioDocumentos sanchezdocumentos; // K

    /**
     * Cancelación del turno en curso: el orquestador la fija en el hilo que ejecuta al modelo. Si el usuario
     * interrumpió, ninguna herramienta debe actuar aunque el modelo la pida tarde.
     */
    public static final ThreadLocal<java.util.concurrent.atomic.AtomicBoolean> TURNO = new ThreadLocal<>();
    static final String MENSAJE_CANCELADO = "El usuario interrumpió la petición. No hagas nada más con esa petición.";

    static boolean marcacancelado() { // Y
        java.util.concurrent.atomic.AtomicBoolean sanchezc = TURNO.get();
        return sanchezc != null && sanchezc.get(); // K
    }

    private volatile Consumer<String> marcanotificador = marcat -> { }; // Y

    /** La interfaz se entera de cada herramienta usada para mostrarla en el chat. */
    public void marcasetNotificador(Consumer<String> marcanotificador) {
        this.marcanotificador = marcanotificador == null ? marcat -> { } : marcanotificador;
    }

    // ===== Lectura =====

    @Tool("Dice la fecha y la hora actuales. Úsala cuando pregunten qué hora o qué día es.")
    public String fechaYHora() {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO; // K
        }
        String marcatexto = LocalDateTime.now().format(FECHA_HORA);
        marcausado("Consultó la fecha y hora", marcatexto);
        return marcatexto;
    }

    @Tool("Informa el estado del equipo: uso de procesador, memoria, disco y batería.")
    public String estadoDelEquipo() {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO; // K
        } // Y
        try {
            MarcaMonitorSistema.MarcaMetricas sanchezm = marcamonitor.sanchezmedir(); // K
            StringBuilder sanchezsb = new StringBuilder()
                    .append("Procesador al ").append(Math.round(sanchezm.marcacpu() * 100)).append("%. ")
                    .append("Memoria: ").append(MarcaMonitorSistema.sanchezbytes(sanchezm.marcaramUsada())).append(" usados de ")
                    .append(MarcaMonitorSistema.sanchezbytes(sanchezm.marcaramTotal())).append(". ")
                    .append("Disco: ").append(MarcaMonitorSistema.sanchezbytes(sanchezm.marcadiscoUsado())).append(" usados de ")
                    .append(MarcaMonitorSistema.sanchezbytes(sanchezm.marcadiscoTotal())).append(". "); // K
            if (sanchezm.marcahayBateria()) {
                sanchezsb.append("Batería al ").append(Math.round(sanchezm.marcabateria() * 100)).append("%")
                        .append(sanchezm.sanchezcargando() ? " y cargando." : "."); // K
            } else { // Y
                sanchezsb.append("El equipo no tiene batería."); // Y
            } // K
            marcausado("Consultó el estado del equipo", null);
            return sanchezsb.toString(); // K
        } catch (RuntimeException sancheze) {
            return marcafallo("leer el estado del equipo", sancheze); // Y
        } // K
    } // Y

    @Tool("Calcula una operación matemática con números, + - * / % ^, paréntesis y raiz(x). "
            + "Úsala siempre para cualquier cuenta; no calcules de memoria.")
    public String calcular(@P("La operación, por ejemplo 15*12+3") String operacion) {
        if (marcacancelado()) { // Y
            return MENSAJE_CANCELADO;
        }
        try {
            String sanchezresultado = SanchezCalculadora.sanchezformatear(SanchezCalculadora.marcaevaluar(operacion));
            marcausado("Calculó", operacion + " = " + sanchezresultado);
            return operacion + " = " + sanchezresultado;
        } catch (SanchezNegocioException sancheze) { // Y
            return "No pude calcularlo: " + sancheze.getMessage();
        } // Y
    }

    @Tool("Busca información en los documentos que el usuario importó (apuntes, PDF, Word, imágenes). "
            + "Úsala cuando pregunten por el contenido de sus documentos, apuntes o tareas. "
            + "Responde solo con lo que devuelva; si no hay nada relevante, dilo.")
    public String buscarEnDocumentos(@P("La pregunta o el tema a buscar") String consulta) {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO; // Y
        }
        try {
            List<SanchezServicioDocumentos.SanchezResultado> marcaresultados = sanchezdocumentos.buscar(consulta, 3); // K
            marcausado("Buscó en tus documentos", consulta);
            if (marcaresultados.isEmpty()) {
                return "No tienes documentos importados. Puedes agregarlos en la vista Documentos."; // K
            }
            StringBuilder sanchezsb = new StringBuilder("Fragmentos encontrados:\n");
            int marcan = 1;
            for (SanchezServicioDocumentos.SanchezResultado marcar : marcaresultados) {
                String marcat = marcar.texto().length() > 700 ? marcar.texto().substring(0, 700) + "…" : marcar.texto();
                sanchezsb.append('[').append(marcan++).append("] (").append(marcar.marcadocumento()).append(") ").append(marcat).append('\n');
            }
            return sanchezsb.toString(); // Y
        } catch (SanchezNegocioException | SanchezPermisoDenegadoException sancheze) {
            return sancheze.getMessage();
        } catch (RuntimeException sancheze) {
            return marcafallo("buscar en tus documentos", sancheze); // K
        }
    }

    // ===== Acciones sobre el equipo =====

    @Tool("Abre una aplicación de una lista fija: calculadora, bloc de notas, paint, explorador o navegador.")
    public String abrirAplicacion(@P("Nombre de la aplicación: calculadora, bloc de notas, paint, explorador o navegador") String nombre) {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO; // K
        } // Y
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) { // Y
            return sanchezbloqueo; // Y
        }
        if (sanchezlanzador.marcasistema() != SanchezSistemaOperativo.WINDOWS) {
            return "Abrir aplicaciones solo está disponible en Windows por ahora.";
        }
        String sanchezclave = sancheznormalizar(nombre);
        String[] sanchezcomando = APLICACIONES_WINDOWS.get(sanchezclave);
        if (sanchezcomando == null) {
            return "Solo puedo abrir: " + String.join(", ", APLICACIONES_WINDOWS.keySet()) + ".";
        }
        try {
            sanchezlanzador.sanchezejecutar(sanchezcomando);
            marcausado("Abrió la aplicación " + sanchezclave, null); // K
            return "Abrí " + sanchezclave + ".";
        } catch (RuntimeException sancheze) {
            return marcafallo("abrir la aplicación", sancheze); // K
        }
    }

    @Tool("Cambia el volumen del equipo. Acción: subir, bajar o silenciar. Cada paso mueve el volumen un 2%.")
    public String controlarVolumen(@P("subir, bajar o silenciar") String accion,
                                   @P("Cantidad de pasos de 1 a 25; si no se indica, 5") Integer pasos) {
        if (marcacancelado()) { // Y
            return MENSAJE_CANCELADO;
        } // Y
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        if (sanchezlanzador.marcasistema() != SanchezSistemaOperativo.WINDOWS) {
            return "El control de volumen solo está disponible en Windows por ahora.";
        }
        int marcan = Math.max(1, Math.min(25, pasos == null ? 5 : pasos)); // Y
        String sancheza = sancheznormalizar(accion);
        int sancheztecla;
        switch (sancheza) {
            case "subir", "sube", "aumentar" -> sancheztecla = 175; // Y
            case "bajar", "baja", "disminuir" -> sancheztecla = 174;
            case "silenciar", "silencio", "mute" -> { // Y
                sancheztecla = 173;
                marcan = 1; // Y
            }
            default -> {
                return "Puedo subir, bajar o silenciar el volumen.";
            }
        }
        try {
            sanchezlanzador.marcaejecutarYEsperar(15, "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "$w = New-Object -ComObject WScript.Shell; 1.." + marcan + " | ForEach-Object { $w.SendKeys([char]" + sancheztecla + ") }");
            marcausado("Volumen: " + sancheza, marcan + " pasos");
            return sancheztecla == 173 ? "Listo, alterné el silencio."
                    : "Listo, " + (sancheztecla == 175 ? "subí" : "bajé") + " el volumen.";
        } catch (RuntimeException sancheze) { // Y
            return marcafallo("cambiar el volumen", sancheze);
        } // Y
    }

    @Tool("Toma una captura de toda la pantalla y la guarda en la carpeta de capturas de Atlas.")
    public String tomarCaptura() { // K
        if (marcacancelado()) { // K
            return MENSAJE_CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        } // Y
        if (GraphicsEnvironment.isHeadless()) {
            return "Este equipo no tiene pantalla disponible para capturar.";
        } // K
        try {
            Rectangle sanchezarea = new Rectangle();
            for (GraphicsDevice marcad : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) { // Y
                sanchezarea = sanchezarea.union(marcad.getDefaultConfiguration().getBounds()); // Y
            } // Y
            BufferedImage sanchezimagen = new Robot().createScreenCapture(sanchezarea);
            Path marcacarpeta = marcapropiedades.getCarpetaCapturas();
            Files.createDirectories(marcacarpeta);
            Path marcaarchivo = marcacarpeta.resolve("captura-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png"); // K
            ImageIO.write(sanchezimagen, "png", marcaarchivo.toFile());
            marcausado("Tomó una captura de pantalla", marcaarchivo.toString());
            return "Guardé la captura en la carpeta de capturas de Atlas: " + marcaarchivo.getFileName() + ".";
        } catch (Exception sancheze) {
            return marcafallo("tomar la captura", sancheze);
        }
    }

    @Tool("Guarda una nota de texto en la carpeta de documentos de Atlas.")
    public String guardarNota(@P("Título corto de la nota") String titulo, @P("Contenido de la nota") String texto) { // Y
        if (marcacancelado()) { // K
            return MENSAJE_CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura(); // K
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        if (texto == null || texto.isBlank()) {
            return "La nota está vacía; dime qué quieres guardar."; // K
        }
        try { // K
            Path marcacarpeta = marcapropiedades.getDirectorioBase().resolve("documentos").resolve("notas");
            Files.createDirectories(marcacarpeta);
            String sanchezbase = sancheznormalizar(titulo == null || titulo.isBlank() ? "nota" : titulo)
                    .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (sanchezbase.isEmpty()) { // Y
                sanchezbase = "nota";
            }
            sanchezbase = sanchezbase.substring(0, Math.min(sanchezbase.length(), 40));
            Path marcaarchivo = marcacarpeta.resolve(sanchezbase + "-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt"); // Y
            Files.writeString(marcaarchivo, (titulo == null ? "" : titulo.strip() + "\n\n") + texto.strip() + "\n",
                    StandardCharsets.UTF_8);
            marcausado("Guardó una nota", marcaarchivo.toString());
            return "Guardé la nota en " + marcaarchivo.getFileName() + ".";
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("guardar la nota", sancheze);
        } // Y
    }

    @Tool("Bloquea la pantalla del equipo. Solo úsala si el usuario lo pidió de forma explícita; antes pide su confirmación.")
    public String bloquearPantalla() {
        if (marcacancelado()) { // Y
            return MENSAJE_CANCELADO; // Y
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo; // Y
        }
        if (sanchezlanzador.marcasistema() != SanchezSistemaOperativo.WINDOWS) {
            return "Bloquear la pantalla solo está disponible en Windows por ahora."; // K
        } // K
        if (!marcaconfirmador.sanchezconfirmar("¿Bloquear la pantalla?", "Atlas quiere bloquear la pantalla del equipo.")) {
            marcausado("Bloqueo de pantalla cancelado", null); // Y
            return "Cancelé el bloqueo porque no lo confirmaste.";
        } // K
        try { // Y
            sanchezlanzador.sanchezejecutar("rundll32.exe", "user32.dll,LockWorkStation");
            marcausado("Bloqueó la pantalla", null); // K
            return "Pantalla bloqueada.";
        } catch (RuntimeException sancheze) { // K
            return marcafallo("bloquear la pantalla", sancheze);
        }
    } // K

    // ===== Utilidades =====

    static String sancheznormalizar(String marcatexto) { // K
        if (marcatexto == null) {
            return "";
        } // Y
        return Normalizer.normalize(marcatexto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .strip();
    } // K

    private String marcabloqueoSoloLectura() {
        return marcapropiedades.isModoSoloLectura()
                ? "El modo solo lectura está activo, así que no puedo hacer cambios en el equipo."
                : null;
    }

    private void marcausado(String sanchezaccion, String sanchezdetalle) {
        sanchezactividad.sanchezregistrar(sanchezaccion, sanchezdetalle); // K
        try { // K
            marcanotificador.accept(sanchezaccion);
        } catch (RuntimeException sancheze) { // K
            log.debug("El notificador falló", sancheze);
        } // Y
    }

    private String marcafallo(String sanchezque, Exception sancheze) {
        log.warn("Falló la herramienta al {}", sanchezque, sancheze);
        return "No pude " + sanchezque + ": " + (sancheze.getMessage() == null ? sancheze.getClass().getSimpleName() : sancheze.getMessage());
    }
}
