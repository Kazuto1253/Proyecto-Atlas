// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.asistente.SanchezOrquestadorConversacion;
import pe.upeu.atlas.asistente.SanchezOrquestadorConversacion.MarcaEstado;
import pe.upeu.atlas.ui.SanchezNavegador;

/**
 * Modo flotante: una ventana pequeña, siempre visible, para pedirle cosas a Atlas con solo pulsar el micrófono.
 * Muestra únicamente lo esencial: lo último que pediste, la respuesta y los botones de hablar e interrumpir.
 */
@Component
@RequiredArgsConstructor
public class SanchezMiniController implements SanchezOrquestadorConversacion.MarcaOyente {

    private final SanchezOrquestadorConversacion sanchezorquestador;
    private final SanchezNavegador sancheznavegador;

    private volatile String marcaavisoPendiente; // Y
    private double marcaarrastreX; // K
    private double sanchezarrastreY; // K

    @FXML private StackPane marcaraiz;
    @FXML private HBox marcabarra;
    @FXML private Label marcalblEstado;
    @FXML private Label sanchezlblPedido;
    @FXML private Label marcalblHerramienta;
    @FXML private Label marcalblRespuesta;
    @FXML private ScrollPane sanchezscrollRespuesta;
    @FXML private Button marcabtnMicrofono;
    @FXML private FontIcon sanchezicoMicrofono;
    @FXML private Button sanchezbtnInterrumpir;
    @FXML private TextField sancheztxtMensaje;
    @FXML private Button sanchezbtnEnviar;

    @FXML
    private void initialize() {
        sanchezbtnInterrumpir.managedProperty().bind(sanchezbtnInterrumpir.visibleProperty());
        sanchezbtnInterrumpir.setVisible(false);
        marcalblHerramienta.managedProperty().bind(marcalblHerramienta.visibleProperty()); // K
        marcalblHerramienta.setVisible(false);
        marcalblRespuesta.setText("Pulsa el micrófono y pídeme algo.");
        marcalblRespuesta.getStyleClass().add("text-muted");
        marcalblEstado.setText("Listo");

        // Arrastrar la ventana desde la barra superior
        marcabarra.addEventHandler(MouseEvent.MOUSE_PRESSED, sancheze -> {
            Window sanchezw = marcaraiz.getScene().getWindow(); // K
            marcaarrastreX = sancheze.getScreenX() - sanchezw.getX();
            sanchezarrastreY = sancheze.getScreenY() - sanchezw.getY();
        });
        marcabarra.addEventHandler(MouseEvent.MOUSE_DRAGGED, sancheze -> { // K
            Window sanchezw = marcaraiz.getScene().getWindow();
            sanchezw.setX(sancheze.getScreenX() - marcaarrastreX);
            sanchezw.setY(sancheze.getScreenY() - sanchezarrastreY); // Y
        });

        // F2 = hablar, Esc = interrumpir
        marcaraiz.sceneProperty().addListener((marcaobs, marcaanterior, sanchezescena) -> {
            if (sanchezescena != null) {
                sanchezescena.addEventFilter(KeyEvent.KEY_PRESSED, sancheze -> { // K
                    if (sancheze.getCode() == KeyCode.F2) {
                        marcaalPulsarMicrofono();
                        sancheze.consume();
                    } else if (sancheze.getCode() == KeyCode.ESCAPE && sanchezorquestador.marcagetEstado() != MarcaEstado.INACTIVO) { // Y
                        marcaalInterrumpir();
                        sancheze.consume(); // K
                    } // K
                });
            }
        });

        sanchezorquestador.sanchezagregarOyente(this); // Y
    }

    @FXML
    private void marcaalPulsarMicrofono() {
        if (sanchezorquestador.marcagetEstado() == MarcaEstado.INACTIVO) {
            sanchezorquestador.marcaturnoPorVoz();
        } else {
            sanchezorquestador.sanchezdetener(); // escuchando: terminar; hablando: callar
        } // K
    }

    @FXML
    private void marcaalInterrumpir() {
        sanchezorquestador.sanchezinterrumpir();
    }

    @FXML
    private void marcaalEnviar() {
        String marcatexto = sancheztxtMensaje.getText(); // K
        if (marcatexto == null || marcatexto.isBlank() || sanchezorquestador.marcagetEstado() != MarcaEstado.INACTIVO) { // K
            return;
        }
        sancheztxtMensaje.clear();
        sanchezorquestador.sanchezturnoPorTexto(marcatexto); // K
    }

    @FXML
    private void marcaalExpandir() { // K
        sancheznavegador.sanchezrestaurarDesdeFlotante();
    }

    // ===== Oyente (llamado desde el hilo de conversación) =====

