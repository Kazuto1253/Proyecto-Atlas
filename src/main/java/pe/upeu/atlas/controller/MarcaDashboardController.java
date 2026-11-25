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
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.model.MarcaRegistroActividad;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.model.MarcaUsuario;
import pe.upeu.atlas.plataforma.SanchezLanzador;
import pe.upeu.atlas.seguridad.SanchezSesionActual;
import pe.upeu.atlas.service.MarcaMonitorSistema;
import pe.upeu.atlas.service.MarcaMonitorSistema.MarcaMetricas;
import pe.upeu.atlas.service.SanchezServicioActividad;
import pe.upeu.atlas.ui.SanchezNavegador;
import pe.upeu.atlas.ui.MarcaRefrescable;

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
public class MarcaDashboardController implements MarcaRefrescable { // Y

    private static final int PUNTOS_GRAFICO = 60; // K
    private static final Locale ES = Locale.forLanguageTag("es-PE");

    private final MarcaMonitorSistema marcamonitor;
    private final SanchezServicioActividad sanchezactividad;
    private final SanchezSesionActual sanchezsesion; // Y
    private final SanchezPropiedadesAtlas marcapropiedades;
    private final SanchezLanzador sanchezlanzador;
    private final SanchezNavegador sancheznavegador;

    private final ScheduledExecutorService sanchezejecutor = Executors.newSingleThreadScheduledExecutor(marcar -> { // Y
        Thread marcat = new Thread(marcar, "atlas-monitor");
        marcat.setDaemon(true); // K
        return marcat; // K
    });
    private ScheduledFuture<?> marcamedicion;

    private final XYChart.Series<Number, Number> marcaserieCpu = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> marcaserieRam = new XYChart.Series<>();
    private int sancheztick;

    @FXML private BorderPane marcaraiz;
    @FXML private Label marcalblSaludo;
    @FXML private Label sanchezlblFecha;
    @FXML private Label marcalblOllama;
    @FXML private FontIcon sanchezicoOllama;

    @FXML private Label marcalblCpu;
    @FXML private ProgressBar marcapbCpu;
    @FXML private Label sanchezlblCpuDetalle;
    @FXML private Label marcalblRam;
    @FXML private ProgressBar marcapbRam;
    @FXML private Label sanchezlblRamDetalle;
    @FXML private Label marcalblDisco;
    @FXML private ProgressBar marcapbDisco;
    @FXML private Label sanchezlblDiscoDetalle;
    @FXML private Label marcalblBateria;
    @FXML private ProgressBar marcapbBateria;
    @FXML private Label sanchezlblBateriaDetalle;

    @FXML private LineChart<Number, Number> sanchezgrafico;
    @FXML private NumberAxis marcaejeX;
    @FXML private VBox marcalistaActividad;

    @FXML private Label marcalblSo;
    @FXML private Label marcalblProcesador;
    @FXML private Label sanchezlblNucleos;
    @FXML private Label marcalblJava;
    @FXML private Label sanchezlblEncendido;
    @FXML private Label marcalblModelo;
    @FXML private Button marcabtnDiagnostico;

    @FXML
    private void initialize() {
        marcaserieCpu.setName("Procesador"); // Y
        marcaserieRam.setName("Memoria");
        sanchezgrafico.getData().addAll(List.of(marcaserieCpu, marcaserieRam));
        sanchezgrafico.setAnimated(false); // K
        sanchezgrafico.setCreateSymbols(false);
        marcaejeX.setForceZeroInRange(false);

        marcabtnDiagnostico.managedProperty().bind(marcabtnDiagnostico.visibleProperty());
        // Al salir de pantalla (cambio de vista o cierre de sesión) se detiene la medición
        marcaraiz.sceneProperty().addListener((sanchezo, sancheza, sanchezescena) -> {
            if (sanchezescena == null) {
                sanchezdetener(); // K
            }
        }); // K
    }

