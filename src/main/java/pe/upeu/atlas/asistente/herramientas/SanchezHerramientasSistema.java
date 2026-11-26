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

    private final SanchezPropiedadesAtlas marcapropiedades;
    private final MarcaMonitorSistema marcamonitor;
    private final SanchezLanzador sanchezlanzador;
    private final SanchezConfirmadorAcciones marcaconfirmador;
    private final SanchezServicioActividad sanchezactividad;
    private final SanchezServicioDocumentos sanchezdocumentos;

    public static final ThreadLocal<java.util.concurrent.atomic.AtomicBoolean> TURNO = new ThreadLocal<>();
    static final String MENSAJE_CANCELADO = "El usuario interrumpió la petición. No hagas nada más con esa petición.";

    static boolean marcacancelado() {
        java.util.concurrent.atomic.AtomicBoolean sanchezc = TURNO.get();
        return sanchezc != null && sanchezc.get();
    }

    private volatile Consumer<String> marcanotificador = marcat -> { };

    public void marcasetNotificador(Consumer<String> marcanotificador) {
        this.marcanotificador = marcanotificador == null ? marcat -> { } : marcanotificador;
    }

    @Tool("Dice la fecha y la hora actuales. Úsala cuando pregunten qué hora o qué día es.")
    public String fechaYHora() {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO;
        }
        String marcatexto = LocalDateTime.now().format(FECHA_HORA);
        marcausado("Consultó la fecha y hora", marcatexto);
        return marcatexto;
    }

    @Tool("Informa el estado del equipo: uso de procesador, memoria, disco y batería.")
    public String estadoDelEquipo() {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO;
        }
        try {
            MarcaMonitorSistema.MarcaMetricas sanchezm = marcamonitor.sanchezmedir();
            StringBuilder sanchezsb = new StringBuilder()
                    .append("Procesador al ").append(Math.round(sanchezm.marcacpu() * 100)).append("%. ")
                    .append("Memoria: ").append(MarcaMonitorSistema.sanchezbytes(sanchezm.marcaramUsada())).append(" usados de ")
                    .append(MarcaMonitorSistema.sanchezbytes(sanchezm.marcaramTotal())).append(". ")
                    .append("Disco: ").append(MarcaMonitorSistema.sanchezbytes(sanchezm.marcadiscoUsado())).append(" usados de ")
                    .append(MarcaMonitorSistema.sanchezbytes(sanchezm.marcadiscoTotal())).append(". ");
            if (sanchezm.marcahayBateria()) {
                sanchezsb.append("Batería al ").append(Math.round(sanchezm.marcabateria() * 100)).append("%")
                        .append(sanchezm.sanchezcargando() ? " y cargando." : ".");
            } else {
                sanchezsb.append("El equipo no tiene batería.");
            }
            marcausado("Consultó el estado del equipo", null);
            return sanchezsb.toString();
        } catch (RuntimeException sancheze) {
            return marcafallo("leer el estado del equipo", sancheze);
        }
    }

    @Tool("Calcula una operación matemática con números, + - * / % ^, paréntesis y raiz(x). "
            + "Úsala siempre para cualquier cuenta; no calcules de memoria.")
    public String calcular(@P("La operación, por ejemplo 15*12+3") String operacion) {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO;
        }
        try {
            String sanchezresultado = SanchezCalculadora.sanchezformatear(SanchezCalculadora.marcaevaluar(operacion));
            marcausado("Calculó", operacion + " = " + sanchezresultado);
            return operacion + " = " + sanchezresultado;
        } catch (SanchezNegocioException sancheze) {
            return "No pude calcularlo: " + sancheze.getMessage();
        }
    }

    @Tool("Busca información en los documentos que el usuario importó (apuntes, PDF, Word, imágenes). "
            + "Úsala cuando pregunten por el contenido de sus documentos, apuntes o tareas. "
            + "Responde solo con lo que devuelva; si no hay nada relevante, dilo.")
    public String buscarEnDocumentos(@P("La pregunta o el tema a buscar") String consulta) {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO;
        }
        try {
            List<SanchezServicioDocumentos.SanchezResultado> marcaresultados = sanchezdocumentos.buscar(consulta, 3);
            marcausado("Buscó en tus documentos", consulta);
            if (marcaresultados.isEmpty()) {
                return "No tienes documentos importados. Puedes agregarlos en la vista Documentos.";
            }
            StringBuilder sanchezsb = new StringBuilder("Fragmentos encontrados:\n");
            int marcan = 1;
            for (SanchezServicioDocumentos.SanchezResultado marcar : marcaresultados) {
                String marcat = marcar.texto().length() > 700 ? marcar.texto().substring(0, 700) + "…" : marcar.texto();
                sanchezsb.append('[').append(marcan++).append("] (").append(marcar.marcadocumento()).append(") ").append(marcat).append('\n');
            }
            return sanchezsb.toString();
        } catch (SanchezNegocioException | SanchezPermisoDenegadoException sancheze) {
            return sancheze.getMessage();
        } catch (RuntimeException sancheze) {
            return marcafallo("buscar en tus documentos", sancheze);
        }
    }

    @Tool("Abre una aplicación de una lista fija: calculadora, bloc de notas, paint, explorador o navegador.")
    public String abrirAplicacion(@P("Nombre de la aplicación: calculadora, bloc de notas, paint, explorador o navegador") String nombre) {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
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
            marcausado("Abrió la aplicación " + sanchezclave, null);
            return "Abrí " + sanchezclave + ".";
        } catch (RuntimeException sancheze) {
            return marcafallo("abrir la aplicación", sancheze);
        }
    }

    @Tool("Cambia el volumen del equipo. Acción: subir, bajar o silenciar. Cada paso mueve el volumen un 2%.")
    public String controlarVolumen(@P("subir, bajar o silenciar") String accion,
                                   @P("Cantidad de pasos de 1 a 25; si no se indica, 5") Integer pasos) {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        if (sanchezlanzador.marcasistema() != SanchezSistemaOperativo.WINDOWS) {
            return "El control de volumen solo está disponible en Windows por ahora.";
        }
        int marcan = Math.max(1, Math.min(25, pasos == null ? 5 : pasos));
        String sancheza = sancheznormalizar(accion);
        int sancheztecla;
        switch (sancheza) {
            case "subir", "sube", "aumentar" -> sancheztecla = 175;
            case "bajar", "baja", "disminuir" -> sancheztecla = 174;
            case "silenciar", "silencio", "mute" -> {
                sancheztecla = 173;
                marcan = 1;
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
        } catch (RuntimeException sancheze) {
            return marcafallo("cambiar el volumen", sancheze);
        }
    }

    @Tool("Toma una captura de toda la pantalla y la guarda en la carpeta de capturas de Atlas.")
    public String tomarCaptura() {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return "Este equipo no tiene pantalla disponible para capturar.";
        }
        try {
            Rectangle sanchezarea = new Rectangle();
            for (GraphicsDevice marcad : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                sanchezarea = sanchezarea.union(marcad.getDefaultConfiguration().getBounds());
            }
            BufferedImage sanchezimagen = new Robot().createScreenCapture(sanchezarea);
            Path marcacarpeta = marcapropiedades.getCarpetaCapturas();
            Files.createDirectories(marcacarpeta);
            Path marcaarchivo = marcacarpeta.resolve("captura-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".png");
            ImageIO.write(sanchezimagen, "png", marcaarchivo.toFile());
            marcausado("Tomó una captura de pantalla", marcaarchivo.toString());
            return "Guardé la captura en la carpeta de capturas de Atlas: " + marcaarchivo.getFileName() + ".";
        } catch (Exception sancheze) {
            return marcafallo("tomar la captura", sancheze);
        }
    }

    @Tool("Guarda una nota de texto en la carpeta de documentos de Atlas.")
    public String guardarNota(@P("Título corto de la nota") String titulo, @P("Contenido de la nota") String texto) {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        if (texto == null || texto.isBlank()) {
            return "La nota está vacía; dime qué quieres guardar.";
        }
        try {
            Path marcacarpeta = marcapropiedades.getDirectorioBase().resolve("documentos").resolve("notas");
            Files.createDirectories(marcacarpeta);
            String sanchezbase = sancheznormalizar(titulo == null || titulo.isBlank() ? "nota" : titulo)
                    .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            if (sanchezbase.isEmpty()) {
                sanchezbase = "nota";
            }
            sanchezbase = sanchezbase.substring(0, Math.min(sanchezbase.length(), 40));
            Path marcaarchivo = marcacarpeta.resolve(sanchezbase + "-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
            Files.writeString(marcaarchivo, (titulo == null ? "" : titulo.strip() + "\n\n") + texto.strip() + "\n",
                    StandardCharsets.UTF_8);
            marcausado("Guardó una nota", marcaarchivo.toString());
            return "Guardé la nota en " + marcaarchivo.getFileName() + ".";
        } catch (IOException | RuntimeException sancheze) {
            return marcafallo("guardar la nota", sancheze);
        }
    }

    @Tool("Bloquea la pantalla del equipo. Solo úsala si el usuario lo pidió de forma explícita; antes pide su confirmación.")
    public String bloquearPantalla() {
        if (marcacancelado()) {
            return MENSAJE_CANCELADO;
        }
        String sanchezbloqueo = marcabloqueoSoloLectura();
        if (sanchezbloqueo != null) {
            return sanchezbloqueo;
        }
        if (sanchezlanzador.marcasistema() != SanchezSistemaOperativo.WINDOWS) {
            return "Bloquear la pantalla solo está disponible en Windows por ahora.";
        }
        if (!marcaconfirmador.sanchezconfirmar("¿Bloquear la pantalla?", "Atlas quiere bloquear la pantalla del equipo.")) {
            marcausado("Bloqueo de pantalla cancelado", null);
            return "Cancelé el bloqueo porque no lo confirmaste.";
        }
        try {
            sanchezlanzador.sanchezejecutar("rundll32.exe", "user32.dll,LockWorkStation");
            marcausado("Bloqueó la pantalla", null);
            return "Pantalla bloqueada.";
        } catch (RuntimeException sancheze) {
            return marcafallo("bloquear la pantalla", sancheze);
        }
    }

    static String sancheznormalizar(String marcatexto) {
        if (marcatexto == null) {
            return "";
        }
        return Normalizer.normalize(marcatexto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .strip();
    }

    private String marcabloqueoSoloLectura() {
        return marcapropiedades.isModoSoloLectura()
                ? "El modo solo lectura está activo, así que no puedo hacer cambios en el equipo."
                : null;
    }

    private void marcausado(String sanchezaccion, String sanchezdetalle) {
        sanchezactividad.sanchezregistrar(sanchezaccion, sanchezdetalle);
        try {
            marcanotificador.accept(sanchezaccion);
        } catch (RuntimeException sancheze) {
            log.debug("El notificador falló", sancheze);
        }
    }

    private String marcafallo(String sanchezque, Exception sancheze) {
        log.warn("Falló la herramienta al {}", sanchezque, sancheze);
        return "No pude " + sanchezque + ": " + (sancheze.getMessage() == null ? sancheze.getClass().getSimpleName() : sancheze.getMessage());
    }
}
