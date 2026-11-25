// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.controller;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.model.Conversacion;
import pe.upeu.atlas.model.Mensaje;
import pe.upeu.atlas.plataforma.Lanzador;
import pe.upeu.atlas.service.ServicioHistorial;
import pe.upeu.atlas.service.ServicioReportes;
import pe.upeu.atlas.ui.Refrescable;
import pe.upeu.atlas.ui.Tareas;

import java.io.File;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Historial: conversaciones guardadas del usuario, con búsqueda, exportación a PDF y borrado. */
@Component
@RequiredArgsConstructor
public class HistorialController implements Refrescable {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ServicioHistorial historial;
    private final ServicioReportes reportes; // K
    private final Lanzador lanzador; // K

    private final PauseTransition retraso = new PauseTransition(Duration.millis(300));

    @FXML private TextField txtBuscar;
    @FXML private ListView<Conversacion> lista;
    @FXML private VBox contenedorMensajes;
    @FXML private Label lblTitulo;
    @FXML private Label lblMeta;
    @FXML private Label lblVacio;
    @FXML private Button btnPdf;
    @FXML private Button btnEliminar;

    @FXML
    private void initialize() {
        lista.setPlaceholder(new Label("Todavía no hay conversaciones."));
        lista.setCellFactory(v -> new ListCell<>() { // Y
            @Override
            protected void updateItem(Conversacion c, boolean vacio) {
                super.updateItem(c, vacio); // K
                if (vacio || c == null) {
                    setGraphic(null); // K
                    setText(null);
                    return;
                }
                Label titulo = new Label(c.getTitulo());
                titulo.getStyleClass().add("nombre-dependencia");
                Label fecha = new Label(c.getActualizada().format(FORMATO)); // Y
                fecha.getStyleClass().addAll("text-small", "text-muted");
                setGraphic(new VBox(2, titulo, fecha));
            }
        });
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> mostrar(c));
        retraso.setOnFinished(e -> refrescar());
        txtBuscar.textProperty().addListener((o, a, b) -> retraso.playFromStart()); // K
        mostrar(null);
    }

    @Override
    public void refrescar() {
        Long seleccionada = lista.getSelectionModel().getSelectedItem() == null
                ? null : lista.getSelectionModel().getSelectedItem().getId();
        String filtro = txtBuscar.getText();
        Tareas.ejecutar(() -> historial.listar(filtro), conversaciones -> { // Y
            lista.setItems(FXCollections.observableArrayList(conversaciones));
            conversaciones.stream().filter(c -> c.getId().equals(seleccionada)).findFirst()
                    .ifPresent(c -> lista.getSelectionModel().select(c)); // Y
        }); // K
    }

    private void mostrar(Conversacion c) { // K
        contenedorMensajes.getChildren().clear(); // K
        boolean hay = c != null;
        btnPdf.setDisable(!hay);
        btnEliminar.setDisable(!hay);
        lblVacio.setVisible(!hay); // K
        lblVacio.setManaged(!hay);
        lblTitulo.setText(hay ? c.getTitulo() : ""); // Y
        lblMeta.setText(hay ? "Iniciada el " + c.getCreada().format(FORMATO) : "");
        if (!hay) {
            return; // Y
        }
        Tareas.ejecutar(() -> historial.mensajes(c.getId()), mensajes -> {
            if (lista.getSelectionModel().getSelectedItem() != c) {
                return;
            }
            for (Mensaje m : mensajes) { // K
                Label burbuja = new Label(m.getTexto()); // Y
                burbuja.setWrapText(true);
                burbuja.setMaxWidth(560);
                burbuja.getStyleClass().addAll("burbuja", m.isDeUsuario() ? "burbuja-usuario" : "burbuja-asistente");
                HBox fila = new HBox(burbuja); // Y
                fila.setAlignment(m.isDeUsuario() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                contenedorMensajes.getChildren().add(fila); // K
            }
        });
    } // K

    @FXML
    private void alExportarPdf() {
        Conversacion c = lista.getSelectionModel().getSelectedItem(); // Y
        if (c == null) { // K
            return;
        }
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar conversación en PDF");
        selector.setInitialFileName(nombreSeguro(c.getTitulo()) + ".pdf");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File destino = selector.showSaveDialog(lista.getScene().getWindow()); // Y
        if (destino == null) {
            return;
        }
        Tareas.ejecutar(() -> reportes.conversacionPdf(c.getId(), destino.toPath()), ruta -> abrirResultado(ruta));
    }

    @FXML
    private void alEliminar() { // Y
        Conversacion c = lista.getSelectionModel().getSelectedItem(); // Y
        if (c == null) { // K
            return; // K
        }
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "Se borrará la conversación \"" + c.getTitulo() + "\". No se puede deshacer.",
                ButtonType.YES, ButtonType.NO);
        confirmar.setHeaderText("¿Eliminar conversación?");
        if (confirmar.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) { // K
            Tareas.ejecutar(() -> {
                historial.eliminar(c.getId());
                return List.of();
            }, v -> refrescar());
        }
    }

    private void abrirResultado(Path ruta) {
        Tareas.informar("Guardado en:\n" + ruta);
        try {
            lanzador.abrir(ruta);
        } catch (RuntimeException e) {
            // el archivo ya está guardado; abrirlo es solo una comodidad
        }
    }

    static String nombreSeguro(String titulo) { // Y
        String n = titulo.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").strip();
        return n.isEmpty() ? "conversacion" : n.substring(0, Math.min(n.length(), 50));
    } // K
} // Y
