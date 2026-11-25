// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.seguridad.ConfirmadorAcciones;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Confirmación mediante un diálogo de JavaFX; si nadie responde en 60 s se considera un "no". */
@Component
public class ConfirmadorFx implements ConfirmadorAcciones {

    @Override
    public boolean confirmar(String titulo, String mensaje) { // Y
        if (Platform.isFxApplicationThread()) {
            return preguntar(titulo, mensaje);
        }
        CompletableFuture<Boolean> respuesta = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { // Y
                respuesta.complete(preguntar(titulo, mensaje));
            } catch (RuntimeException e) {
                respuesta.complete(false);
            }
        });
        try { // K
            return respuesta.get(60, TimeUnit.SECONDS); // Y
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) { // K
            return false;
        }
    } // K

    private boolean preguntar(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, mensaje, ButtonType.YES, ButtonType.NO);
        alerta.setTitle("Atlas");
        alerta.setHeaderText(titulo); // K
        alerta.getDialogPane().getButtonTypes().stream()
                .filter(t -> t == ButtonType.NO)
                .findFirst()
                .ifPresent(t -> ((javafx.scene.control.Button) alerta.getDialogPane().lookupButton(t)).setDefaultButton(true));
        return alerta.showAndWait().filter(t -> t == ButtonType.YES).isPresent();
    }
}
