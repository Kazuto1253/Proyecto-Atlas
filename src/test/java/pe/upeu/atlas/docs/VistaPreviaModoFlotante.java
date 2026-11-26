// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.docs;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import pe.upeu.atlas.asistente.SanchezOrquestadorConversacion.MarcaEstado;
import pe.upeu.atlas.controller.SanchezMiniController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@EnabledIfSystemProperty(named = "atlas.capturas", matches = "true")
@SpringBootTest
@ActiveProfiles("test")
class VistaPreviaModoFlotante {

    @Autowired private ApplicationContext contexto;

    @Test
    void generaLasVistas() throws Exception {
        Path salida = Path.of(System.getProperty("atlas.salida", "docs/capturas"));
        Files.createDirectories(salida);
        Platform.startup(() -> { });
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/mini-view.fxml"));
        loader.setControllerFactory(contexto::getBean);
        Parent mini = fx(() -> {
            javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
            try {
                return loader.load();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        SanchezMiniController c = loader.getController();
        StackPane fondo = new StackPane(mini);
        fondo.setStyle("-fx-background-color: linear-gradient(to bottom right, #6b7a99, #cfd8e6); -fx-padding: 30;");
        fx(() -> {
            Stage ventana = new Stage(StageStyle.UNDECORATED);
            Scene escena = new Scene(fondo);
            escena.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            ventana.setScene(escena);
            ventana.setX(60);
            ventana.setY(60);
            ventana.show();
            return null;
        });

        foto(fondo, salida.resolve("mini-1-inactivo.png"));

        c.sanchezmensajeUsuario("Toma una captura de pantalla");
        c.marcaestado(MarcaEstado.PENSANDO);
        Thread.sleep(300);
        foto(fondo, salida.resolve("mini-2-pensando.png"));

        c.marcaherramienta("Tomó una captura de pantalla");
        c.sanchezmensajeAsistente("Listo, guardé la captura en la carpeta de capturas de Atlas.");
        c.marcaestado(MarcaEstado.INACTIVO);
        Thread.sleep(300);
        foto(fondo, salida.resolve("mini-3-respuesta.png"));

        c.marcaestado(MarcaEstado.ESCUCHANDO);
        c.marcatextoParcial("abre la carpeta de");
        Thread.sleep(300);
        foto(fondo, salida.resolve("mini-4-escuchando.png"));

        c.marcainterrumpido("Abre la carpeta de descargas");
        c.marcaestado(MarcaEstado.INACTIVO);
        Thread.sleep(300);
        foto(fondo, salida.resolve("mini-5-interrumpido.png"));
        System.out.println("MINI -> " + salida.toAbsolutePath());
    }

    private void foto(StackPane nodo, Path destino) throws Exception {
        BufferedImage img = fx(() -> {
            nodo.applyCss();
            nodo.layout();
            SnapshotParameters p = new SnapshotParameters();
            p.setTransform(Transform.scale(2, 2));
            WritableImage wi = nodo.snapshot(p, null);
            int w = (int) wi.getWidth(), h = (int) wi.getHeight();
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            PixelReader r = wi.getPixelReader();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    bi.setRGB(x, y, r.getArgb(x, y));
                }
            }
            return bi;
        });
        ImageIO.write(img, "png", destino.toFile());
    }

    private static <T> T fx(Supplier<T> trabajo) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                f.complete(trabajo.get());
            } catch (Throwable e) {
                f.completeExceptionally(e);
            }
        });
        return f.get(60, TimeUnit.SECONDS);
    }
}
