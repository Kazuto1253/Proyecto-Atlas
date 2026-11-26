// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.asistente.SanchezOrquestadorConversacion;
import pe.upeu.atlas.asistente.SanchezOrquestadorConversacion.MarcaEstado;

@Component
@RequiredArgsConstructor
public class MarcaAsistenteController implements SanchezOrquestadorConversacion.MarcaOyente {

    private final SanchezOrquestadorConversacion sanchezorquestador;
    private final pe.upeu.atlas.ui.SanchezNavegador sancheznavegador;

    private volatile String marcaavisoPendiente; // K

    @FXML private Label marcalblEstado;
    @FXML private Label marcalblParcial;
    @FXML private ScrollPane sanchezscrollChat;
    @FXML private VBox sanchezcontenedorChat;
    @FXML private VBox sanchezbienvenida;
    @FXML private Button marcabtnMicrofono;
    @FXML private Button sanchezbtnInterrumpir;
    @FXML private FontIcon sanchezicoMicrofono;
    @FXML private TextField sancheztxtMensaje;
    @FXML private Button sanchezbtnEnviar;
    @FXML private ToggleButton marcatglVoz;
    @FXML private FontIcon marcaicoVoz;

    @FXML
    private void initialize() {
        marcalblParcial.managedProperty().bind(marcalblParcial.visibleProperty());
        marcalblParcial.setVisible(false); // K
        sanchezbtnInterrumpir.managedProperty().bind(sanchezbtnInterrumpir.visibleProperty());
        sanchezbtnInterrumpir.setVisible(false);

        sanchezcontenedorChat.heightProperty().addListener((marcaobs, sancheza, marcab) -> sanchezscrollChat.setVvalue(1.0));

        marcabtnMicrofono.sceneProperty().addListener((marcaobs, marcaanterior, sanchezescena) -> {
            if (sanchezescena != null) {
                sanchezescena.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, sancheze -> {
                    if (sancheze.getCode() == KeyCode.F2) {
                        marcaalPulsarMicrofono();
                        sancheze.consume(); // K
                    } else if (sancheze.getCode() == KeyCode.ESCAPE && sanchezorquestador.marcagetEstado() != MarcaEstado.INACTIVO) { // K
                        marcaalInterrumpir();
                        sancheze.consume();
                    }
                });
            }
        });

