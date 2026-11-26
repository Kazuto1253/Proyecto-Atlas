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
public class SanchezNavegador {

    private final ApplicationContext marcacontexto;
    private final MarcaModoFlotante sanchezflotante;

    private Stage sanchezventana;
    private Scene sanchezescena;
    private SanchezMainController marcaprincipal;

    public void sancheziniciar(Stage marcastage) {
        sanchezventana = marcastage;
        sanchezescena = new Scene(new StackPane(), 1200, 760);
        sanchezescena.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
        marcastage.setTitle("Atlas | Asistente de voz");
        marcastage.setScene(sanchezescena);
        marcastage.setMinWidth(1000);
        marcastage.setMinHeight(640);
        sanchezmostrarLogin();
        marcastage.show();
    }

    public void sanchezmostrarLogin() {
        marcaprincipal = null;
        sanchezescena.setRoot(marcacargar("/view/login-view.fxml").marcanodo());
    }

    public void marcamostrarPrincipal() {
        SanchezCargada sanchezc = marcacargar("/view/main-view.fxml");
        marcaprincipal = (SanchezMainController) sanchezc.sanchezcontrolador();
        sanchezescena.setRoot(sanchezc.marcanodo());
    }

    public void marcaactivarModoFlotante() {
        if (marcaprincipal == null || sanchezventana == null) {
            return;
        }
        sanchezflotante.marcamostrar(this::sanchezrestaurarDesdeFlotante);
        sanchezventana.hide();
    }

    public void sanchezrestaurarDesdeFlotante() {
        sanchezflotante.marcaocultar();
        if (sanchezventana != null) {
            sanchezventana.show();
            sanchezventana.toFront();
        }
    }

    public boolean sanchezenModoFlotante() {
        return sanchezflotante.marcavisible();
    }

    public void marcairA(String sanchezvista) {
        if (marcaprincipal != null) {
            marcaprincipal.marcamostrar(sanchezvista);
        }
    }

    private record SanchezCargada(Parent marcanodo, Object sanchezcontrolador) { }

    private SanchezCargada marcacargar(String sanchezfxml) {
        try {
            FXMLLoader sanchezloader = new FXMLLoader(getClass().getResource(sanchezfxml));
            sanchezloader.setControllerFactory(marcacontexto::getBean);
            Parent marcanodo = sanchezloader.load();
            return new SanchezCargada(marcanodo, sanchezloader.getController());
        } catch (IOException sancheze) {
            throw new UncheckedIOException("No se pudo cargar " + sanchezfxml, sancheze);
        }
    }
}
