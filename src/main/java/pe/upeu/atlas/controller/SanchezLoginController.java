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
public class SanchezLoginController {

    private final SanchezServicioAutenticacion sanchezautenticacion;
    private final SanchezNavegador sancheznavegador;

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
    private void initialize() {
        sanchezformLogin.managedProperty().bind(sanchezformLogin.visibleProperty());
        sanchezformRegistro.managedProperty().bind(sanchezformRegistro.visibleProperty());
        marcalblError.managedProperty().bind(marcalblError.visibleProperty());
        sanchezalVerLogin();
        marcatxtUsuario.sceneProperty().addListener((sanchezo, sancheza, sanchezescena) -> {
            if (sanchezescena != null) {
                javafx.application.Platform.runLater(marcatxtUsuario::requestFocus);
            }
        });
    }

    @FXML
    private void sanchezalVerLogin() {
        marcaocultarError();
        sanchezformLogin.setVisible(true);
        sanchezformRegistro.setVisible(false);
    }

    @FXML
    private void sanchezalVerRegistro() {
        marcaocultarError();
        sanchezformLogin.setVisible(false);
        sanchezformRegistro.setVisible(true);
    }

    @FXML
    private void sanchezalIniciarSesion() {
        String marcausuario = marcatxtUsuario.getText();
        String sanchezclave = sancheztxtClave.getText();
        sanchezejecutar(() -> sanchezautenticacion.marcainiciarSesion(marcausuario, sanchezclave), sanchezu -> {
            sancheztxtClave.clear();
            sancheznavegador.marcamostrarPrincipal();
        });
    }

    @FXML
    private void marcaalCrearCuenta() {
        String sancheznombre = sancheztxtNombre.getText();
        String marcausuario = sancheztxtNuevoUsuario.getText();
        String sanchezclave = marcatxtNuevaClave.getText();
        if (!sanchezclave.equals(marcatxtRepetirClave.getText())) {
            marcamostrarError("Las contraseñas no coinciden.");
            return;
        }
        sanchezejecutar(() -> {
            sanchezautenticacion.sanchezregistrar(sancheznombre, marcausuario, sanchezclave, SanchezRol.ESTUDIANTE);
            return sanchezautenticacion.marcainiciarSesion(marcausuario, sanchezclave);
        }, sanchezu -> {
            sancheztxtNombre.clear();
            sancheztxtNuevoUsuario.clear();
            marcatxtNuevaClave.clear();
            marcatxtRepetirClave.clear();
            sancheznavegador.marcamostrarPrincipal();
        });
    }

    private <T> void sanchezejecutar(Callable<T> sancheztrabajo, java.util.function.Consumer<T> sanchezalTerminar) {
        marcaocultarError();
        sanchezocupado(true);
        Task<T> sancheztarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return sancheztrabajo.call();
            }
        };
        sancheztarea.setOnSucceeded(sancheze -> {
            sanchezocupado(false);
            sanchezalTerminar.accept(sancheztarea.getValue());
        });
        sancheztarea.setOnFailed(sancheze -> {
            sanchezocupado(false);
            Throwable sanchezcausa = sancheztarea.getException();
            if (sanchezcausa instanceof SanchezNegocioException) {
                marcamostrarError(sanchezcausa.getMessage());
            } else {
                log.error("Fallo inesperado en el acceso", sanchezcausa);
                marcamostrarError("Ocurrió un error inesperado. Revisa el registro de Atlas.");
            }
        });
        Thread marcahilo = new Thread(sancheztarea, "atlas-acceso");
        marcahilo.setDaemon(true);
        marcahilo.start();
    }

    private void sanchezocupado(boolean marcavalor) {
        marcabtnEntrar.setDisable(marcavalor);
        sanchezbtnCrear.setDisable(marcavalor);
    }

    private void marcamostrarError(String sanchezmensaje) {
        marcalblError.setText(sanchezmensaje);
        marcalblError.setVisible(true);
    }

    private void marcaocultarError() {
        marcalblError.setVisible(false);
    }
}
