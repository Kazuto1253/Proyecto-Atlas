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
import pe.upeu.atlas.asistente.OrquestadorConversacion;
import pe.upeu.atlas.asistente.OrquestadorConversacion.Estado;

/**
 * Vista Asistente: conversacion por voz o por texto.
 * (Version preliminar; en las Fases 1 y 3 pasa a usar OrquestadorAsistente con herramientas.)
 */
@Component
@RequiredArgsConstructor
public class AsistenteController implements OrquestadorConversacion.Oyente {

    private final OrquestadorConversacion orquestador;
    private final pe.upeu.atlas.ui.Navegador navegador;

    /** Aviso para mostrar en la línea de estado cuando termine el turno (texto de voz por confirmar). */
    private volatile String avisoPendiente; // K

    @FXML private Label lblEstado;
    @FXML private Label lblParcial;
    @FXML private ScrollPane scrollChat;
    @FXML private VBox contenedorChat;
    @FXML private VBox bienvenida;
    @FXML private Button btnMicrofono;
    @FXML private Button btnInterrumpir;
    @FXML private FontIcon icoMicrofono;
    @FXML private TextField txtMensaje;
    @FXML private Button btnEnviar;
    @FXML private ToggleButton tglVoz;
    @FXML private FontIcon icoVoz;

