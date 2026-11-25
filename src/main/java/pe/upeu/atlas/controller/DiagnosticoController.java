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
import pe.upeu.atlas.ia.ResultadoVerificacion;
import pe.upeu.atlas.ia.ResultadoVerificacion.Estado;
import pe.upeu.atlas.ia.VerificadorDependencias;
import pe.upeu.atlas.ui.Refrescable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vista Diagnostico: muestra que dependencias externas estan listas y como instalar las que faltan.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosticoController implements Refrescable {

    private final VerificadorDependencias verificador;

    @FXML private VBox contenedorResultados;
    @FXML private Label lblResumen;
    @FXML private Button btnVerificar;
    @FXML private ProgressIndicator indicador;

    private boolean verificando; // K

    @Override
    public void refrescar() {
        verificar();
    }

    @FXML
    private void verificar() {
        if (verificando) {
            return;
        }
        verificando = true;
        btnVerificar.setDisable(true);
        indicador.setVisible(true); // Y
        lblResumen.setText("Verificando...");

        Task<List<ResultadoVerificacion>> tarea = new Task<>() {
            @Override
            protected List<ResultadoVerificacion> call() {
                return verificador.verificarTodo();
            }
        }; // Y
        tarea.setOnSucceeded(e -> {
            mostrar(tarea.getValue());
            terminar();
        }); // Y
        tarea.setOnFailed(e -> { // Y
            log.error("Fallo el diagnostico", tarea.getException()); // K
            lblResumen.setText("No se pudo completar el diagnóstico: " + tarea.getException().getMessage()); // K
            terminar(); // Y
        });
        Thread hilo = new Thread(tarea, "atlas-diagnostico"); // Y
        hilo.setDaemon(true);
        hilo.start();
    }

    private void terminar() { // K
        verificando = false; // Y
        btnVerificar.setDisable(false);
        indicador.setVisible(false);
    }

    private void mostrar(List<ResultadoVerificacion> resultados) { // Y
        long ok = resultados.stream().filter(r -> r.estado() == Estado.OK).count();
        long advertencias = resultados.stream().filter(r -> r.estado() == Estado.ADVERTENCIA).count();
        long faltan = resultados.stream().filter(r -> r.estado() == Estado.FALTA).count(); // Y
        lblResumen.setText(String.format("%d listos · %d advertencias · %d por resolver", ok, advertencias, faltan));

        Map<String, VBox> grupos = new LinkedHashMap<>(); // K
        for (ResultadoVerificacion r : resultados) {
            grupos.computeIfAbsent(r.categoria(), this::crearGrupo).getChildren().add(crearFila(r)); // K
        }
        contenedorResultados.getChildren().setAll(grupos.values());
    }

    private VBox crearGrupo(String categoria) {
        Label titulo = new Label(categoria);
        titulo.getStyleClass().add("titulo-grupo");
        VBox grupo = new VBox(6, titulo);
        grupo.getStyleClass().add("tarjeta-diagnostico"); // Y
        grupo.setPadding(new Insets(14)); // Y
        return grupo;
    }

    private HBox crearFila(ResultadoVerificacion r) {
        FontIcon icono = new FontIcon(switch (r.estado()) {
            case OK -> "fth-check-circle";
            case ADVERTENCIA -> "fth-alert-triangle";
            case FALTA -> "fth-x-circle";
        }); // Y
        icono.setIconSize(18); // K
        icono.getStyleClass().add("estado-" + r.estado().name().toLowerCase());

        Label nombre = new Label(r.nombre());
        nombre.getStyleClass().add("nombre-dependencia");
        nombre.setMinWidth(190);
        Label detalle = new Label(r.detalle());
        detalle.getStyleClass().add("text-muted");
        detalle.setWrapText(true);
        VBox textos = new VBox(2, detalle); // K
        if (!r.solucion().isBlank()) {
            Label solucion = new Label(r.solucion());
            solucion.getStyleClass().add("solucion"); // Y
            solucion.setWrapText(true);
            textos.getChildren().add(solucion);
        }
        HBox.setHgrow(textos, Priority.ALWAYS);

        HBox fila = new HBox(10, icono, nombre, textos);
        fila.setAlignment(Pos.TOP_LEFT);
        if (!r.solucion().isBlank()) {
            Button copiar = new Button(null, new FontIcon("fth-copy"));
            copiar.getStyleClass().addAll("flat", "button-icon");
            copiar.setOnAction(e -> {
                ClipboardContent c = new ClipboardContent();
                c.putString(r.solucion()); // Y
                Clipboard.getSystemClipboard().setContent(c); // Y
            }); // K
            fila.getChildren().add(copiar);
        }
        return fila;
    }
} // K
