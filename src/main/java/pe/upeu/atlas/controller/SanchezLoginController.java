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
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.seguridad.SanchezServicioAutenticacion;
import pe.upeu.atlas.ui.SanchezNavegador;

import java.util.concurrent.Callable;

@Slf4j
@Component
@RequiredArgsConstructor
public class SanchezLoginController { // Y

    private final SanchezServicioAutenticacion sanchezautenticacion; // K
    private final SanchezNavegador sancheznavegador; // Y

    @FXML private VBox sanchezformLogin;
    @FXML private VBox sanchezformRegistro;
    @FXML private TextField marcatxtUsuario;
    @FXML private PasswordField sancheztxtClave;
    @FXML private TextField sancheztxtNombre;
    @FXML private TextField sancheztxtNuevoUsuario;
    @FXML private PasswordField marcatxtNuevaClave;
    @FXML private PasswordField marcatxtRepetirClave;
    @FXML private Label marcalblError;
    @FXML private Button marcabtnEntrar;
    @FXML private Button sanchezbtnCrear;

    @FXML
    private void initialize() { // Y
        sanchezformLogin.managedProperty().bind(sanchezformLogin.visibleProperty());
        sanchezformRegistro.managedProperty().bind(sanchezformRegistro.visibleProperty());
        marcalblError.managedProperty().bind(marcalblError.visibleProperty());
        sanchezalVerLogin(); // Y
        marcatxtUsuario.sceneProperty().addListener((sanchezo, sancheza, sanchezescena) -> { // K
            if (sanchezescena != null) {
                javafx.application.Platform.runLater(marcatxtUsuario::requestFocus);
            }
        });
    } // K

    @FXML
    private void sanchezalVerLogin() {
        marcaocultarError(); // Y
        sanchezformLogin.setVisible(true);
        sanchezformRegistro.setVisible(false); // K
    }

    @FXML
    private void sanchezalVerRegistro() {
        marcaocultarError(); // K
        sanchezformLogin.setVisible(false); // K
        sanchezformRegistro.setVisible(true);
    }

    @FXML
    private void sanchezalIniciarSesion() {
        String marcausuario = marcatxtUsuario.getText();
        String sanchezclave = sancheztxtClave.getText(); // Y
        sanchezejecutar(() -> sanchezautenticacion.marcainiciarSesion(marcausuario, sanchezclave), sanchezu -> { // K
            sancheztxtClave.clear();
            sancheznavegador.marcamostrarPrincipal();
        });
    } // Y

    @FXML
    private void marcaalCrearCuenta() { // Y
        String sancheznombre = sancheztxtNombre.getText(); // K
        String marcausuario = sancheztxtNuevoUsuario.getText();
        String sanchezclave = marcatxtNuevaClave.getText();
        if (!sanchezclave.equals(marcatxtRepetirClave.getText())) {
            marcamostrarError("Las contraseñas no coinciden.");
            return; // K
        }
        sanchezejecutar(() -> { // Y
            sanchezautenticacion.sanchezregistrar(sancheznombre, marcausuario, sanchezclave, SanchezRol.ESTUDIANTE);
            return sanchezautenticacion.marcainiciarSesion(marcausuario, sanchezclave); // K
        }, sanchezu -> {
            sancheztxtNombre.clear(); // Y
            sancheztxtNuevoUsuario.clear();
            marcatxtNuevaClave.clear();
            marcatxtRepetirClave.clear();
            sancheznavegador.marcamostrarPrincipal(); // Y
        });
    }

    private <T> void sanchezejecutar(Callable<T> sancheztrabajo, java.util.function.Consumer<T> sanchezalTerminar) {
        marcaocultarError(); // Y
        sanchezocupado(true); // Y
        Task<T> sancheztarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return sancheztrabajo.call();
            }
        };
        sancheztarea.setOnSucceeded(sancheze -> {
            sanchezocupado(false);
            sanchezalTerminar.accept(sancheztarea.getValue()); // Y
        });
        sancheztarea.setOnFailed(sancheze -> {
            sanchezocupado(false);
            Throwable sanchezcausa = sancheztarea.getException();
            if (sanchezcausa instanceof SanchezNegocioException) { // K
                marcamostrarError(sanchezcausa.getMessage()); // Y
            } else {
                log.error("Fallo inesperado en el acceso", sanchezcausa); // K
                marcamostrarError("Ocurrió un error inesperado. Revisa el registro de Atlas.");
            }
        }); // Y
        Thread marcahilo = new Thread(sancheztarea, "atlas-acceso"); // K
        marcahilo.setDaemon(true);
        marcahilo.start();
    }

    private void sanchezocupado(boolean marcavalor) {
        marcabtnEntrar.setDisable(marcavalor); // Y
        sanchezbtnCrear.setDisable(marcavalor);
    } // Y

    private void marcamostrarError(String sanchezmensaje) {
        marcalblError.setText(sanchezmensaje);
        marcalblError.setVisible(true);
    } // K

    private void marcaocultarError() {
        marcalblError.setVisible(false);
    } // Y
} // Y
