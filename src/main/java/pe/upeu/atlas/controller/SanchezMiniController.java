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

@Component
@RequiredArgsConstructor
public class SanchezMiniController implements SanchezOrquestadorConversacion.MarcaOyente {

    private final SanchezOrquestadorConversacion sanchezorquestador;
    private final SanchezNavegador sancheznavegador;

    private volatile String marcaavisoPendiente;
    private double marcaarrastreX;
    private double sanchezarrastreY;

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
        marcalblHerramienta.managedProperty().bind(marcalblHerramienta.visibleProperty());
        marcalblHerramienta.setVisible(false);
        marcalblRespuesta.setText("Pulsa el micrófono y pídeme algo.");
        marcalblRespuesta.getStyleClass().add("text-muted");
        marcalblEstado.setText("Listo");

        marcabarra.addEventHandler(MouseEvent.MOUSE_PRESSED, sancheze -> {
            Window sanchezw = marcaraiz.getScene().getWindow();
            marcaarrastreX = sancheze.getScreenX() - sanchezw.getX();
            sanchezarrastreY = sancheze.getScreenY() - sanchezw.getY();
        });
        marcabarra.addEventHandler(MouseEvent.MOUSE_DRAGGED, sancheze -> {
            Window sanchezw = marcaraiz.getScene().getWindow();
            sanchezw.setX(sancheze.getScreenX() - marcaarrastreX);
            sanchezw.setY(sancheze.getScreenY() - sanchezarrastreY);
        });

        marcaraiz.sceneProperty().addListener((marcaobs, marcaanterior, sanchezescena) -> {
            if (sanchezescena != null) {
                sanchezescena.addEventFilter(KeyEvent.KEY_PRESSED, sancheze -> {
                    if (sancheze.getCode() == KeyCode.F2) {
                        marcaalPulsarMicrofono();
                        sancheze.consume();
                    } else if (sancheze.getCode() == KeyCode.ESCAPE && sanchezorquestador.marcagetEstado() != MarcaEstado.INACTIVO) {
                        marcaalInterrumpir();
                        sancheze.consume();
                    }
                });
            }
        });

        sanchezorquestador.sanchezagregarOyente(this);
    }

    @FXML
    private void marcaalPulsarMicrofono() {
        if (sanchezorquestador.marcagetEstado() == MarcaEstado.INACTIVO) {
            sanchezorquestador.marcaturnoPorVoz();
        } else {
            sanchezorquestador.sanchezdetener();
        }
    }

    @FXML
    private void marcaalInterrumpir() {
        sanchezorquestador.sanchezinterrumpir();
    }

    @FXML
    private void marcaalEnviar() {
        String marcatexto = sancheztxtMensaje.getText();
        if (marcatexto == null || marcatexto.isBlank() || sanchezorquestador.marcagetEstado() != MarcaEstado.INACTIVO) {
            return;
        }
        sancheztxtMensaje.clear();
        sanchezorquestador.sanchezturnoPorTexto(marcatexto);
    }

    @FXML
    private void marcaalExpandir() {
        sancheznavegador.sanchezrestaurarDesdeFlotante();
    }

    @Override
    public void marcaestado(MarcaEstado marcaestado) {
        Platform.runLater(() -> {
            marcabtnMicrofono.getStyleClass().removeAll("escuchando", "hablando");
            boolean marcalibre = marcaestado == MarcaEstado.INACTIVO;
            sancheztxtMensaje.setDisable(!marcalibre);
            sanchezbtnEnviar.setDisable(!marcalibre);
            marcabtnMicrofono.setDisable(marcaestado == MarcaEstado.CARGANDO || marcaestado == MarcaEstado.PENSANDO);
            sanchezbtnInterrumpir.setVisible(marcaestado == MarcaEstado.ESCUCHANDO || marcaestado == MarcaEstado.PENSANDO || marcaestado == MarcaEstado.HABLANDO);
            switch (marcaestado) {
                case CARGANDO -> marcamostrarEstado("Cargando voz...", "fth-loader");
                case ESCUCHANDO -> {
                    marcabtnMicrofono.getStyleClass().add("escuchando");
                    marcamostrarEstado("Escuchando... (Esc cancela)", "fth-square");
                }
                case PENSANDO -> marcamostrarEstado("Pensando... (Esc interrumpe)", "fth-loader");
                case HABLANDO -> {
                    marcabtnMicrofono.getStyleClass().add("hablando");
                    marcamostrarEstado("Hablando...", "fth-volume-x");
                }
                case INACTIVO -> {
                    String marcaaviso = marcaavisoPendiente;
                    marcaavisoPendiente = null;
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
        Platform.runLater(() -> {
            if (marcatexto != null && !marcatexto.isBlank()) {
                sanchezlblPedido.setText("« " + marcatexto + " »");
            }
        });
    }

    @Override
    public void sanchezmensajeUsuario(String marcatexto) {
        Platform.runLater(() -> {
            sanchezlblPedido.setText("Tú: " + marcatexto);
            marcalblHerramienta.setVisible(false);
            marcaponerRespuesta("", false);
        });
    }

    @Override
    public void sanchezmensajeAsistente(String marcatexto) {
        Platform.runLater(() -> marcaponerRespuesta(marcatexto, false));
    }

    @Override
    public void marcaherramienta(String sanchezdescripcion) {
        Platform.runLater(() -> {
            marcalblHerramienta.setText(sanchezdescripcion);
            marcalblHerramienta.setVisible(true);
        });
    }

    @Override
    public void error(String sanchezmensaje) {
        Platform.runLater(() -> marcaponerRespuesta(sanchezmensaje, true));
    }

    @Override
    public void sanchezconfirmarTexto(String marcatexto) {
        marcaavisoPendiente = "No estoy segura de haberte entendido. Corrige y pulsa Enter.";
        Platform.runLater(() -> sancheztxtMensaje.setText(marcatexto));
    }

    @Override
    public void marcainterrumpido(String sanchezpedido) {
        if (sanchezpedido != null && !sanchezpedido.isBlank()) {
            marcaavisoPendiente = "Interrumpido. Corrige tu pedido y pulsa Enter.";
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

    private void marcaponerRespuesta(String marcatexto, boolean marcaesError) {
        marcalblRespuesta.getStyleClass().removeAll("text-muted", "burbuja-error");
        if (marcaesError) {
            marcalblRespuesta.getStyleClass().add("burbuja-error");
        }
        marcalblRespuesta.setText(marcatexto);
        sanchezscrollRespuesta.setVvalue(0);
    }

    private void marcamostrarEstado(String marcatexto, String marcaicono) {
        marcalblEstado.setText(marcatexto);
        sanchezicoMicrofono.setIconLiteral(marcaicono);
    }
}
