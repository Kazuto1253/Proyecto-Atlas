package pe.upeu.atlas.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.model.MarcaUsuario;
import pe.upeu.atlas.seguridad.SanchezServicioUsuarios;
import pe.upeu.atlas.ui.MarcaRefrescable;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@Slf4j
@Component
@RequiredArgsConstructor
public class SanchezUsuariosController implements MarcaRefrescable {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SanchezServicioUsuarios marcaservicio;

    @FXML private TableView<MarcaUsuario> marcatabla;
    @FXML private TableColumn<MarcaUsuario, String> sanchezcolNombre;
    @FXML private TableColumn<MarcaUsuario, String> marcacolUsuario;
    @FXML private TableColumn<MarcaUsuario, String> sanchezcolRol;
    @FXML private TableColumn<MarcaUsuario, String> marcacolEstado;
    @FXML private TableColumn<MarcaUsuario, String> marcacolAcceso;
    @FXML private Label sanchezlblResumen;
    @FXML private Button marcabtnActivar;
    @FXML private Button sanchezbtnRol;
    @FXML private Button sanchezbtnClave;

    @FXML
    private void initialize() {
        sanchezcolNombre.setCellValueFactory(sanchezc -> new SimpleStringProperty(sanchezc.getValue().getNombreCompleto()));
        marcacolUsuario.setCellValueFactory(sanchezc -> new SimpleStringProperty(sanchezc.getValue().getUsuario()));
        sanchezcolRol.setCellValueFactory(sanchezc -> new SimpleStringProperty(sanchezc.getValue().getRol().marcaetiqueta()));
        marcacolEstado.setCellValueFactory(sanchezc -> new SimpleStringProperty(sanchezc.getValue().isActivo() ? "Activo" : "Desactivado"));
        marcacolAcceso.setCellValueFactory(sanchezc -> new SimpleStringProperty(
                sanchezc.getValue().getUltimoAcceso() == null ? "Nunca" : sanchezc.getValue().getUltimoAcceso().format(FORMATO)));
        marcatabla.setPlaceholder(new Label("No hay usuarios."));
        marcatabla.getSelectionModel().selectedItemProperty().addListener((sanchezo, sancheza, sanchezu) -> {
            boolean sanchezsinSeleccion = sanchezu == null;
            marcabtnActivar.setDisable(sanchezsinSeleccion);
            sanchezbtnRol.setDisable(sanchezsinSeleccion);
            sanchezbtnClave.setDisable(sanchezsinSeleccion);
            if (sanchezu != null) {
                marcabtnActivar.setText(sanchezu.isActivo() ? "Desactivar" : "Activar");
            }
        });
        marcabtnActivar.setDisable(true);
        sanchezbtnRol.setDisable(true);
        sanchezbtnClave.setDisable(true);
    }

    @Override
    public void sanchezrefrescar() {
        sanchezejecutar(marcaservicio::sanchezlistar, sanchezlista -> {
            marcatabla.setItems(FXCollections.observableArrayList(sanchezlista));
            long sanchezactivos = sanchezlista.stream().filter(MarcaUsuario::isActivo).count();
            sanchezlblResumen.setText(sanchezlista.size() + " usuarios · " + sanchezactivos + " activos");
        });
    }

    @FXML
    private void marcaalCrearUsuario() {
        Dialog<ButtonType> sanchezdialogo = new Dialog<>();
        sanchezdialogo.setTitle("Atlas");
        sanchezdialogo.setHeaderText("Nuevo usuario");
        TextField sancheznombre = new TextField();
        sancheznombre.setPromptText("Nombres y apellidos");
        TextField marcausuario = new TextField();
        marcausuario.setPromptText("3 a 30 caracteres");
        PasswordField sanchezclave = new PasswordField();
        sanchezclave.setPromptText("Mínimo 6 caracteres");
        ChoiceBox<SanchezRol> sanchezrol = new ChoiceBox<>(FXCollections.observableArrayList(SanchezRol.values()));
        sanchezrol.setValue(SanchezRol.ESTUDIANTE);
        GridPane sanchezrejilla = new GridPane();
        sanchezrejilla.setHgap(10);
        sanchezrejilla.setVgap(10);
        sanchezrejilla.addRow(0, new Label("Nombre"), sancheznombre);
        sanchezrejilla.addRow(1, new Label("Usuario"), marcausuario);
        sanchezrejilla.addRow(2, new Label("Contraseña"), sanchezclave);
        sanchezrejilla.addRow(3, new Label("Rol"), sanchezrol);
        sanchezdialogo.getDialogPane().setContent(sanchezrejilla);
        sanchezdialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (sanchezdialogo.showAndWait().filter(marcab -> marcab == ButtonType.OK).isPresent()) {
            sanchezejecutar(() -> marcaservicio.sanchezcrear(sancheznombre.getText(), marcausuario.getText(), sanchezclave.getText(), sanchezrol.getValue()),
                    sanchezu -> sanchezrefrescar());
        }
    }

