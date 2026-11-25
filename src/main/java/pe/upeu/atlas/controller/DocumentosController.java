// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.model.Documento;
import pe.upeu.atlas.service.ExtractorTexto;
import pe.upeu.atlas.service.ServicioDocumentos;
import pe.upeu.atlas.ui.Refrescable;
import pe.upeu.atlas.ui.Tareas;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Biblioteca personal: importar PDF, Word, texto e imágenes (OCR) y buscar en ellos por significado. */
@Component
@RequiredArgsConstructor
public class DocumentosController implements Refrescable { // K

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ServicioDocumentos servicio;

    @FXML private TableView<Documento> tabla;
    @FXML private TableColumn<Documento, String> colNombre;
    @FXML private TableColumn<Documento, String> colTipo;
    @FXML private TableColumn<Documento, String> colFragmentos;
    @FXML private TableColumn<Documento, String> colFecha;
    @FXML private Button btnImportar;
    @FXML private Button btnEliminar;
    @FXML private TextField txtConsulta;
    @FXML private Button btnBuscar;
    @FXML private VBox contenedorResultados;
    @FXML private Label lblEstado;
    @FXML private ProgressIndicator indicador;

    @FXML
    private void initialize() {
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo().toUpperCase()));
        colFragmentos.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getFragmentos()))); // Y
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFecha().format(FORMATO)));
        tabla.setPlaceholder(new Label("Importa tus apuntes para poder consultarlos."));
        btnEliminar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        indicador.managedProperty().bind(indicador.visibleProperty()); // Y
        indicador.setVisible(false); // Y
    } // Y

    @Override
    public void refrescar() {
        Tareas.ejecutar(servicio::listar, docs -> { // Y
            tabla.setItems(FXCollections.observableArrayList(docs));
            lblEstado.setText(docs.size() + (docs.size() == 1 ? " documento" : " documentos")); // Y
        }); // K
    } // Y

    @FXML
    private void alImportar() {
        FileChooser selector = new FileChooser(); // Y
        selector.setTitle("Elige un documento");
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos e imágenes",
                ExtractorTexto.TIPOS.stream().sorted().map(t -> "*." + t).toList()));
        File archivo = selector.showOpenDialog(tabla.getScene().getWindow());
        if (archivo == null) { // K
            return;
        }
        ocupado(true, "Procesando \"" + archivo.getName() + "\" (lectura, OCR y embeddings)..."); // K
        Tareas.ejecutar(() -> servicio.importar(archivo.toPath()), d -> { // Y
            ocupado(false, null); // Y
            refrescar();
            Tareas.informar("Listo: \"" + d.getNombre() + "\" quedó en " + d.getFragmentos()
                    + " fragmentos. Ya puedes preguntarle a Atlas por su contenido.");
        }, causa -> {
            ocupado(false, null);
            refrescar(); // K
            Tareas.mostrarError(causa);
        });
    } // Y

    @FXML
    private void alEliminar() {
        Documento d = tabla.getSelectionModel().getSelectedItem(); // Y
        if (d == null) {
            return;
        }
        Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "Se quitará \"" + d.getNombre() + "\" de tu biblioteca (el archivo original no se toca).",
                ButtonType.YES, ButtonType.NO);
        confirmar.setHeaderText("¿Quitar documento?"); // K
        if (confirmar.showAndWait().filter(b -> b == ButtonType.YES).isPresent()) {
            Tareas.ejecutar(() -> { // K
                servicio.eliminar(d.getId());
                return List.of(); // K
            }, v -> refrescar()); // Y
        } // K
    }

    @FXML
    private void alBuscar() {
        String consulta = txtConsulta.getText();
        if (consulta == null || consulta.isBlank()) { // Y
            return; // Y
        }
        ocupado(true, "Buscando...");
        Tareas.ejecutar(() -> servicio.buscar(consulta, 5), resultados -> {
            ocupado(false, resultados.size() + (resultados.size() == 1 ? " resultado" : " resultados"));
            contenedorResultados.getChildren().clear(); // K
            if (resultados.isEmpty()) { // Y
                Label vacio = new Label("No hay documentos para buscar. Importa alguno primero.");
                vacio.getStyleClass().add("text-muted");
                contenedorResultados.getChildren().add(vacio);
                return;
            } // K
            for (ServicioDocumentos.Resultado r : resultados) { // Y
                Label cabecera = new Label(r.documento() + "  ·  " + Math.round(r.similitud() * 100) + " % de coincidencia"); // Y
                cabecera.getStyleClass().add("nombre-dependencia");
                String t = r.texto().length() > 380 ? r.texto().substring(0, 380) + "…" : r.texto(); // K
                Label cuerpo = new Label(t); // Y
                cuerpo.setWrapText(true);
                VBox tarjeta = new VBox(4, cabecera, cuerpo);
                tarjeta.getStyleClass().add("tarjeta-diagnostico");
                tarjeta.setStyle("-fx-padding: 12;"); // Y
                contenedorResultados.getChildren().add(tarjeta);
            }
        }, causa -> { // K
            ocupado(false, null);
            refrescar();
            Tareas.mostrarError(causa);
        });
    }

    private void ocupado(boolean valor, String mensaje) {
        indicador.setVisible(valor);
        btnImportar.setDisable(valor);
        btnBuscar.setDisable(valor);
        if (mensaje != null) { // Y
            lblEstado.setText(mensaje);
        }
    }
}
