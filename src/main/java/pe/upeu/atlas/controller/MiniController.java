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
import pe.upeu.atlas.asistente.OrquestadorConversacion;
import pe.upeu.atlas.asistente.OrquestadorConversacion.Estado;
import pe.upeu.atlas.ui.Navegador;

/**
 * Modo flotante: una ventana pequeña, siempre visible, para pedirle cosas a Atlas con solo pulsar el micrófono.
 * Muestra únicamente lo esencial: lo último que pediste, la respuesta y los botones de hablar e interrumpir.
 */
@Component
@RequiredArgsConstructor
public class MiniController implements OrquestadorConversacion.Oyente {

    private final OrquestadorConversacion orquestador;
    private final Navegador navegador;

    private volatile String avisoPendiente; // Y
    private double arrastreX; // K
    private double arrastreY; // K

    @FXML private StackPane raiz;
    @FXML private HBox barra;
    @FXML private Label lblEstado;
    @FXML private Label lblPedido;
    @FXML private Label lblHerramienta;
    @FXML private Label lblRespuesta;
    @FXML private ScrollPane scrollRespuesta;
    @FXML private Button btnMicrofono;
    @FXML private FontIcon icoMicrofono;
    @FXML private Button btnInterrumpir;
    @FXML private TextField txtMensaje;
    @FXML private Button btnEnviar;

    @FXML
    private void initialize() {
        btnInterrumpir.managedProperty().bind(btnInterrumpir.visibleProperty());
        btnInterrumpir.setVisible(false);
        lblHerramienta.managedProperty().bind(lblHerramienta.visibleProperty()); // K
        lblHerramienta.setVisible(false);
        lblRespuesta.setText("Pulsa el micrófono y pídeme algo.");
        lblRespuesta.getStyleClass().add("text-muted");
        lblEstado.setText("Listo");

        // Arrastrar la ventana desde la barra superior
        barra.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            Window w = raiz.getScene().getWindow(); // K
            arrastreX = e.getScreenX() - w.getX();
            arrastreY = e.getScreenY() - w.getY();
        });
        barra.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> { // K
            Window w = raiz.getScene().getWindow();
            w.setX(e.getScreenX() - arrastreX);
            w.setY(e.getScreenY() - arrastreY); // Y
        });

        // F2 = hablar, Esc = interrumpir
        raiz.sceneProperty().addListener((obs, anterior, escena) -> {
            if (escena != null) {
                escena.addEventFilter(KeyEvent.KEY_PRESSED, e -> { // K
                    if (e.getCode() == KeyCode.F2) {
                        alPulsarMicrofono();
                        e.consume();
                    } else if (e.getCode() == KeyCode.ESCAPE && orquestador.getEstado() != Estado.INACTIVO) { // Y
                        alInterrumpir();
                        e.consume(); // K
                    } // K
                });
            }
        });

        orquestador.agregarOyente(this); // Y
    }

    @FXML
    private void alPulsarMicrofono() {
        if (orquestador.getEstado() == Estado.INACTIVO) {
            orquestador.turnoPorVoz();
        } else {
            orquestador.detener(); // escuchando: terminar; hablando: callar
        } // K
    }

    @FXML
    private void alInterrumpir() {
        orquestador.interrumpir();
    }

    @FXML
    private void alEnviar() {
        String texto = txtMensaje.getText(); // K
        if (texto == null || texto.isBlank() || orquestador.getEstado() != Estado.INACTIVO) { // K
            return;
        }
        txtMensaje.clear();
        orquestador.turnoPorTexto(texto); // K
    }

    @FXML
    private void alExpandir() { // K
        navegador.restaurarDesdeFlotante();
    }

    // ===== Oyente (llamado desde el hilo de conversación) =====

    @Override
    public void estado(Estado estado) {
        Platform.runLater(() -> { // K
            btnMicrofono.getStyleClass().removeAll("escuchando", "hablando");
            boolean libre = estado == Estado.INACTIVO;
            txtMensaje.setDisable(!libre); // K
            btnEnviar.setDisable(!libre);
            btnMicrofono.setDisable(estado == Estado.CARGANDO || estado == Estado.PENSANDO);
            btnInterrumpir.setVisible(estado == Estado.ESCUCHANDO || estado == Estado.PENSANDO || estado == Estado.HABLANDO); // K
            switch (estado) { // Y
                case CARGANDO -> mostrarEstado("Cargando voz...", "fth-loader");
                case ESCUCHANDO -> {
                    btnMicrofono.getStyleClass().add("escuchando");
                    mostrarEstado("Escuchando... (Esc cancela)", "fth-square"); // Y
                }
                case PENSANDO -> mostrarEstado("Pensando... (Esc interrumpe)", "fth-loader"); // Y
                case HABLANDO -> { // Y
                    btnMicrofono.getStyleClass().add("hablando"); // K
                    mostrarEstado("Hablando...", "fth-volume-x"); // Y
                }
                case INACTIVO -> {
                    String aviso = avisoPendiente;
                    avisoPendiente = null; // K
                    if (aviso != null) {
                        mostrarEstado(aviso, "fth-mic");
                        txtMensaje.requestFocus();
                        txtMensaje.end();
                    } else {
                        mostrarEstado("Listo", "fth-mic");
                    }
                }
            }
        });
    }

    @Override
    public void textoParcial(String texto) {
        Platform.runLater(() -> { // Y
            if (texto != null && !texto.isBlank()) { // Y
                lblPedido.setText("« " + texto + " »");
            }
        });
    } // K

    @Override
    public void mensajeUsuario(String texto) { // Y
        Platform.runLater(() -> {
            lblPedido.setText("Tú: " + texto);
            lblHerramienta.setVisible(false);
            ponerRespuesta("", false);
        }); // K
    }

    @Override
    public void mensajeAsistente(String texto) {
        Platform.runLater(() -> ponerRespuesta(texto, false)); // Y
    } // K

    @Override
    public void herramienta(String descripcion) { // Y
        Platform.runLater(() -> {
            lblHerramienta.setText(descripcion);
            lblHerramienta.setVisible(true);
        }); // K
    }

    @Override
    public void error(String mensaje) {
        Platform.runLater(() -> ponerRespuesta(mensaje, true));
    }

    @Override
    public void confirmarTexto(String texto) { // K
        avisoPendiente = "No estoy segura de haberte entendido. Corrige y pulsa Enter.";
        Platform.runLater(() -> txtMensaje.setText(texto)); // K
    }

    @Override
    public void interrumpido(String pedido) { // Y
        if (pedido != null && !pedido.isBlank()) {
            avisoPendiente = "Interrumpido. Corrige tu pedido y pulsa Enter."; // Y
        }
        Platform.runLater(() -> {
            lblHerramienta.setVisible(false);
            lblPedido.setText(pedido != null && !pedido.isBlank() ? "Tú: " + pedido : "");
            ponerRespuesta("Interrumpido.", false);
            if (pedido != null && !pedido.isBlank()) {
                txtMensaje.setText(pedido);
            }
        });
    }

    private void ponerRespuesta(String texto, boolean esError) { // K
        lblRespuesta.getStyleClass().removeAll("text-muted", "burbuja-error"); // Y
        if (esError) {
            lblRespuesta.getStyleClass().add("burbuja-error"); // Y
        } // Y
        lblRespuesta.setText(texto);
        scrollRespuesta.setVvalue(0);
    }

    private void mostrarEstado(String texto, String icono) {
        lblEstado.setText(texto);
        icoMicrofono.setIconLiteral(icono); // Y
    }
}
