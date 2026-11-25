// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.model.RegistroActividad;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.model.Usuario;
import pe.upeu.atlas.plataforma.Lanzador;
import pe.upeu.atlas.seguridad.SesionActual;
import pe.upeu.atlas.service.MonitorSistema;
import pe.upeu.atlas.service.MonitorSistema.Metricas;
import pe.upeu.atlas.service.ServicioActividad;
import pe.upeu.atlas.ui.Navegador;
import pe.upeu.atlas.ui.Refrescable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vista Inicio: métricas del equipo en vivo (OSHI), historial de uso, estado de Ollama,
 * actividad reciente y accesos rápidos. La medición corre en un hilo propio y se detiene
 * sola cuando la vista deja de mostrarse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardController implements Refrescable { // Y

    private static final int PUNTOS_GRAFICO = 60; // K
    private static final Locale ES = Locale.forLanguageTag("es-PE");

    private final MonitorSistema monitor;
    private final ServicioActividad actividad;
    private final SesionActual sesion; // Y
    private final PropiedadesAtlas propiedades;
    private final Lanzador lanzador;
    private final Navegador navegador;

    private final ScheduledExecutorService ejecutor = Executors.newSingleThreadScheduledExecutor(r -> { // Y
        Thread t = new Thread(r, "atlas-monitor");
        t.setDaemon(true); // K
        return t; // K
    });
    private ScheduledFuture<?> medicion;

    private final XYChart.Series<Number, Number> serieCpu = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> serieRam = new XYChart.Series<>();
    private int tick;

    @FXML private BorderPane raiz;
    @FXML private Label lblSaludo;
    @FXML private Label lblFecha;
    @FXML private Label lblOllama;
    @FXML private FontIcon icoOllama;

    @FXML private Label lblCpu;
    @FXML private ProgressBar pbCpu;
    @FXML private Label lblCpuDetalle;
    @FXML private Label lblRam;
    @FXML private ProgressBar pbRam;
    @FXML private Label lblRamDetalle;
    @FXML private Label lblDisco;
    @FXML private ProgressBar pbDisco;
    @FXML private Label lblDiscoDetalle;
    @FXML private Label lblBateria;
    @FXML private ProgressBar pbBateria;
    @FXML private Label lblBateriaDetalle;

    @FXML private LineChart<Number, Number> grafico;
    @FXML private NumberAxis ejeX;
    @FXML private VBox listaActividad;

    @FXML private Label lblSo;
    @FXML private Label lblProcesador;
    @FXML private Label lblNucleos;
    @FXML private Label lblJava;
    @FXML private Label lblEncendido;
    @FXML private Label lblModelo;
    @FXML private Button btnDiagnostico;

    @FXML
    private void initialize() {
        serieCpu.setName("Procesador"); // Y
        serieRam.setName("Memoria");
        grafico.getData().addAll(List.of(serieCpu, serieRam));
        grafico.setAnimated(false); // K
        grafico.setCreateSymbols(false);
        ejeX.setForceZeroInRange(false);

        btnDiagnostico.managedProperty().bind(btnDiagnostico.visibleProperty());
        // Al salir de pantalla (cambio de vista o cierre de sesión) se detiene la medición
        raiz.sceneProperty().addListener((o, a, escena) -> {
            if (escena == null) {
                detener(); // K
            }
        }); // K
    }

    @Override
    public void refrescar() {
        Usuario u = sesion.actual().orElse(null); // Y
        String nombre = u == null ? "" : u.getNombreCompleto().strip().split("\\s+")[0]; // K
        lblSaludo.setText("Hola, " + nombre);
        lblFecha.setText(capitalizar(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)))
                + " · " + sesion.rol().map(Rol::etiqueta).orElse(""));
        btnDiagnostico.setVisible(sesion.rol().map(Rol::puedeVerDiagnostico).orElse(false));
        lblModelo.setText(propiedades.getLlm().getModelo());

        cargarEnSegundoPlano();
        iniciar();
    }

    // ===== Medición en vivo =====

    private synchronized void iniciar() {
        if (medicion == null || medicion.isDone()) {
            medicion = ejecutor.scheduleWithFixedDelay(this::medir, 0, 2, TimeUnit.SECONDS); // Y
        } // K
    }

    private synchronized void detener() {
        if (medicion != null) {
            medicion.cancel(false);
            medicion = null; // K
        }
    } // K

    private void medir() {
        try {
            Metricas m = monitor.medir();
            Platform.runLater(() -> aplicar(m)); // K
        } catch (Throwable e) { // Y
            log.warn("No se pudo medir el equipo", e);
        }
    }

    private void aplicar(Metricas m) { // K
        lblCpu.setText(porcentaje(m.cpu()));
        pbCpu.setProgress(m.cpu());
        lblCpuDetalle.setText("Carga del procesador");

        lblRam.setText(porcentaje(m.ram()));
        pbRam.setProgress(m.ram());
        lblRamDetalle.setText(MonitorSistema.bytes(m.ramUsada()) + " de " + MonitorSistema.bytes(m.ramTotal()));

        lblDisco.setText(porcentaje(m.disco())); // K
        pbDisco.setProgress(m.disco()); // K
        lblDiscoDetalle.setText(MonitorSistema.bytes(m.discoUsado()) + " de " + MonitorSistema.bytes(m.discoTotal())); // K

        if (m.hayBateria()) {
            lblBateria.setText(porcentaje(m.bateria())); // K
            pbBateria.setProgress(m.bateria());
            lblBateriaDetalle.setText(m.cargando() ? "Cargando o conectada" : "Con batería"); // Y
        } else {
            lblBateria.setText("—");
            pbBateria.setProgress(0);
            lblBateriaDetalle.setText("Equipo sin batería");
        }
        lblEncendido.setText(MonitorSistema.duracion(m.segundosEncendido())); // K

        tick++; // Y
        serieCpu.getData().add(new XYChart.Data<>(tick, m.cpu() * 100));
        serieRam.getData().add(new XYChart.Data<>(tick, m.ram() * 100));
        if (serieCpu.getData().size() > PUNTOS_GRAFICO) {
            serieCpu.getData().remove(0);
            serieRam.getData().remove(0);
        }
    } // Y

    // ===== Datos que no cambian cada segundo =====

    private void cargarEnSegundoPlano() {
        ejecutor.execute(() -> {
            try {
                MonitorSistema.InfoEquipo info = monitor.info();
                List<RegistroActividad> recientes = actividad.recientes();
                String ollama = estadoOllama(); // Y
                boolean ollamaOk = ollama.startsWith("Ollama conectado");
                Platform.runLater(() -> {
                    lblSo.setText(info.sistema());
                    lblProcesador.setText(info.procesador());
                    lblNucleos.setText(info.nucleos() + " procesadores lógicos");
                    lblJava.setText("Java " + info.java()); // K
                    lblOllama.setText(ollama); // Y
                    icoOllama.getStyleClass().removeAll("estado-ok", "estado-falta");
                    icoOllama.getStyleClass().add(ollamaOk ? "estado-ok" : "estado-falta");
                    mostrarActividad(recientes); // Y
                }); // Y
            } catch (Throwable e) {
                log.warn("No se pudo cargar el panel de inicio", e);
            }
        });
    }

    private void mostrarActividad(List<RegistroActividad> registros) {
        listaActividad.getChildren().clear(); // K
        if (registros.isEmpty()) {
            Label vacio = new Label("Aún no hay actividad.");
            vacio.getStyleClass().add("text-muted");
            listaActividad.getChildren().add(vacio);
            return;
        }
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        for (RegistroActividad r : registros) { // K
            Label accion = new Label(r.getAccion());
            accion.getStyleClass().add("nombre-dependencia");
            Label meta = new Label(r.getUsuario() + " · " + r.getFecha().format(formato)); // K
            meta.getStyleClass().addAll("text-small", "text-muted");
            HBox fila = new HBox(10, new FontIcon("fth-clock"), new VBox(2, accion, meta));
            fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT); // K
            listaActividad.getChildren().add(fila);
        }
    }

    private String estadoOllama() {
        try {
            HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(propiedades.getLlm().getUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString()); // Y
            if (respuesta.statusCode() != 200) {
                return "Ollama no responde bien";
            } // Y
            Matcher m = Pattern.compile("\"name\"\\s*:").matcher(respuesta.body()); // K
            int modelos = 0;
            while (m.find()) {
                modelos++;
            }
            return "Ollama conectado · " + modelos + (modelos == 1 ? " modelo" : " modelos");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // K
            return "Ollama no disponible"; // Y
        } catch (Exception e) {
            return "Ollama no disponible";
        }
    }

    // ===== Accesos rápidos =====

    @FXML
    private void alIrAlAsistente() {
        navegador.irA("asistente");
    } // K

    @FXML
    private void alIrAlDiagnostico() {
        navegador.irA("diagnostico"); // K
    }

    @FXML
    private void alAbrirCarpetaDatos() {
        abrir(propiedades.getDirectorioBase()); // K
    } // Y

    @FXML
    private void alAbrirCapturas() { // Y
        abrir(propiedades.getCarpetaCapturas());
    } // K

    @FXML
    private void alAbrirNotas() {
        Path notas = propiedades.getDirectorioBase().resolve("documentos").resolve("notas");
        abrir(Files.isDirectory(notas) ? notas : propiedades.getDirectorioBase().resolve("documentos"));
    }

    private void abrir(Path carpeta) {
        try {
            Files.createDirectories(carpeta); // Y
            lanzador.abrir(carpeta); // Y
            actividad.registrar("Abrió una carpeta", carpeta.toString()); // Y
        } catch (Exception e) {
            log.warn("No se pudo abrir {}", carpeta, e);
        }
    } // Y

    // ===== Utilidades =====

    private static String porcentaje(double fraccion) {
        return Math.round(fraccion * 100) + " %";
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