    @Override
    public void marcaestado(MarcaEstado marcaestado) {
        Platform.runLater(() -> { // K
            marcabtnMicrofono.getStyleClass().removeAll("escuchando", "hablando");
            boolean marcalibre = marcaestado == MarcaEstado.INACTIVO;
            sancheztxtMensaje.setDisable(!marcalibre); // K
            sanchezbtnEnviar.setDisable(!marcalibre);
            marcabtnMicrofono.setDisable(marcaestado == MarcaEstado.CARGANDO || marcaestado == MarcaEstado.PENSANDO);
            sanchezbtnInterrumpir.setVisible(marcaestado == MarcaEstado.ESCUCHANDO || marcaestado == MarcaEstado.PENSANDO || marcaestado == MarcaEstado.HABLANDO); // K
            switch (marcaestado) { // Y
                case CARGANDO -> marcamostrarEstado("Cargando voz...", "fth-loader");
                case ESCUCHANDO -> {
                    marcabtnMicrofono.getStyleClass().add("escuchando");
                    marcamostrarEstado("Escuchando... (Esc cancela)", "fth-square"); // Y
                }
                case PENSANDO -> marcamostrarEstado("Pensando... (Esc interrumpe)", "fth-loader"); // Y
                case HABLANDO -> { // Y
                    marcabtnMicrofono.getStyleClass().add("hablando"); // K
                    marcamostrarEstado("Hablando...", "fth-volume-x"); // Y
                }
                case INACTIVO -> {
                    String marcaaviso = marcaavisoPendiente;
                    marcaavisoPendiente = null; // K
                    if (marcaaviso != null) {
                        marcamostrarEstado(marcaaviso, "fth-mic");
                        sancheztxtMensaje.requestFocus();
                        sancheztxtMensaje.end();
                    } else {
                        marcamostrarEstado("Listo", "fth-mic");
                    }
                }
            }
        });
    }

    @Override
    public void marcatextoParcial(String marcatexto) {
        Platform.runLater(() -> { // Y
            if (marcatexto != null && !marcatexto.isBlank()) { // Y
                sanchezlblPedido.setText("« " + marcatexto + " »");
            }
        });
    } // K

    @Override
    public void sanchezmensajeUsuario(String marcatexto) { // Y
        Platform.runLater(() -> {
            sanchezlblPedido.setText("Tú: " + marcatexto);
            marcalblHerramienta.setVisible(false);
            marcaponerRespuesta("", false);
        }); // K
    }

    @Override
    public void sanchezmensajeAsistente(String marcatexto) {
        Platform.runLater(() -> marcaponerRespuesta(marcatexto, false)); // Y
    } // K

    @Override
    public void marcaherramienta(String sanchezdescripcion) { // Y
        Platform.runLater(() -> {
            marcalblHerramienta.setText(sanchezdescripcion);
            marcalblHerramienta.setVisible(true);
        }); // K
    }

    @Override
    public void error(String sanchezmensaje) {
        Platform.runLater(() -> marcaponerRespuesta(sanchezmensaje, true));
    }

    @Override
    public void sanchezconfirmarTexto(String marcatexto) { // K
        marcaavisoPendiente = "No estoy segura de haberte entendido. Corrige y pulsa Enter.";
        Platform.runLater(() -> sancheztxtMensaje.setText(marcatexto)); // K
    }

    @Override
    public void marcainterrumpido(String sanchezpedido) { // Y
        if (sanchezpedido != null && !sanchezpedido.isBlank()) {
            marcaavisoPendiente = "Interrumpido. Corrige tu pedido y pulsa Enter."; // Y
        }
        Platform.runLater(() -> {
            marcalblHerramienta.setVisible(false);
            sanchezlblPedido.setText(sanchezpedido != null && !sanchezpedido.isBlank() ? "Tú: " + sanchezpedido : "");
            marcaponerRespuesta("Interrumpido.", false);
            if (sanchezpedido != null && !sanchezpedido.isBlank()) {
                sancheztxtMensaje.setText(sanchezpedido);
            }
        });
    }

    private void marcaponerRespuesta(String marcatexto, boolean marcaesError) { // K
        marcalblRespuesta.getStyleClass().removeAll("text-muted", "burbuja-error"); // Y
        if (marcaesError) {
            marcalblRespuesta.getStyleClass().add("burbuja-error"); // Y
        } // Y
        marcalblRespuesta.setText(marcatexto);
        sanchezscrollRespuesta.setVvalue(0);
    }

    private void marcamostrarEstado(String marcatexto, String marcaicono) {
        marcalblEstado.setText(marcatexto);
        sanchezicoMicrofono.setIconLiteral(marcaicono); // Y
    }
}
