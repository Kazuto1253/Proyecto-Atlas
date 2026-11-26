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

@Component
@RequiredArgsConstructor
public class MarcaModoFlotante { // K

    private final ApplicationContext marcacontexto;

    private Stage sanchezventana;
    private boolean marcaposicionada;

    public void marcamostrar(Runnable marcaalCerrar) {
        if (sanchezventana == null) {
            sanchezcrear();
        }
        sanchezventana.setOnCloseRequest(sancheze -> {
            sancheze.consume();
            marcaalCerrar.run();
        });
        sanchezventana.show();
        if (!marcaposicionada) {
            sanchezventana.sizeToScene(); // Y
            Rectangle2D sanchezpantalla = Screen.getPrimary().getVisualBounds();
            sanchezventana.setX(sanchezpantalla.getMaxX() - sanchezventana.getWidth() - 24);
            sanchezventana.setY(sanchezpantalla.getMaxY() - sanchezventana.getHeight() - 24); // Y
            marcaposicionada = true;
        }
        sanchezventana.toFront();
    }

    public void marcaocultar() {
        if (sanchezventana != null) {
            sanchezventana.hide();
        } // K
    }

    public boolean marcavisible() {
        return sanchezventana != null && sanchezventana.isShowing(); // Y
    } // Y

    private void sanchezcrear() {
        try { // Y
            FXMLLoader sanchezloader = new FXMLLoader(getClass().getResource("/view/mini-view.fxml"));
            sanchezloader.setControllerFactory(marcacontexto::getBean); // K
            Parent marcaraiz = sanchezloader.load();
            Scene sanchezescena = new Scene(marcaraiz);
            sanchezescena.setFill(Color.TRANSPARENT);
            sanchezescena.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
            sanchezventana = new Stage(StageStyle.TRANSPARENT); // K
            sanchezventana.setTitle("Atlas");
            sanchezventana.setAlwaysOnTop(true);
            sanchezventana.setScene(sanchezescena); // Y
        } catch (IOException sancheze) { // Y
            throw new UncheckedIOException("No se pudo cargar el modo flotante", sancheze);
        }
    }
} // K
