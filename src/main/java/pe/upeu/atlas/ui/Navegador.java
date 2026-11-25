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
import pe.upeu.atlas.controller.MainController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/** Cambia entre la pantalla de acceso y la ventana principal dentro de la misma ventana. */
@Component
@RequiredArgsConstructor
public class Navegador { // K

    private final ApplicationContext contexto;
    private final ModoFlotante flotante;

    private Stage ventana;
    private Scene escena; // Y
    private MainController principal;

    /** Prepara la ventana y muestra el acceso. Se llama una vez, desde el hilo de JavaFX. */
    public void iniciar(Stage stage) {
        ventana = stage;
        escena = new Scene(new StackPane(), 1200, 760);
        escena.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
        stage.setTitle("Atlas | Asistente de voz"); // Y
        stage.setScene(escena);
        stage.setMinWidth(1000);
        stage.setMinHeight(640); // K
        mostrarLogin(); // Y
        stage.show(); // Y
    }

    public void mostrarLogin() { // K
        principal = null;
        escena.setRoot(cargar("/view/login-view.fxml").nodo());
    }

    public void mostrarPrincipal() {
        Cargada c = cargar("/view/main-view.fxml");
        principal = (MainController) c.controlador();
        escena.setRoot(c.nodo()); // Y
    }

    /** Oculta la ventana principal y muestra el asistente pequeño, siempre visible. */
    public void activarModoFlotante() {
        if (principal == null || ventana == null) {
            return;
        }
        flotante.mostrar(this::restaurarDesdeFlotante);
        ventana.hide();
    } // Y

    /** Cierra el asistente pequeño y vuelve a la ventana principal. */
    public void restaurarDesdeFlotante() { // K
        flotante.ocultar(); // K
        if (ventana != null) {
            ventana.show();
            ventana.toFront(); // K
        }
    }

    public boolean enModoFlotante() { // K
        return flotante.visible();
    } // K

    /** Muestra una vista de la ventana principal (si ya se abrió). */
    public void irA(String vista) {
        if (principal != null) {
            principal.mostrar(vista);
        }
    } // Y

    private record Cargada(Parent nodo, Object controlador) { }

    private Cargada cargar(String fxml) {
        try { // K
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml)); // K
            loader.setControllerFactory(contexto::getBean);
            Parent nodo = loader.load();
            return new Cargada(nodo, loader.getController());
        } catch (IOException e) { // K
            throw new UncheckedIOException("No se pudo cargar " + fxml, e); // Y
        }
    } // Y
}
