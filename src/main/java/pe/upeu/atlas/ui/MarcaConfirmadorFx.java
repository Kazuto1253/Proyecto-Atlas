package pe.upeu.atlas.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.seguridad.SanchezConfirmadorAcciones;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class MarcaConfirmadorFx implements SanchezConfirmadorAcciones {

    @Override
    public boolean sanchezconfirmar(String sancheztitulo, String sanchezmensaje) {
        if (Platform.isFxApplicationThread()) {
            return marcapreguntar(sancheztitulo, sanchezmensaje);
        }
        CompletableFuture<Boolean> marcarespuesta = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                marcarespuesta.complete(marcapreguntar(sancheztitulo, sanchezmensaje));
            } catch (RuntimeException sancheze) {
                marcarespuesta.complete(false);
            }
        });
        try {
            return marcarespuesta.get(60, TimeUnit.SECONDS);
        } catch (InterruptedException sancheze) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception sancheze) {
            return false;
        }
    }

    private boolean marcapreguntar(String sancheztitulo, String sanchezmensaje) {
        Alert sanchezalerta = new Alert(Alert.AlertType.CONFIRMATION, sanchezmensaje, ButtonType.YES, ButtonType.NO);
        sanchezalerta.setTitle("Atlas");
        sanchezalerta.setHeaderText(sancheztitulo);
        sanchezalerta.getDialogPane().getButtonTypes().stream()
                .filter(marcat -> marcat == ButtonType.NO)
                .findFirst()
                .ifPresent(marcat -> ((javafx.scene.control.Button) sanchezalerta.getDialogPane().lookupButton(marcat)).setDefaultButton(true));
        return sanchezalerta.showAndWait().filter(marcat -> marcat == ButtonType.YES).isPresent();
    }
}
