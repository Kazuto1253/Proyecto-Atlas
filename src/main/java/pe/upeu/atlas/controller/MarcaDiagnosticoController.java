// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.ia.SanchezResultadoVerificacion;
import pe.upeu.atlas.ia.SanchezResultadoVerificacion.MarcaEstado;
import pe.upeu.atlas.ia.SanchezVerificadorDependencias;
import pe.upeu.atlas.ui.MarcaRefrescable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarcaDiagnosticoController implements MarcaRefrescable {

    private final SanchezVerificadorDependencias marcaverificador;

    @FXML private VBox sanchezcontenedorResultados;
    @FXML private Label sanchezlblResumen;
    @FXML private Button sanchezbtnVerificar;
    @FXML private ProgressIndicator sanchezindicador;

    private boolean marcaverificando; // K

    @Override
    public void sanchezrefrescar() {
        sanchezverificar();
    }

    @FXML
    private void sanchezverificar() {
        if (marcaverificando) {
            return;
        }
        marcaverificando = true;
        sanchezbtnVerificar.setDisable(true);
        sanchezindicador.setVisible(true); // Y
        sanchezlblResumen.setText("Verificando...");

        Task<List<SanchezResultadoVerificacion>> sancheztarea = new Task<>() {
            @Override
            protected List<SanchezResultadoVerificacion> call() {
                return marcaverificador.sanchezverificarTodo();
            }
        }; // Y
        sancheztarea.setOnSucceeded(sancheze -> {
            marcamostrar(sancheztarea.getValue());
            marcaterminar();
        }); // Y
        sancheztarea.setOnFailed(sancheze -> { // Y
            log.error("Fallo el diagnostico", sancheztarea.getException()); // K
            sanchezlblResumen.setText("No se pudo completar el diagnóstico: " + sancheztarea.getException().getMessage()); // K
            marcaterminar(); // Y
        });
        Thread marcahilo = new Thread(sancheztarea, "atlas-diagnostico"); // Y
        marcahilo.setDaemon(true);
        marcahilo.start();
    }

    private void marcaterminar() { // K
        marcaverificando = false; // Y
        sanchezbtnVerificar.setDisable(false);
        sanchezindicador.setVisible(false);
    }

    private void marcamostrar(List<SanchezResultadoVerificacion> marcaresultados) { // Y
        long marcaok = marcaresultados.stream().filter(marcar -> marcar.marcaestado() == MarcaEstado.OK).count();
        long sanchezadvertencias = marcaresultados.stream().filter(marcar -> marcar.marcaestado() == MarcaEstado.ADVERTENCIA).count();
        long marcafaltan = marcaresultados.stream().filter(marcar -> marcar.marcaestado() == MarcaEstado.FALTA).count(); // Y
        sanchezlblResumen.setText(String.format("%d listos · %d advertencias · %d por resolver", marcaok, sanchezadvertencias, marcafaltan));

        Map<String, VBox> marcagrupos = new LinkedHashMap<>(); // K
        for (SanchezResultadoVerificacion marcar : marcaresultados) {
            marcagrupos.computeIfAbsent(marcar.sanchezcategoria(), this::marcacrearGrupo).getChildren().add(sanchezcrearFila(marcar)); // K
        }
        sanchezcontenedorResultados.getChildren().setAll(marcagrupos.values());
    }

    private VBox marcacrearGrupo(String sanchezcategoria) {
        Label sancheztitulo = new Label(sanchezcategoria);
        sancheztitulo.getStyleClass().add("titulo-grupo");
        VBox sanchezgrupo = new VBox(6, sancheztitulo);
        sanchezgrupo.getStyleClass().add("tarjeta-diagnostico"); // Y
        sanchezgrupo.setPadding(new Insets(14)); // Y
        return sanchezgrupo;
    }

    private HBox sanchezcrearFila(SanchezResultadoVerificacion marcar) {
        FontIcon marcaicono = new FontIcon(switch (marcar.marcaestado()) {
            case OK -> "fth-check-circle";
            case ADVERTENCIA -> "fth-alert-triangle";
            case FALTA -> "fth-x-circle";
        }); // Y
        marcaicono.setIconSize(18); // K
        marcaicono.getStyleClass().add("estado-" + marcar.marcaestado().name().toLowerCase());

        Label sancheznombre = new Label(marcar.nombre());
        sancheznombre.getStyleClass().add("nombre-dependencia");
        sancheznombre.setMinWidth(190);
        Label sanchezdetalle = new Label(marcar.detalle());
        sanchezdetalle.getStyleClass().add("text-muted");
        sanchezdetalle.setWrapText(true);
        VBox sancheztextos = new VBox(2, sanchezdetalle); // K
        if (!marcar.marcasolucion().isBlank()) {
            Label marcasolucion = new Label(marcar.marcasolucion());
            marcasolucion.getStyleClass().add("solucion"); // Y
            marcasolucion.setWrapText(true);
            sancheztextos.getChildren().add(marcasolucion);
        }
        HBox.setHgrow(sancheztextos, Priority.ALWAYS);

        HBox marcafila = new HBox(10, marcaicono, sancheznombre, sancheztextos);
        marcafila.setAlignment(Pos.TOP_LEFT);
        if (!marcar.marcasolucion().isBlank()) {
            Button marcacopiar = new Button(null, new FontIcon("fth-copy"));
            marcacopiar.getStyleClass().addAll("flat", "button-icon");
            marcacopiar.setOnAction(sancheze -> {
                ClipboardContent sanchezc = new ClipboardContent();
                sanchezc.putString(marcar.marcasolucion()); // Y
                Clipboard.getSystemClipboard().setContent(sanchezc); // Y
            }); // K
            marcafila.getChildren().add(marcacopiar);
        }
        return marcafila;
    }
} // K
