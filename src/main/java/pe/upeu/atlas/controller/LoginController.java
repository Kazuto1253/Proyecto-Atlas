// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.seguridad.ServicioAutenticacion;
import pe.upeu.atlas.ui.Navegador;

import java.util.concurrent.Callable;

/** Pantalla de acceso: inicio de sesión y autoregistro de estudiantes. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginController { // Y

    private final ServicioAutenticacion autenticacion; // K
    private final Navegador navegador; // Y

    @FXML private VBox formLogin;
    @FXML private VBox formRegistro;
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtClave;
    @FXML private TextField txtNombre;
    @FXML private TextField txtNuevoUsuario;
    @FXML private PasswordField txtNuevaClave;
    @FXML private PasswordField txtRepetirClave;
    @FXML private Label lblError;
    @FXML private Button btnEntrar;
    @FXML private Button btnCrear;

    @FXML
    private void initialize() { // Y
        formLogin.managedProperty().bind(formLogin.visibleProperty());
        formRegistro.managedProperty().bind(formRegistro.visibleProperty());
        lblError.managedProperty().bind(lblError.visibleProperty());
        alVerLogin(); // Y
        txtUsuario.sceneProperty().addListener((o, a, escena) -> { // K
            if (escena != null) {
                javafx.application.Platform.runLater(txtUsuario::requestFocus);
            }
        });
    } // K

    @FXML
    private void alVerLogin() {
        ocultarError(); // Y
        formLogin.setVisible(true);
        formRegistro.setVisible(false); // K
    }

    @FXML
    private void alVerRegistro() {
        ocultarError(); // K
        formLogin.setVisible(false); // K
        formRegistro.setVisible(true);
    }

    @FXML
    private void alIniciarSesion() {
        String usuario = txtUsuario.getText();
        String clave = txtClave.getText(); // Y
        ejecutar(() -> autenticacion.iniciarSesion(usuario, clave), u -> { // K
            txtClave.clear();
            navegador.mostrarPrincipal();
        });
    } // Y

    @FXML
    private void alCrearCuenta() { // Y
        String nombre = txtNombre.getText(); // K
        String usuario = txtNuevoUsuario.getText();
        String clave = txtNuevaClave.getText();
        if (!clave.equals(txtRepetirClave.getText())) {
            mostrarError("Las contraseñas no coinciden.");
            return; // K
        }
        ejecutar(() -> { // Y
            autenticacion.registrar(nombre, usuario, clave, Rol.ESTUDIANTE);
            return autenticacion.iniciarSesion(usuario, clave); // K
        }, u -> {
            txtNombre.clear(); // Y
            txtNuevoUsuario.clear();
            txtNuevaClave.clear();
            txtRepetirClave.clear();
            navegador.mostrarPrincipal(); // Y
        });
    }

    /** El hash BCrypt tarda unos cientos de ms: se hace fuera del hilo de JavaFX. */
    private <T> void ejecutar(Callable<T> trabajo, java.util.function.Consumer<T> alTerminar) {
        ocultarError(); // Y
        ocupado(true); // Y
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabajo.call();
            }
        };
        tarea.setOnSucceeded(e -> {
            ocupado(false);
            alTerminar.accept(tarea.getValue()); // Y
        });
        tarea.setOnFailed(e -> {
            ocupado(false);
            Throwable causa = tarea.getException();
            if (causa instanceof NegocioException) { // K
                mostrarError(causa.getMessage()); // Y
            } else {
                log.error("Fallo inesperado en el acceso", causa); // K
                mostrarError("Ocurrió un error inesperado. Revisa el registro de Atlas.");
            }
        }); // Y
        Thread hilo = new Thread(tarea, "atlas-acceso"); // K
        hilo.setDaemon(true);
        hilo.start();
    }

    private void ocupado(boolean valor) {
        btnEntrar.setDisable(valor); // Y
        btnCrear.setDisable(valor);
    } // Y

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
    } // K

    private void ocultarError() {
        lblError.setVisible(false);
    } // Y
} // Y
