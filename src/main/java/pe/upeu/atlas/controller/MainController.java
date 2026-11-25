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
import pe.upeu.atlas.asistente.OrquestadorConversacion;
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.model.Usuario;
import pe.upeu.atlas.plataforma.DetectorEntorno;
import pe.upeu.atlas.seguridad.ServicioAutenticacion;
import pe.upeu.atlas.seguridad.SesionActual;
import pe.upeu.atlas.ui.Navegador;
import pe.upeu.atlas.ui.Refrescable;

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
public class MainController {

    private static final double ANCHO_MENU = 210;
    private static final double ANCHO_MENU_CONTRAIDO = 64;

    /** Entrada del menu → archivo FXML. */
    private record EntradaMenu(String titulo, String icono, String fxml) { } // Y

    /** Vista ya cargada: nodo y controlador. */
    private record Vista(Parent nodo, Object controlador) { }

    private final ApplicationContext contexto;
    private final PropiedadesAtlas propiedades;
    private final DetectorEntorno detector;
    private final SesionActual sesion;
    private final ServicioAutenticacion autenticacion; // Y
    private final OrquestadorConversacion orquestador;
    private final Navegador navegador;

    private final Map<String, EntradaMenu> menu = new LinkedHashMap<>();
    private final Map<String, Vista> cache = new HashMap<>(); // K
    private final Map<String, Button> botones = new HashMap<>();
    private boolean menuContraido;

    @FXML private StackPane raiz;
    @FXML private VBox menuLateral;
    @FXML private VBox contenedorBotones;
    @FXML private Label lblMarca;
    @FXML private Button btnContraer;
    @FXML private HBox chipUsuario;
    @FXML private VBox textosUsuario;
    @FXML private Label lblUsuario;
    @FXML private Label lblRol;
    @FXML private Button btnSalir;
    @FXML private Button btnFlotante;
    @FXML private StackPane contenido;
    @FXML private Label lblModelo;
    @FXML private Label lblCarpeta;
    @FXML private Label lblEntorno;

    private ModalPane modal;

    @FXML
    private void initialize() {
        modal = new ModalPane();
        raiz.getChildren().add(modal);

        Rol rol = sesion.rol().orElse(Rol.ESTUDIANTE);
        menu.put("inicio", new EntradaMenu("Inicio", "fth-home", "/view/dashboard-view.fxml")); // Y
        menu.put("asistente", new EntradaMenu("Asistente", "fth-mic", "/view/asistente-view.fxml"));
        menu.put("historial", new EntradaMenu("Historial", "fth-clock", "/view/historial-view.fxml"));
        menu.put("documentos", new EntradaMenu("Documentos", "fth-file-text", "/view/documentos-view.fxml"));
        menu.put("reportes", new EntradaMenu("Reportes", "fth-bar-chart-2", "/view/reportes-view.fxml"));
        if (rol.puedeVerDiagnostico()) {
            menu.put("diagnostico", new EntradaMenu("Diagnóstico", "fth-activity", "/view/diagnostico-view.fxml"));
        }
        if (rol.puedeAdministrarUsuarios()) {
            menu.put("usuarios", new EntradaMenu("Usuarios", "fth-users", "/view/usuarios-view.fxml"));
        }
        menu.forEach((clave, entrada) -> contenedorBotones.getChildren().add(crearBoton(clave, entrada)));

        Usuario u = sesion.actual().orElse(null);
        lblUsuario.setText(u == null ? "Sin sesión" : u.getNombreCompleto()); // Y
        lblRol.setText(rol.etiqueta());

        lblModelo.setText("Modelo: " + propiedades.getLlm().getModelo());
        lblCarpeta.setText("Datos: " + propiedades.getDirectorioBase());
        lblEntorno.setText(detector.entorno().descripcion());

        mostrar("inicio"); // Y
    }

    /** Dialogos modales para el resto de controladores (confirmaciones, etc.). */
    public ModalPane getModal() { // Y
        return modal;
    }

    /** Muestra la vista indicada, cargandola la primera vez. */
    public void mostrar(String clave) { // Y
        EntradaMenu entrada = menu.get(clave); // Y
        if (entrada == null) {
            throw new IllegalArgumentException("Vista desconocida: " + clave);
        } // Y
        Vista vista = cache.computeIfAbsent(clave, k -> cargar(entrada.fxml())); // K
        contenido.getChildren().setAll(vista.nodo());
        botones.forEach((k, b) -> b.getStyleClass().remove("activo"));
        botones.get(clave).getStyleClass().add("activo");
        if (vista.controlador() instanceof Refrescable r) { // Y
            r.refrescar();
        } // K
    }

    private Vista cargar(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            loader.setControllerFactory(contexto::getBean); // K
            Parent nodo = loader.load(); // K
            return new Vista(nodo, loader.getController());
        } catch (IOException e) { // Y
            throw new UncheckedIOException("No se pudo cargar " + fxml, e);
        }
    }

    private Button crearBoton(String clave, EntradaMenu entrada) {
        Button b = new Button(entrada.titulo(), new FontIcon(entrada.icono())); // Y
        b.getStyleClass().add("boton-menu"); // K
        b.setMaxWidth(Double.MAX_VALUE);
        b.setTooltip(new Tooltip(entrada.titulo())); // K
        b.setOnAction(e -> mostrar(clave));
        botones.put(clave, b);
        return b; // K
    }

    /** Reduce Atlas a una ventana pequeña que flota sobre las demás. */
    @FXML
    private void alModoFlotante() { // Y
        navegador.activarModoFlotante(); // K
    }

    @FXML
    private void alCerrarSesion() {
        orquestador.detener(); // Y
        orquestador.nuevaConversacion();
        autenticacion.cerrarSesion();
        navegador.mostrarLogin();
    } // Y

    @FXML
    private void alContraerMenu() { // Y
        menuContraido = !menuContraido;
        double destino = menuContraido ? ANCHO_MENU_CONTRAIDO : ANCHO_MENU; // Y
        if (menuContraido) {
            aplicarModoContraido(true);
        }
        Timeline animacion = new Timeline(new KeyFrame(Duration.millis(180),
                new KeyValue(menuLateral.prefWidthProperty(), destino),
                new KeyValue(menuLateral.minWidthProperty(), destino),
                new KeyValue(menuLateral.maxWidthProperty(), destino)));
        if (!menuContraido) {
            animacion.setOnFinished(e -> aplicarModoContraido(false)); // Y
        } // K
        animacion.play();
        ((FontIcon) btnContraer.getGraphic()).setIconLiteral(menuContraido ? "fth-chevrons-right" : "fth-chevrons-left");
    } // K

    private void aplicarModoContraido(boolean contraido) {
        lblMarca.setVisible(!contraido); // K
        lblMarca.setManaged(!contraido); // K
        textosUsuario.setVisible(!contraido); // K
        textosUsuario.setManaged(!contraido);
        ContentDisplay modo = contraido ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT; // Y
        botones.values().forEach(b -> b.setContentDisplay(modo)); // K
        btnSalir.setContentDisplay(modo);
        btnFlotante.setContentDisplay(modo); // Y
    } // Y
}