    @FXML
    private void marcaalCambiarActivo() {
        MarcaUsuario sanchezu = marcatabla.getSelectionModel().getSelectedItem();
        if (sanchezu == null) {
            return;
        }
        sanchezejecutar(() -> {
            marcaservicio.sanchezcambiarActivo(sanchezu.getId(), !sanchezu.isActivo());
            return null;
        }, marcav -> sanchezrefrescar());
    }

    @FXML
    private void sanchezalCambiarRol() {
        MarcaUsuario sanchezu = marcatabla.getSelectionModel().getSelectedItem();
        if (sanchezu == null) {
            return;
        }
        ChoiceDialog<SanchezRol> sanchezdialogo = new ChoiceDialog<>(sanchezu.getRol(), SanchezRol.values());
        sanchezdialogo.setTitle("Atlas");
        sanchezdialogo.setHeaderText("Nuevo rol para " + sanchezu.getUsuario());
        Optional<SanchezRol> sanchezelegido = sanchezdialogo.showAndWait();
        sanchezelegido.ifPresent(sanchezrol -> sanchezejecutar(() -> {
            marcaservicio.marcacambiarRol(sanchezu.getId(), sanchezrol);
            return null;
        }, marcav -> sanchezrefrescar()));
    }

    @FXML
    private void marcaalRestablecerClave() {
        MarcaUsuario sanchezu = marcatabla.getSelectionModel().getSelectedItem();
        if (sanchezu == null) {
            return;
        }
        TextInputDialog sanchezdialogo = new TextInputDialog();
        sanchezdialogo.setTitle("Atlas");
        sanchezdialogo.setHeaderText("Nueva contraseña para " + sanchezu.getUsuario());
        sanchezdialogo.setContentText("Contraseña (mínimo 6):");
        sanchezdialogo.showAndWait().ifPresent(sanchezclave -> sanchezejecutar(() -> {
            marcaservicio.sanchezrestablecerClave(sanchezu.getId(), sanchezclave);
            return null;
        }, marcav -> marcainformar("Contraseña actualizada.")));
    }

    private <T> void sanchezejecutar(Callable<T> sancheztrabajo, java.util.function.Consumer<T> sanchezalTerminar) {
        Task<T> sancheztarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return sancheztrabajo.call();
            }
        };
        sancheztarea.setOnSucceeded(sancheze -> sanchezalTerminar.accept(sancheztarea.getValue()));
        sancheztarea.setOnFailed(sancheze -> {
            Throwable sanchezcausa = sancheztarea.getException();
            if (sanchezcausa instanceof SanchezNegocioException || sanchezcausa instanceof SanchezPermisoDenegadoException) {
                new Alert(Alert.AlertType.WARNING, sanchezcausa.getMessage()).showAndWait();
            } else {
                log.error("Fallo en la gestión de usuarios", sanchezcausa);
                new Alert(Alert.AlertType.ERROR, "Ocurrió un error inesperado.").showAndWait();
            }
        });
        Thread marcahilo = new Thread(sancheztarea, "atlas-usuarios");
        marcahilo.setDaemon(true);
        marcahilo.start();
    }

    private void marcainformar(String sanchezmensaje) {
        new Alert(Alert.AlertType.INFORMATION, sanchezmensaje).showAndWait();
    }
}