    @FXML
    private void initialize() {
        lblParcial.managedProperty().bind(lblParcial.visibleProperty());
        lblParcial.setVisible(false); // K
        btnInterrumpir.managedProperty().bind(btnInterrumpir.visibleProperty());
        btnInterrumpir.setVisible(false);

        // Al crecer el chat, bajar al ultimo mensaje
        contenedorChat.heightProperty().addListener((obs, a, b) -> scrollChat.setVvalue(1.0));

        // F2 activa el microfono mientras la ventana tiene el foco (el atajo global llega en la Fase 7)
        btnMicrofono.sceneProperty().addListener((obs, anterior, escena) -> {
            if (escena != null) {
                escena.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
                    if (e.getCode() == KeyCode.F2) {
                        alPulsarMicrofono();
                        e.consume(); // K
                    } else if (e.getCode() == KeyCode.ESCAPE && orquestador.getEstado() != Estado.INACTIVO) { // K
                        alInterrumpir();
                        e.consume();
                    }
                });
            }
        });

        orquestador.agregarOyente(this);
        orquestador.precargar();
    } // K

    @FXML
    private void alPulsarMicrofono() {
        if (orquestador.getEstado() == Estado.INACTIVO) { // Y
            orquestador.turnoPorVoz();
        } else { // K
            // Escuchando: termina ya. Hablando: calla.
            orquestador.detener();
        }
    } // Y

    @FXML
    private void alModoFlotante() {
        navegador.activarModoFlotante();
    }

    /** Corta lo que Atlas esté haciendo (escuchar, pensar o hablar) sin ejecutar nada más. */
    @FXML
    private void alInterrumpir() { // Y
        orquestador.interrumpir(); // Y
    }

    @FXML
    private void alEnviarTexto() { // Y
        String texto = txtMensaje.getText(); // K
        if (texto == null || texto.isBlank() || orquestador.getEstado() != Estado.INACTIVO) {
            return;
        }
        txtMensaje.clear();
        orquestador.turnoPorTexto(texto); // Y
    }

    @FXML
    private void alCambiarVoz() {
        boolean activa = tglVoz.isSelected(); // Y
        orquestador.setVozActivada(activa);
        icoVoz.setIconLiteral(activa ? "fth-volume-2" : "fth-volume-x"); // K
        if (!activa) {
            orquestador.detener(); // Y
        } // K
    }

    // ===== Oyente (llamado desde el hilo de conversacion) =====

    @Override
    public void estado(Estado estado) {
        Platform.runLater(() -> {
            btnMicrofono.getStyleClass().removeAll("escuchando", "hablando");
            boolean libre = estado == Estado.INACTIVO;
            txtMensaje.setDisable(!libre);
            btnEnviar.setDisable(!libre);
            btnMicrofono.setDisable(estado == Estado.CARGANDO || estado == Estado.PENSANDO);
            btnInterrumpir.setVisible(estado == Estado.ESCUCHANDO || estado == Estado.PENSANDO || estado == Estado.HABLANDO);
            switch (estado) { // Y
                case CARGANDO -> mostrarEstado("Cargando modelo de voz...", "fth-loader");
                case ESCUCHANDO -> {
                    btnMicrofono.getStyleClass().add("escuchando");
                    mostrarEstado("Escuchando... (micrófono: terminar · Esc: cancelar)", "fth-square");
                } // K
                case PENSANDO -> mostrarEstado("Pensando... (Esc o el botón rojo para interrumpir)", "fth-loader");
                case HABLANDO -> {
                    btnMicrofono.getStyleClass().add("hablando"); // Y
                    mostrarEstado("Hablando... (pulsa para callar)", "fth-volume-x"); // K
                } // K
                case INACTIVO -> { // K
                    String aviso = avisoPendiente; // K
                    avisoPendiente = null;
                    if (aviso != null) { // Y
                        mostrarEstado(aviso, "fth-mic");
                        txtMensaje.requestFocus(); // Y
                        txtMensaje.end();
                    } else { // K
                        mostrarEstado("Listo. Pulsa el micrófono o F2 para hablar.", "fth-mic");
                    }
                }
            } // Y
        });
    } // Y

    @Override
    public void textoParcial(String texto) { // Y
        Platform.runLater(() -> { // K
            lblParcial.setText(texto); // Y
            lblParcial.setVisible(!texto.isBlank()); // K
        });
    }

    @Override
    public void mensajeUsuario(String texto) {
        Platform.runLater(() -> agregarBurbuja(texto, true));
    }

    @Override
    public void mensajeAsistente(String texto) { // K
        Platform.runLater(() -> agregarBurbuja(texto, false));
    }

    /** Lo entendido por voz tiene poca confianza: se deja en el cuadro de texto para corregirlo y enviarlo. */
    @Override
    public void confirmarTexto(String texto) {
        avisoPendiente = "No estoy segura de haberte entendido bien. Revisa el texto, corrígelo si hace falta y pulsa Enter.";
        Platform.runLater(() -> txtMensaje.setText(texto)); // Y
    }

    /** El usuario interrumpió: se deja una marca en el chat y su pedido vuelve al cuadro para corregirlo. */
    @Override
    public void interrumpido(String pedido) {
        if (pedido != null && !pedido.isBlank()) { // Y
            avisoPendiente = "Interrumpiste a Atlas. Corrige tu pedido y pulsa Enter para enviarlo.";
        }
        Platform.runLater(() -> {
            bienvenida.setVisible(false); // Y
            Label marca = new Label("Interrumpido", new FontIcon("fth-slash"));
            marca.getStyleClass().add("burbuja-interrumpida");
            HBox fila = new HBox(marca);
            fila.setAlignment(Pos.CENTER_LEFT); // K
            contenedorChat.getChildren().add(fila);
            if (pedido != null && !pedido.isBlank()) {
                txtMensaje.setText(pedido);
            } // K
        });
    }

    @Override
    public void herramienta(String descripcion) {
        Platform.runLater(() -> {
            bienvenida.setVisible(false); // K
            Label chip = new Label(descripcion, new FontIcon("fth-tool"));
            chip.getStyleClass().add("burbuja-herramienta"); // K
            HBox fila = new HBox(chip);
            fila.setAlignment(Pos.CENTER_LEFT);
            contenedorChat.getChildren().add(fila);
        }); // Y
    }

    /** Empieza una conversación nueva: la actual queda guardada en el Historial. */
    @FXML
    private void alNuevaConversacion() {
        if (orquestador.getEstado() != Estado.INACTIVO) {
            return;
        } // Y
        orquestador.nuevaConversacion(); // K
        contenedorChat.getChildren().clear();
        bienvenida.setVisible(true);
    }

    /** Las sugerencias rápidas envían su propio texto como mensaje. */
    @FXML
    private void alElegirSugerencia(javafx.event.ActionEvent evento) {
        if (evento.getSource() instanceof Button b && orquestador.getEstado() == Estado.INACTIVO) { // K
            orquestador.turnoPorTexto((String) b.getUserData());
        }
    }

    @Override
    public void error(String mensaje) { // Y
        Platform.runLater(() -> {
            Label lbl = new Label(mensaje);
            lbl.setWrapText(true); // Y
            lbl.getStyleClass().add("burbuja-error");
            HBox fila = new HBox(lbl);
            fila.setAlignment(Pos.CENTER);
            contenedorChat.getChildren().add(fila); // Y
            bienvenida.setVisible(false);
        });
    } // Y

    private void mostrarEstado(String texto, String icono) {
        lblEstado.setText(texto);
        icoMicrofono.setIconLiteral(icono);
    } // K

    private void agregarBurbuja(String texto, boolean deUsuario) {
        bienvenida.setVisible(false);
        Label burbuja = new Label(texto);
        burbuja.setWrapText(true);
        burbuja.setMaxWidth(620); // Y
        burbuja.getStyleClass().addAll("burbuja", deUsuario ? "burbuja-usuario" : "burbuja-asistente");
        HBox fila = new HBox(burbuja);
        fila.setAlignment(deUsuario ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        contenedorChat.getChildren().add(fila);
    }
}
