// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.controller.SanchezMainController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class SanchezNavegador { // K

    private final ApplicationContext marcacontexto;
    private final MarcaModoFlotante sanchezflotante;

    private Stage sanchezventana;
    private Scene sanchezescena; // Y
    private SanchezMainController marcaprincipal;

    public void sancheziniciar(Stage marcastage) {
        sanchezventana = marcastage;
        sanchezescena = new Scene(new StackPane(), 1200, 760);
        sanchezescena.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
        marcastage.setTitle("Atlas | Asistente de voz"); // Y
        marcastage.setScene(sanchezescena);
        marcastage.setMinWidth(1000);
        marcastage.setMinHeight(640); // K
        sanchezmostrarLogin(); // Y
        marcastage.show(); // Y
    }

    public void sanchezmostrarLogin() { // K
        marcaprincipal = null;
        sanchezescena.setRoot(marcacargar("/view/login-view.fxml").marcanodo());
    }

    public void marcamostrarPrincipal() {
        SanchezCargada sanchezc = marcacargar("/view/main-view.fxml");
        marcaprincipal = (SanchezMainController) sanchezc.sanchezcontrolador();
        sanchezescena.setRoot(sanchezc.marcanodo()); // Y
    }

    public void marcaactivarModoFlotante() {
        if (marcaprincipal == null || sanchezventana == null) {
            return;
        }
        sanchezflotante.marcamostrar(this::sanchezrestaurarDesdeFlotante);
        sanchezventana.hide();
    } // Y

    public void sanchezrestaurarDesdeFlotante() { // K
        sanchezflotante.marcaocultar(); // K
        if (sanchezventana != null) {
            sanchezventana.show();
            sanchezventana.toFront(); // K
        }
    }

    public boolean sanchezenModoFlotante() { // K
        return sanchezflotante.marcavisible();
    } // K

    public void marcairA(String sanchezvista) {
        if (marcaprincipal != null) {
            marcaprincipal.marcamostrar(sanchezvista);
        }
    } // Y

    private record SanchezCargada(Parent marcanodo, Object sanchezcontrolador) { }

    private SanchezCargada marcacargar(String sanchezfxml) {
        try { // K
            FXMLLoader sanchezloader = new FXMLLoader(getClass().getResource(sanchezfxml)); // K
            sanchezloader.setControllerFactory(marcacontexto::getBean);
            Parent marcanodo = sanchezloader.load();
            return new SanchezCargada(marcanodo, sanchezloader.getController());
        } catch (IOException sancheze) { // K
            throw new UncheckedIOException("No se pudo cargar " + sanchezfxml, sancheze); // Y
        }
    } // Y
}