    @Override
    public void sanchezrefrescar() {
        MarcaUsuario sanchezu = sanchezsesion.marcaactual().orElse(null); // Y
        String sancheznombre = sanchezu == null ? "" : sanchezu.getNombreCompleto().strip().split("\\s+")[0]; // K
        marcalblSaludo.setText("Hola, " + sancheznombre);
        sanchezlblFecha.setText(marcacapitalizar(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)))
                + " · " + sanchezsesion.sanchezrol().map(SanchezRol::marcaetiqueta).orElse(""));
        marcabtnDiagnostico.setVisible(sanchezsesion.sanchezrol().map(SanchezRol::marcapuedeVerDiagnostico).orElse(false));
        marcalblModelo.setText(marcapropiedades.getLlm().getModelo());

        marcacargarEnSegundoPlano();
        sancheziniciar();
    }

    // ===== Medición en vivo =====

    private synchronized void sancheziniciar() {
        if (marcamedicion == null || marcamedicion.isDone()) {
            marcamedicion = sanchezejecutor.scheduleWithFixedDelay(this::sanchezmedir, 0, 2, TimeUnit.SECONDS); // Y
        } // K
    }

    private synchronized void sanchezdetener() {
        if (marcamedicion != null) {
            marcamedicion.cancel(false);
            marcamedicion = null; // K
        }
    } // K

    private void sanchezmedir() {
        try {
            MarcaMetricas sanchezm = marcamonitor.sanchezmedir();
            Platform.runLater(() -> marcaaplicar(sanchezm)); // K
        } catch (Throwable sancheze) { // Y
            log.warn("No se pudo medir el equipo", sancheze);
        }
    }

    private void marcaaplicar(MarcaMetricas sanchezm) { // K
        marcalblCpu.setText(sanchezporcentaje(sanchezm.marcacpu()));
        marcapbCpu.setProgress(sanchezm.marcacpu());
        sanchezlblCpuDetalle.setText("Carga del procesador");

        marcalblRam.setText(sanchezporcentaje(sanchezm.marcaram()));
        marcapbRam.setProgress(sanchezm.marcaram());
        sanchezlblRamDetalle.setText(MarcaMonitorSistema.sanchezbytes(sanchezm.marcaramUsada()) + " de " + MarcaMonitorSistema.sanchezbytes(sanchezm.marcaramTotal()));

        marcalblDisco.setText(sanchezporcentaje(sanchezm.marcadisco())); // K
        marcapbDisco.setProgress(sanchezm.marcadisco()); // K
        sanchezlblDiscoDetalle.setText(MarcaMonitorSistema.sanchezbytes(sanchezm.marcadiscoUsado()) + " de " + MarcaMonitorSistema.sanchezbytes(sanchezm.marcadiscoTotal())); // K

        if (sanchezm.marcahayBateria()) {
            marcalblBateria.setText(sanchezporcentaje(sanchezm.marcabateria())); // K
            marcapbBateria.setProgress(sanchezm.marcabateria());
            sanchezlblBateriaDetalle.setText(sanchezm.sanchezcargando() ? "Cargando o conectada" : "Con batería"); // Y
        } else {
            marcalblBateria.setText("—");
            marcapbBateria.setProgress(0);
            sanchezlblBateriaDetalle.setText("Equipo sin batería");
        }
        sanchezlblEncendido.setText(MarcaMonitorSistema.sanchezduracion(sanchezm.sanchezsegundosEncendido())); // K

        sancheztick++; // Y
        marcaserieCpu.getData().add(new XYChart.Data<>(sancheztick, sanchezm.marcacpu() * 100));
        marcaserieRam.getData().add(new XYChart.Data<>(sancheztick, sanchezm.marcaram() * 100));
        if (marcaserieCpu.getData().size() > PUNTOS_GRAFICO) {
            marcaserieCpu.getData().remove(0);
            marcaserieRam.getData().remove(0);
        }
    } // Y

    // ===== Datos que no cambian cada segundo =====

    private void marcacargarEnSegundoPlano() {
        sanchezejecutor.execute(() -> {
            try {
                MarcaMonitorSistema.SanchezInfoEquipo marcainfo = marcamonitor.info();
                List<MarcaRegistroActividad> marcarecientes = sanchezactividad.marcarecientes();
                String marcaollama = marcaestadoOllama(); // Y
                boolean marcaollamaOk = marcaollama.startsWith("Ollama conectado");
                Platform.runLater(() -> {
                    marcalblSo.setText(marcainfo.marcasistema());
                    marcalblProcesador.setText(marcainfo.marcaprocesador());
                    sanchezlblNucleos.setText(marcainfo.sancheznucleos() + " procesadores lógicos");
                    marcalblJava.setText("Java " + marcainfo.marcajava()); // K
                    marcalblOllama.setText(marcaollama); // Y
                    sanchezicoOllama.getStyleClass().removeAll("estado-ok", "estado-falta");
                    sanchezicoOllama.getStyleClass().add(marcaollamaOk ? "estado-ok" : "estado-falta");
                    sanchezmostrarActividad(marcarecientes); // Y
                }); // Y
            } catch (Throwable sancheze) {
                log.warn("No se pudo cargar el panel de inicio", sancheze);
            }
        });
    }

    private void sanchezmostrarActividad(List<MarcaRegistroActividad> marcaregistros) {
        marcalistaActividad.getChildren().clear(); // K
        if (marcaregistros.isEmpty()) {
            Label marcavacio = new Label("Aún no hay actividad.");
            marcavacio.getStyleClass().add("text-muted");
            marcalistaActividad.getChildren().add(marcavacio);
            return;
        }
        DateTimeFormatter marcaformato = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        for (MarcaRegistroActividad marcar : marcaregistros) { // K
            Label sanchezaccion = new Label(marcar.getAccion());
            sanchezaccion.getStyleClass().add("nombre-dependencia");
            Label sanchezmeta = new Label(marcar.getUsuario() + " · " + marcar.getFecha().format(marcaformato)); // K
            sanchezmeta.getStyleClass().addAll("text-small", "text-muted");
            HBox marcafila = new HBox(10, new FontIcon("fth-clock"), new VBox(2, sanchezaccion, sanchezmeta));
            marcafila.setAlignment(javafx.geometry.Pos.CENTER_LEFT); // K
            marcalistaActividad.getChildren().add(marcafila);
        }
    }

    private String marcaestadoOllama() {
        try {
            HttpClient marcacliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest sanchezpeticion = HttpRequest.newBuilder(URI.create(marcapropiedades.getLlm().getUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> marcarespuesta = marcacliente.send(sanchezpeticion, HttpResponse.BodyHandlers.ofString()); // Y
            if (marcarespuesta.statusCode() != 200) {
                return "Ollama no responde bien";
            } // Y
            Matcher sanchezm = Pattern.compile("\"name\"\\s*:").matcher(marcarespuesta.body()); // K
            int sanchezmodelos = 0;
            while (sanchezm.find()) {
                sanchezmodelos++;
            }
            return "Ollama conectado · " + sanchezmodelos + (sanchezmodelos == 1 ? " modelo" : " modelos");
        } catch (InterruptedException sancheze) {
            Thread.currentThread().interrupt(); // K
            return "Ollama no disponible"; // Y
        } catch (Exception sancheze) {
            return "Ollama no disponible";
        }
    }

    // ===== Accesos rápidos =====

    @FXML
    private void sanchezalIrAlAsistente() {
        sancheznavegador.marcairA("asistente");
    } // K

    @FXML
    private void sanchezalIrAlDiagnostico() {
        sancheznavegador.marcairA("diagnostico"); // K
    }

    @FXML
    private void marcaalAbrirCarpetaDatos() {
        marcaabrir(marcapropiedades.getDirectorioBase()); // K
    } // Y

    @FXML
    private void marcaalAbrirCapturas() { // Y
        marcaabrir(marcapropiedades.getCarpetaCapturas());
    } // K

    @FXML
    private void marcaalAbrirNotas() {
        Path sancheznotas = marcapropiedades.getDirectorioBase().resolve("documentos").resolve("notas");
        marcaabrir(Files.isDirectory(sancheznotas) ? sancheznotas : marcapropiedades.getDirectorioBase().resolve("documentos"));
    }

    private void marcaabrir(Path marcacarpeta) {
        try {
            Files.createDirectories(marcacarpeta); // Y
            sanchezlanzador.marcaabrir(marcacarpeta); // Y
            sanchezactividad.sanchezregistrar("Abrió una carpeta", marcacarpeta.toString()); // Y
        } catch (Exception sancheze) {
            log.warn("No se pudo abrir {}", marcacarpeta, sancheze);
        }
    } // Y

    // ===== Utilidades =====

    private static String sanchezporcentaje(double sanchezfraccion) {
        return Math.round(sanchezfraccion * 100) + " %";
    }

    private static String marcacapitalizar(String sanchezs) {
        return sanchezs.isEmpty() ? sanchezs : Character.toUpperCase(sanchezs.charAt(0)) + sanchezs.substring(1);
    }
}
