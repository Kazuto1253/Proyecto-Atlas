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
import pe.upeu.atlas.model.MarcaDocumento;
import pe.upeu.atlas.service.MarcaExtractorTexto;
import pe.upeu.atlas.service.SanchezServicioDocumentos;
import pe.upeu.atlas.ui.MarcaRefrescable;
import pe.upeu.atlas.ui.MarcaTareas;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SanchezDocumentosController implements MarcaRefrescable {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SanchezServicioDocumentos marcaservicio;

    @FXML private TableView<MarcaDocumento> marcatabla;
    @FXML private TableColumn<MarcaDocumento, String> sanchezcolNombre;
    @FXML private TableColumn<MarcaDocumento, String> marcacolTipo;
    @FXML private TableColumn<MarcaDocumento, String> marcacolFragmentos;
    @FXML private TableColumn<MarcaDocumento, String> sanchezcolFecha;
    @FXML private Button marcabtnImportar;
    @FXML private Button sanchezbtnEliminar;
    @FXML private TextField sancheztxtConsulta;
    @FXML private Button marcabtnBuscar;
    @FXML private VBox sanchezcontenedorResultados;
    @FXML private Label marcalblEstado;
    @FXML private ProgressIndicator sanchezindicador;

    @FXML
    private void initialize() {
        sanchezcolNombre.setCellValueFactory(sanchezc -> new SimpleStringProperty(sanchezc.getValue().getNombre()));
        marcacolTipo.setCellValueFactory(sanchezc -> new SimpleStringProperty(sanchezc.getValue().getTipo().toUpperCase()));
        marcacolFragmentos.setCellValueFactory(sanchezc -> new SimpleStringProperty(String.valueOf(sanchezc.getValue().getFragmentos())));
        sanchezcolFecha.setCellValueFactory(sanchezc -> new SimpleStringProperty(sanchezc.getValue().getFecha().format(FORMATO)));
        marcatabla.setPlaceholder(new Label("Importa tus apuntes para poder consultarlos."));
        sanchezbtnEliminar.disableProperty().bind(marcatabla.getSelectionModel().selectedItemProperty().isNull());
        sanchezindicador.managedProperty().bind(sanchezindicador.visibleProperty());
        sanchezindicador.setVisible(false);
    }

    @Override
    public void sanchezrefrescar() {
        MarcaTareas.sanchezejecutar(marcaservicio::sanchezlistar, sanchezdocs -> {
            marcatabla.setItems(FXCollections.observableArrayList(sanchezdocs));
            marcalblEstado.setText(sanchezdocs.size() + (sanchezdocs.size() == 1 ? " documento" : " documentos"));
        });
    }

    @FXML
    private void sanchezalImportar() {
        FileChooser sanchezselector = new FileChooser();
        sanchezselector.setTitle("Elige un documento");
        sanchezselector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos e imágenes",
                MarcaExtractorTexto.TIPOS.stream().sorted().map(marcat -> "*." + marcat).toList()));
        File marcaarchivo = sanchezselector.showOpenDialog(marcatabla.getScene().getWindow());
        if (marcaarchivo == null) {
            return;
        }
        sanchezocupado(true, "Procesando \"" + marcaarchivo.getName() + "\" (lectura, OCR y embeddings)...");
        MarcaTareas.sanchezejecutar(() -> marcaservicio.marcaimportar(marcaarchivo.toPath()), marcad -> {
            sanchezocupado(false, null);
            sanchezrefrescar();
            MarcaTareas.marcainformar("Listo: \"" + marcad.getNombre() + "\" quedó en " + marcad.getFragmentos()
                    + " fragmentos. Ya puedes preguntarle a Atlas por su contenido.");
        }, sanchezcausa -> {
            sanchezocupado(false, null);
            sanchezrefrescar();
            MarcaTareas.marcamostrarError(sanchezcausa);
        });
    }

    @FXML
    private void marcaalEliminar() {
        MarcaDocumento marcad = marcatabla.getSelectionModel().getSelectedItem();
        if (marcad == null) {
            return;
        }
        Alert sanchezconfirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "Se quitará \"" + marcad.getNombre() + "\" de tu biblioteca (el archivo original no se toca).",
                ButtonType.YES, ButtonType.NO);
        sanchezconfirmar.setHeaderText("¿Quitar documento?");
        if (sanchezconfirmar.showAndWait().filter(marcab -> marcab == ButtonType.YES).isPresent()) {
            MarcaTareas.sanchezejecutar(() -> {
                marcaservicio.eliminar(marcad.getId());
                return List.of();
            }, marcav -> sanchezrefrescar());
        }
    }

    @FXML
    private void sanchezalBuscar() {
        String sanchezconsulta = sancheztxtConsulta.getText();
        if (sanchezconsulta == null || sanchezconsulta.isBlank()) {
            return;
        }
        sanchezocupado(true, "Buscando...");
        MarcaTareas.sanchezejecutar(() -> marcaservicio.buscar(sanchezconsulta, 5), marcaresultados -> {
            sanchezocupado(false, marcaresultados.size() + (marcaresultados.size() == 1 ? " resultado" : " resultados"));
            sanchezcontenedorResultados.getChildren().clear();
            if (marcaresultados.isEmpty()) {
                Label marcavacio = new Label("No hay documentos para buscar. Importa alguno primero.");
                marcavacio.getStyleClass().add("text-muted");
                sanchezcontenedorResultados.getChildren().add(marcavacio);
                return;
            }
            for (SanchezServicioDocumentos.SanchezResultado marcar : marcaresultados) {
                Label marcacabecera = new Label(marcar.marcadocumento() + "  ·  " + Math.round(marcar.marcasimilitud() * 100) + " % de coincidencia");
                marcacabecera.getStyleClass().add("nombre-dependencia");
                String marcat = marcar.texto().length() > 380 ? marcar.texto().substring(0, 380) + "…" : marcar.texto();
                Label marcacuerpo = new Label(marcat);
                marcacuerpo.setWrapText(true);
                VBox sancheztarjeta = new VBox(4, marcacabecera, marcacuerpo);
                sancheztarjeta.getStyleClass().add("tarjeta-diagnostico");
                sancheztarjeta.setStyle("-fx-padding: 12;");
                sanchezcontenedorResultados.getChildren().add(sancheztarjeta);
            }
        }, sanchezcausa -> {
            sanchezocupado(false, null);
            sanchezrefrescar();
            MarcaTareas.marcamostrarError(sanchezcausa);
        });
    }

    private void sanchezocupado(boolean marcavalor, String sanchezmensaje) {
        sanchezindicador.setVisible(marcavalor);
        marcabtnImportar.setDisable(marcavalor);
        marcabtnBuscar.setDisable(marcavalor);
        if (sanchezmensaje != null) {
            marcalblEstado.setText(sanchezmensaje);
        }
    }
}