        sanchezorquestador.sanchezagregarOyente(this);
        sanchezorquestador.sanchezprecargar();
    } // K

    @FXML
    private void marcaalPulsarMicrofono() {
        if (sanchezorquestador.marcagetEstado() == MarcaEstado.INACTIVO) { // Y
            sanchezorquestador.marcaturnoPorVoz();
        } else { // K
            sanchezorquestador.sanchezdetener();
        }
    } // Y

    @FXML
    private void sanchezalModoFlotante() {
        sancheznavegador.marcaactivarModoFlotante();
    }

    @FXML
    private void marcaalInterrumpir() { // Y
        sanchezorquestador.sanchezinterrumpir(); // Y
    }

    @FXML
    private void marcaalEnviarTexto() { // Y
        String marcatexto = sancheztxtMensaje.getText(); // K
        if (marcatexto == null || marcatexto.isBlank() || sanchezorquestador.marcagetEstado() != MarcaEstado.INACTIVO) {
            return;
        }
        sancheztxtMensaje.clear();
        sanchezorquestador.sanchezturnoPorTexto(marcatexto); // Y
    }

    @FXML
    private void sanchezalCambiarVoz() {
        boolean marcaactiva = marcatglVoz.isSelected(); // Y
        sanchezorquestador.marcasetVozActivada(marcaactiva);
        marcaicoVoz.setIconLiteral(marcaactiva ? "fth-volume-2" : "fth-volume-x"); // K
        if (!marcaactiva) {
            sanchezorquestador.sanchezdetener(); // Y
        } // K
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
            switch (marcaestado) { // Y
                case CARGANDO -> marcamostrarEstado("Cargando modelo de voz...", "fth-loader");
                case ESCUCHANDO -> {
                    marcabtnMicrofono.getStyleClass().add("escuchando");
                    marcamostrarEstado("Escuchando... (micrófono: terminar · Esc: cancelar)", "fth-square");
                } // K
                case PENSANDO -> marcamostrarEstado("Pensando... (Esc o el botón rojo para interrumpir)", "fth-loader");
                case HABLANDO -> {
                    marcabtnMicrofono.getStyleClass().add("hablando"); // Y
                    marcamostrarEstado("Hablando... (pulsa para callar)", "fth-volume-x"); // K
                } // K
                case INACTIVO -> { // K
                    String marcaaviso = marcaavisoPendiente; // K
                    marcaavisoPendiente = null;
                    if (marcaaviso != null) { // Y
                        marcamostrarEstado(marcaaviso, "fth-mic");
                        sancheztxtMensaje.requestFocus(); // Y
                        sancheztxtMensaje.end();
                    } else { // K
                        marcamostrarEstado("Listo. Pulsa el micrófono o F2 para hablar.", "fth-mic");
                    }
                }
            } // Y
        });
    } // Y

    @Override
    public void marcatextoParcial(String marcatexto) { // Y
        Platform.runLater(() -> { // K
            marcalblParcial.setText(marcatexto); // Y
            marcalblParcial.setVisible(!marcatexto.isBlank()); // K
        });
    }

    @Override
    public void sanchezmensajeUsuario(String marcatexto) {
        Platform.runLater(() -> marcaagregarBurbuja(marcatexto, true));
    }

    @Override
    public void sanchezmensajeAsistente(String marcatexto) { // K
        Platform.runLater(() -> marcaagregarBurbuja(marcatexto, false));
    }

    @Override
    public void sanchezconfirmarTexto(String marcatexto) {
        marcaavisoPendiente = "No estoy segura de haberte entendido bien. Revisa el texto, corrígelo si hace falta y pulsa Enter.";
        Platform.runLater(() -> sancheztxtMensaje.setText(marcatexto)); // Y
    }

    @Override
    public void marcainterrumpido(String sanchezpedido) {
        if (sanchezpedido != null && !sanchezpedido.isBlank()) { // Y
            marcaavisoPendiente = "Interrumpiste a Atlas. Corrige tu pedido y pulsa Enter para enviarlo.";
        }
        Platform.runLater(() -> {
            sanchezbienvenida.setVisible(false); // Y
            Label marcamarca = new Label("Interrumpido", new FontIcon("fth-slash"));
            marcamarca.getStyleClass().add("burbuja-interrumpida");
            HBox marcafila = new HBox(marcamarca);
            marcafila.setAlignment(Pos.CENTER_LEFT); // K
            sanchezcontenedorChat.getChildren().add(marcafila);
            if (sanchezpedido != null && !sanchezpedido.isBlank()) {
                sancheztxtMensaje.setText(sanchezpedido);
            } // K
        });
    }

    @Override
    public void marcaherramienta(String sanchezdescripcion) {
        Platform.runLater(() -> {
            sanchezbienvenida.setVisible(false); // K
            Label marcachip = new Label(sanchezdescripcion, new FontIcon("fth-tool"));
            marcachip.getStyleClass().add("burbuja-herramienta"); // K
            HBox marcafila = new HBox(marcachip);
            marcafila.setAlignment(Pos.CENTER_LEFT);
            sanchezcontenedorChat.getChildren().add(marcafila);
        }); // Y
    }

    @FXML
    private void marcaalNuevaConversacion() {
        if (sanchezorquestador.marcagetEstado() != MarcaEstado.INACTIVO) {
            return;
        } // Y
        sanchezorquestador.sancheznuevaConversacion(); // K
        sanchezcontenedorChat.getChildren().clear();
        sanchezbienvenida.setVisible(true);
    }

    @FXML
    private void sanchezalElegirSugerencia(javafx.event.ActionEvent sanchezevento) {
        if (sanchezevento.getSource() instanceof Button marcab && sanchezorquestador.marcagetEstado() == MarcaEstado.INACTIVO) { // K
            sanchezorquestador.sanchezturnoPorTexto((String) marcab.getUserData());
        }
    }

    @Override
    public void error(String sanchezmensaje) { // Y
        Platform.runLater(() -> {
            Label marcalbl = new Label(sanchezmensaje);
            marcalbl.setWrapText(true); // Y
            marcalbl.getStyleClass().add("burbuja-error");
            HBox marcafila = new HBox(marcalbl);
            marcafila.setAlignment(Pos.CENTER);
            sanchezcontenedorChat.getChildren().add(marcafila); // Y
            sanchezbienvenida.setVisible(false);
        });
    } // Y

    private void marcamostrarEstado(String marcatexto, String marcaicono) {
        marcalblEstado.setText(marcatexto);
        sanchezicoMicrofono.setIconLiteral(marcaicono);
    } // K

    private void marcaagregarBurbuja(String marcatexto, boolean sanchezdeUsuario) {
        sanchezbienvenida.setVisible(false);
        Label sanchezburbuja = new Label(marcatexto);
        sanchezburbuja.setWrapText(true);
        sanchezburbuja.setMaxWidth(620); // Y
        sanchezburbuja.getStyleClass().addAll("burbuja", sanchezdeUsuario ? "burbuja-usuario" : "burbuja-asistente");
        HBox marcafila = new HBox(sanchezburbuja);
        marcafila.setAlignment(sanchezdeUsuario ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        sanchezcontenedorChat.getChildren().add(marcafila);
    }
}
