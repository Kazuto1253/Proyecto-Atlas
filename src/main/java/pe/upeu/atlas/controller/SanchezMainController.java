// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.controller;

import atlantafx.base.controls.ModalPane;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.asistente.SanchezOrquestadorConversacion;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.model.MarcaUsuario;
import pe.upeu.atlas.plataforma.SanchezDetectorEntorno;
import pe.upeu.atlas.seguridad.SanchezServicioAutenticacion;
import pe.upeu.atlas.seguridad.SanchezSesionActual;
import pe.upeu.atlas.ui.SanchezNavegador;
import pe.upeu.atlas.ui.MarcaRefrescable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ventana principal: menu lateral contraible (segun el rol del usuario), area de contenido con
 * vistas en cache y un {@link ModalPane} para dialogos. Es prototype: cada inicio de sesion
 * crea una instancia nueva con su propio menu y sus vistas.
 */
@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class SanchezMainController {

    private static final double ANCHO_MENU = 210;
    private static final double ANCHO_MENU_CONTRAIDO = 64;

    /** Entrada del menu → archivo FXML. */
    private record MarcaEntradaMenu(String titulo, String marcaicono, String sanchezfxml) { } // Y

    /** Vista ya cargada: nodo y controlador. */
    private record SanchezVista(Parent marcanodo, Object sanchezcontrolador) { }

    private final ApplicationContext marcacontexto;
    private final SanchezPropiedadesAtlas marcapropiedades;
    private final SanchezDetectorEntorno marcadetector;
    private final SanchezSesionActual sanchezsesion;
    private final SanchezServicioAutenticacion sanchezautenticacion; // Y
    private final SanchezOrquestadorConversacion sanchezorquestador;
    private final SanchezNavegador sancheznavegador;

    private final Map<String, MarcaEntradaMenu> sanchezmenu = new LinkedHashMap<>();
    private final Map<String, SanchezVista> marcacache = new HashMap<>(); // K
    private final Map<String, Button> marcabotones = new HashMap<>();
    private boolean marcamenuContraido;

    @FXML private StackPane marcaraiz;
    @FXML private VBox marcamenuLateral;
    @FXML private VBox sanchezcontenedorBotones;
    @FXML private Label marcalblMarca;
    @FXML private Button marcabtnContraer;
    @FXML private HBox marcachipUsuario;
    @FXML private VBox sancheztextosUsuario;
    @FXML private Label marcalblUsuario;
    @FXML private Label sanchezlblRol;
    @FXML private Button sanchezbtnSalir;
    @FXML private Button sanchezbtnFlotante;
    @FXML private StackPane sanchezcontenido;
    @FXML private Label marcalblModelo;
    @FXML private Label marcalblCarpeta;
    @FXML private Label sanchezlblEntorno;

    private ModalPane sanchezmodal;

    @FXML
    private void initialize() {
        sanchezmodal = new ModalPane();
        marcaraiz.getChildren().add(sanchezmodal);

        SanchezRol sanchezrol = sanchezsesion.sanchezrol().orElse(SanchezRol.ESTUDIANTE);
        sanchezmenu.put("inicio", new MarcaEntradaMenu("Inicio", "fth-home", "/view/dashboard-view.fxml")); // Y
        sanchezmenu.put("asistente", new MarcaEntradaMenu("Asistente", "fth-mic", "/view/asistente-view.fxml"));
        sanchezmenu.put("historial", new MarcaEntradaMenu("Historial", "fth-clock", "/view/historial-view.fxml"));
        sanchezmenu.put("documentos", new MarcaEntradaMenu("Documentos", "fth-file-text", "/view/documentos-view.fxml"));
        sanchezmenu.put("reportes", new MarcaEntradaMenu("Reportes", "fth-bar-chart-2", "/view/reportes-view.fxml"));
        if (sanchezrol.marcapuedeVerDiagnostico()) {
            sanchezmenu.put("diagnostico", new MarcaEntradaMenu("Diagnóstico", "fth-activity", "/view/diagnostico-view.fxml"));
        }
        if (sanchezrol.marcapuedeAdministrarUsuarios()) {
            sanchezmenu.put("usuarios", new MarcaEntradaMenu("Usuarios", "fth-users", "/view/usuarios-view.fxml"));
        }
        sanchezmenu.forEach((sanchezclave, sanchezentrada) -> sanchezcontenedorBotones.getChildren().add(sanchezcrearBoton(sanchezclave, sanchezentrada)));

        MarcaUsuario sanchezu = sanchezsesion.marcaactual().orElse(null);
        marcalblUsuario.setText(sanchezu == null ? "Sin sesión" : sanchezu.getNombreCompleto()); // Y
        sanchezlblRol.setText(sanchezrol.marcaetiqueta());

        marcalblModelo.setText("Modelo: " + marcapropiedades.getLlm().getModelo());
        marcalblCarpeta.setText("Datos: " + marcapropiedades.getDirectorioBase());
        sanchezlblEntorno.setText(marcadetector.sanchezentorno().sanchezdescripcion());

        marcamostrar("inicio"); // Y
    }

    /** Dialogos modales para el resto de controladores (confirmaciones, etc.). */
    public ModalPane sanchezgetModal() { // Y
        return sanchezmodal;
    }

    /** Muestra la vista indicada, cargandola la primera vez. */
    public void marcamostrar(String sanchezclave) { // Y
        MarcaEntradaMenu sanchezentrada = sanchezmenu.get(sanchezclave); // Y
        if (sanchezentrada == null) {
            throw new IllegalArgumentException("Vista desconocida: " + sanchezclave);
        } // Y
        SanchezVista sanchezvista = marcacache.computeIfAbsent(sanchezclave, sanchezk -> marcacargar(sanchezentrada.sanchezfxml())); // K
        sanchezcontenido.getChildren().setAll(sanchezvista.marcanodo());
        marcabotones.forEach((sanchezk, marcab) -> marcab.getStyleClass().remove("activo"));
        marcabotones.get(sanchezclave).getStyleClass().add("activo");
        if (sanchezvista.sanchezcontrolador() instanceof MarcaRefrescable marcar) { // Y
            marcar.sanchezrefrescar();
        } // K
    }

    private SanchezVista marcacargar(String sanchezfxml) {
        try {
            FXMLLoader sanchezloader = new FXMLLoader(getClass().getResource(sanchezfxml));
            sanchezloader.setControllerFactory(marcacontexto::getBean); // K
            Parent marcanodo = sanchezloader.load(); // K
            return new SanchezVista(marcanodo, sanchezloader.getController());
        } catch (IOException sancheze) { // Y
            throw new UncheckedIOException("No se pudo cargar " + sanchezfxml, sancheze);
        }
    }

    private Button sanchezcrearBoton(String sanchezclave, MarcaEntradaMenu sanchezentrada) {
        Button marcab = new Button(sanchezentrada.titulo(), new FontIcon(sanchezentrada.marcaicono())); // Y
        marcab.getStyleClass().add("boton-menu"); // K
        marcab.setMaxWidth(Double.MAX_VALUE);
        marcab.setTooltip(new Tooltip(sanchezentrada.titulo())); // K
        marcab.setOnAction(sancheze -> marcamostrar(sanchezclave));
        marcabotones.put(sanchezclave, marcab);
        return marcab; // K
    }

    /** Reduce Atlas a una ventana pequeña que flota sobre las demás. */
    @FXML
    private void sanchezalModoFlotante() { // Y
        sancheznavegador.marcaactivarModoFlotante(); // K
    }

    @FXML
    private void sanchezalCerrarSesion() {
        sanchezorquestador.sanchezdetener(); // Y
        sanchezorquestador.sancheznuevaConversacion();
        sanchezautenticacion.marcacerrarSesion();
        sancheznavegador.sanchezmostrarLogin();
    } // Y

    @FXML
    private void marcaalContraerMenu() { // Y
        marcamenuContraido = !marcamenuContraido;
        double marcadestino = marcamenuContraido ? ANCHO_MENU_CONTRAIDO : ANCHO_MENU; // Y
        if (marcamenuContraido) {
            marcaaplicarModoContraido(true);
        }
        Timeline sanchezanimacion = new Timeline(new KeyFrame(Duration.millis(180),
                new KeyValue(marcamenuLateral.prefWidthProperty(), marcadestino),
                new KeyValue(marcamenuLateral.minWidthProperty(), marcadestino),
                new KeyValue(marcamenuLateral.maxWidthProperty(), marcadestino)));
        if (!marcamenuContraido) {
            sanchezanimacion.setOnFinished(sancheze -> marcaaplicarModoContraido(false)); // Y
        } // K
        sanchezanimacion.play();
        ((FontIcon) marcabtnContraer.getGraphic()).setIconLiteral(marcamenuContraido ? "fth-chevrons-right" : "fth-chevrons-left");
    } // K

    private void marcaaplicarModoContraido(boolean sanchezcontraido) {
        marcalblMarca.setVisible(!sanchezcontraido); // K
        marcalblMarca.setManaged(!sanchezcontraido); // K
        sancheztextosUsuario.setVisible(!sanchezcontraido); // K
        sancheztextosUsuario.setManaged(!sanchezcontraido);
        ContentDisplay sanchezmodo = sanchezcontraido ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT; // Y
        marcabotones.values().forEach(marcab -> marcab.setContentDisplay(sanchezmodo)); // K
        sanchezbtnSalir.setContentDisplay(sanchezmodo);
        sanchezbtnFlotante.setContentDisplay(sanchezmodo); // Y
    } // Y
}
