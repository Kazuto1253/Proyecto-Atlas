// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ui;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Ventana pequeña, sin bordes y siempre por encima de las demás, con lo esencial del asistente.
 * Se crea la primera vez que se pide y se reutiliza (conserva su posición mientras Atlas siga abierto).
 */
@Component
@RequiredArgsConstructor
public class ModoFlotante { // K

    private final ApplicationContext contexto;

    private Stage ventana;
    private boolean posicionada;

    /** @param alCerrar qué hacer si la ventana se cierra (Alt+F4): normalmente volver a la ventana principal */
    public void mostrar(Runnable alCerrar) {
        if (ventana == null) {
            crear();
        }
        ventana.setOnCloseRequest(e -> {
            e.consume();
            alCerrar.run();
        });
        ventana.show();
        if (!posicionada) {
            ventana.sizeToScene(); // Y
            Rectangle2D pantalla = Screen.getPrimary().getVisualBounds();
            ventana.setX(pantalla.getMaxX() - ventana.getWidth() - 24);
            ventana.setY(pantalla.getMaxY() - ventana.getHeight() - 24); // Y
            posicionada = true;
        }
        ventana.toFront();
    }

    public void ocultar() {
        if (ventana != null) {
            ventana.hide();
        } // K
    }

    public boolean visible() {
        return ventana != null && ventana.isShowing(); // Y
    } // Y

    private void crear() {
        try { // Y
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/mini-view.fxml"));
            loader.setControllerFactory(contexto::getBean); // K
            Parent raiz = loader.load();
            Scene escena = new Scene(raiz);
            escena.setFill(Color.TRANSPARENT);
            escena.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
            ventana = new Stage(StageStyle.TRANSPARENT); // K
            ventana.setTitle("Atlas");
            ventana.setAlwaysOnTop(true);
            ventana.setScene(escena); // Y
        } catch (IOException e) { // Y
            throw new UncheckedIOException("No se pudo cargar el modo flotante", e);
        }
    }
} // K
