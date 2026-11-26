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
import pe.upeu.atlas.model.MarcaConversacion;
import pe.upeu.atlas.model.SanchezMensaje;
import pe.upeu.atlas.plataforma.SanchezLanzador;
import pe.upeu.atlas.service.SanchezServicioHistorial;
import pe.upeu.atlas.service.MarcaServicioReportes;
import pe.upeu.atlas.ui.MarcaRefrescable;
import pe.upeu.atlas.ui.MarcaTareas;

import java.io.File;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SanchezHistorialController implements MarcaRefrescable {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SanchezServicioHistorial sanchezhistorial;
    private final MarcaServicioReportes marcareportes;
    private final SanchezLanzador sanchezlanzador;

    private final PauseTransition marcaretraso = new PauseTransition(Duration.millis(300));

    @FXML private TextField marcatxtBuscar;
    @FXML private ListView<MarcaConversacion> sanchezlista;
    @FXML private VBox sanchezcontenedorMensajes;
    @FXML private Label sanchezlblTitulo;
    @FXML private Label sanchezlblMeta;
    @FXML private Label marcalblVacio;
    @FXML private Button marcabtnPdf;
    @FXML private Button sanchezbtnEliminar;

    @FXML
    private void initialize() {
        sanchezlista.setPlaceholder(new Label("Todavía no hay conversaciones."));
        sanchezlista.setCellFactory(marcav -> new ListCell<>() {
            @Override
            protected void updateItem(MarcaConversacion sanchezc, boolean marcavacio) {
                super.updateItem(sanchezc, marcavacio);
                if (marcavacio || sanchezc == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label sancheztitulo = new Label(sanchezc.getTitulo());
                sancheztitulo.getStyleClass().add("nombre-dependencia");
                Label sanchezfecha = new Label(sanchezc.getActualizada().format(FORMATO));
                sanchezfecha.getStyleClass().addAll("text-small", "text-muted");
                setGraphic(new VBox(2, sancheztitulo, sanchezfecha));
            }
        });
        sanchezlista.getSelectionModel().selectedItemProperty().addListener((sanchezo, sancheza, sanchezc) -> marcamostrar(sanchezc));
        marcaretraso.setOnFinished(sancheze -> sanchezrefrescar());
        marcatxtBuscar.textProperty().addListener((sanchezo, sancheza, marcab) -> marcaretraso.playFromStart());
        marcamostrar(null);
    }

    @Override
    public void sanchezrefrescar() {
        Long sanchezseleccionada = sanchezlista.getSelectionModel().getSelectedItem() == null
                ? null : sanchezlista.getSelectionModel().getSelectedItem().getId();
        String marcafiltro = marcatxtBuscar.getText();
        MarcaTareas.sanchezejecutar(() -> sanchezhistorial.sanchezlistar(marcafiltro), marcaconversaciones -> {
            sanchezlista.setItems(FXCollections.observableArrayList(marcaconversaciones));
            marcaconversaciones.stream().filter(sanchezc -> sanchezc.getId().equals(sanchezseleccionada)).findFirst()
                    .ifPresent(sanchezc -> sanchezlista.getSelectionModel().select(sanchezc));
        });
    }

    private void marcamostrar(MarcaConversacion sanchezc) {
        sanchezcontenedorMensajes.getChildren().clear();
        boolean marcahay = sanchezc != null;
        marcabtnPdf.setDisable(!marcahay);
        sanchezbtnEliminar.setDisable(!marcahay);
        marcalblVacio.setVisible(!marcahay);
        marcalblVacio.setManaged(!marcahay);
        sanchezlblTitulo.setText(marcahay ? sanchezc.getTitulo() : "");
        sanchezlblMeta.setText(marcahay ? "Iniciada el " + sanchezc.getCreada().format(FORMATO) : "");
        if (!marcahay) {
            return;
        }
        MarcaTareas.sanchezejecutar(() -> sanchezhistorial.marcamensajes(sanchezc.getId()), marcamensajes -> {
            if (sanchezlista.getSelectionModel().getSelectedItem() != sanchezc) {
                return;
            }
            for (SanchezMensaje sanchezm : marcamensajes) {
                Label sanchezburbuja = new Label(sanchezm.getTexto());
                sanchezburbuja.setWrapText(true);
                sanchezburbuja.setMaxWidth(560);
                sanchezburbuja.getStyleClass().addAll("burbuja", sanchezm.isDeUsuario() ? "burbuja-usuario" : "burbuja-asistente");
                HBox marcafila = new HBox(sanchezburbuja);
                marcafila.setAlignment(sanchezm.isDeUsuario() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                sanchezcontenedorMensajes.getChildren().add(marcafila);
            }
        });
    }

    @FXML
    private void marcaalExportarPdf() {
        MarcaConversacion sanchezc = sanchezlista.getSelectionModel().getSelectedItem();
        if (sanchezc == null) {
            return;
        }
        FileChooser sanchezselector = new FileChooser();
        sanchezselector.setTitle("Guardar conversación en PDF");
        sanchezselector.setInitialFileName(marcanombreSeguro(sanchezc.getTitulo()) + ".pdf");
        sanchezselector.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File marcadestino = sanchezselector.showSaveDialog(sanchezlista.getScene().getWindow());
        if (marcadestino == null) {
            return;
        }
        MarcaTareas.sanchezejecutar(() -> marcareportes.marcaconversacionPdf(sanchezc.getId(), marcadestino.toPath()), marcaruta -> sanchezabrirResultado(marcaruta));
    }

    @FXML
    private void marcaalEliminar() {
        MarcaConversacion sanchezc = sanchezlista.getSelectionModel().getSelectedItem();
        if (sanchezc == null) {
            return;
        }
        Alert sanchezconfirmar = new Alert(Alert.AlertType.CONFIRMATION,
                "Se borrará la conversación \"" + sanchezc.getTitulo() + "\". No se puede deshacer.",
                ButtonType.YES, ButtonType.NO);
        sanchezconfirmar.setHeaderText("¿Eliminar conversación?");
        if (sanchezconfirmar.showAndWait().filter(marcab -> marcab == ButtonType.YES).isPresent()) {
            MarcaTareas.sanchezejecutar(() -> {
                sanchezhistorial.eliminar(sanchezc.getId());
                return List.of();
            }, marcav -> sanchezrefrescar());
        }
    }

    private void sanchezabrirResultado(Path marcaruta) {
        MarcaTareas.marcainformar("Guardado en:\n" + marcaruta);
        try {
            sanchezlanzador.marcaabrir(marcaruta);
        } catch (RuntimeException sancheze) {
        }
    }

    static String marcanombreSeguro(String sancheztitulo) {
        String marcan = sancheztitulo.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").strip();
        return marcan.isEmpty() ? "conversacion" : marcan.substring(0, Math.min(marcan.length(), 50));
    }
}
