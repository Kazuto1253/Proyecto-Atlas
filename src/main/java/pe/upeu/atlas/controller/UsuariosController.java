// Autores: K (Kevin) y Y (Yenifher)
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
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.model.Usuario;
import pe.upeu.atlas.seguridad.ServicioUsuarios;
import pe.upeu.atlas.ui.Refrescable;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/** Administración de cuentas (solo administradores): crear, activar, cambiar rol y restablecer clave. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsuariosController implements Refrescable {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ServicioUsuarios servicio;

    @FXML private TableView<Usuario> tabla;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colUsuario;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colEstado;
    @FXML private TableColumn<Usuario, String> colAcceso;
    @FXML private Label lblResumen;
    @FXML private Button btnActivar;
    @FXML private Button btnRol;
    @FXML private Button btnClave;

    @FXML
    private void initialize() {
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombreCompleto()));
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsuario()));
        colRol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRol().etiqueta()));
        colEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActivo() ? "Activo" : "Desactivado"));
        colAcceso.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getUltimoAcceso() == null ? "Nunca" : c.getValue().getUltimoAcceso().format(FORMATO)));
        tabla.setPlaceholder(new Label("No hay usuarios."));
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, u) -> {
            boolean sinSeleccion = u == null;
            btnActivar.setDisable(sinSeleccion);
            btnRol.setDisable(sinSeleccion); // K
            btnClave.setDisable(sinSeleccion);
            if (u != null) { // Y
                btnActivar.setText(u.isActivo() ? "Desactivar" : "Activar");
            } // Y
        });
        btnActivar.setDisable(true);
        btnRol.setDisable(true);
        btnClave.setDisable(true); // K
    }

    @Override
    public void refrescar() {
        ejecutar(servicio::listar, lista -> { // Y
            tabla.setItems(FXCollections.observableArrayList(lista)); // Y
            long activos = lista.stream().filter(Usuario::isActivo).count();
            lblResumen.setText(lista.size() + " usuarios · " + activos + " activos");
        }); // Y
    }

    @FXML
    private void alCrearUsuario() { // K
        Dialog<ButtonType> dialogo = new Dialog<>(); // K
        dialogo.setTitle("Atlas");
        dialogo.setHeaderText("Nuevo usuario");
        TextField nombre = new TextField(); // K
        nombre.setPromptText("Nombres y apellidos"); // Y
        TextField usuario = new TextField(); // K
        usuario.setPromptText("3 a 30 caracteres");
        PasswordField clave = new PasswordField(); // K
        clave.setPromptText("Mínimo 6 caracteres");
        ChoiceBox<Rol> rol = new ChoiceBox<>(FXCollections.observableArrayList(Rol.values()));
        rol.setValue(Rol.ESTUDIANTE); // Y
        GridPane rejilla = new GridPane();
        rejilla.setHgap(10);
        rejilla.setVgap(10); // Y
        rejilla.addRow(0, new Label("Nombre"), nombre);
        rejilla.addRow(1, new Label("Usuario"), usuario); // Y
        rejilla.addRow(2, new Label("Contraseña"), clave); // K
        rejilla.addRow(3, new Label("Rol"), rol);
        dialogo.getDialogPane().setContent(rejilla); // K
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL); // K

        if (dialogo.showAndWait().filter(b -> b == ButtonType.OK).isPresent()) { // Y
            ejecutar(() -> servicio.crear(nombre.getText(), usuario.getText(), clave.getText(), rol.getValue()),
                    u -> refrescar());
        } // K
    }

    @FXML
    private void alCambiarActivo() {
        Usuario u = tabla.getSelectionModel().getSelectedItem();
        if (u == null) {
            return;
        }
        ejecutar(() -> {
            servicio.cambiarActivo(u.getId(), !u.isActivo());
            return null;
        }, v -> refrescar()); // K
    }

    @FXML
    private void alCambiarRol() {
        Usuario u = tabla.getSelectionModel().getSelectedItem();
        if (u == null) { // K
            return; // K
        } // K
        ChoiceDialog<Rol> dialogo = new ChoiceDialog<>(u.getRol(), Rol.values()); // K
        dialogo.setTitle("Atlas");
        dialogo.setHeaderText("Nuevo rol para " + u.getUsuario());
        Optional<Rol> elegido = dialogo.showAndWait(); // Y
        elegido.ifPresent(rol -> ejecutar(() -> {
            servicio.cambiarRol(u.getId(), rol);
            return null; // K
        }, v -> refrescar()));
    } // Y

    @FXML
    private void alRestablecerClave() {
        Usuario u = tabla.getSelectionModel().getSelectedItem(); // Y
        if (u == null) {
            return; // K
        }
        TextInputDialog dialogo = new TextInputDialog();
        dialogo.setTitle("Atlas");
        dialogo.setHeaderText("Nueva contraseña para " + u.getUsuario());
        dialogo.setContentText("Contraseña (mínimo 6):"); // Y
        dialogo.showAndWait().ifPresent(clave -> ejecutar(() -> {
            servicio.restablecerClave(u.getId(), clave); // Y
            return null;
        }, v -> informar("Contraseña actualizada.")));
    }

    private <T> void ejecutar(Callable<T> trabajo, java.util.function.Consumer<T> alTerminar) {
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabajo.call(); // K
            }
        };
        tarea.setOnSucceeded(e -> alTerminar.accept(tarea.getValue())); // Y
        tarea.setOnFailed(e -> {
            Throwable causa = tarea.getException(); // K
            if (causa instanceof NegocioException || causa instanceof PermisoDenegadoException) {
                new Alert(Alert.AlertType.WARNING, causa.getMessage()).showAndWait();
            } else { // Y
                log.error("Fallo en la gestión de usuarios", causa);
                new Alert(Alert.AlertType.ERROR, "Ocurrió un error inesperado.").showAndWait();
            } // K
        });
        Thread hilo = new Thread(tarea, "atlas-usuarios");
        hilo.setDaemon(true); // K
        hilo.start(); // K
    }

    private void informar(String mensaje) {
        new Alert(Alert.AlertType.INFORMATION, mensaje).showAndWait();
    }
} // Y
