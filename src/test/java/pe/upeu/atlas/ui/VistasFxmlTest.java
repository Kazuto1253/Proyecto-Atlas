package pe.upeu.atlas.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.seguridad.SanchezServicioAutenticacion;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@SpringBootTest
@ActiveProfiles("test")
class VistasFxmlTest {

    private static final List<String> VISTAS = List.of(
            "/view/login-view.fxml",
            "/view/main-view.fxml",
            "/view/dashboard-view.fxml",
            "/view/asistente-view.fxml",
            "/view/diagnostico-view.fxml",
            "/view/usuarios-view.fxml",
            "/view/historial-view.fxml",
            "/view/documentos-view.fxml",
            "/view/reportes-view.fxml",
            "/view/mini-view.fxml");

    private static volatile boolean toolkitDisponible = true;

    @Autowired private ApplicationContext contexto;
    @Autowired private SanchezServicioAutenticacion autenticacion;

    @BeforeAll
    static void iniciarJavaFx() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException yaIniciado) {
        } catch (Throwable sinPantalla) {
            toolkitDisponible = false;
        }
    }

    @Test
    void todasLasVistasCargan() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless() || !toolkitDisponible,
                "Sin pantalla: no se puede iniciar JavaFX");
        autenticacion.sanchezregistrar("Admin Vistas", "adminvistas", "secreto1", SanchezRol.ADMINISTRADOR);
        autenticacion.marcainiciarSesion("adminvistas", "secreto1");

        for (String fxml : VISTAS) {
            Parent nodo = enHiloFx(fxml);
            assertThat(nodo).as(fxml).isNotNull();
        }
        autenticacion.marcacerrarSesion();
    }

    private Parent enHiloFx(String fxml) throws Exception {
        CompletableFuture<Parent> resultado = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
                loader.setControllerFactory(contexto::getBean);
                resultado.complete(loader.load());
            } catch (Throwable e) {
                resultado.completeExceptionally(new AssertionError("No cargó " + fxml + ": " + e, e));
            }
        });
        return resultado.get(30, TimeUnit.SECONDS);
    }
}
