package pe.upeu.atlas.docs;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import pe.upeu.atlas.asistente.SanchezOrquestadorConversacion;
import pe.upeu.atlas.asistente.SanchezOrquestadorConversacion.MarcaEstado;
import pe.upeu.atlas.controller.SanchezMainController;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.seguridad.SanchezServicioAutenticacion;
import pe.upeu.atlas.service.SanchezServicioDocumentos;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@EnabledIfSystemProperty(named = "atlas.capturas", matches = "true")
@SpringBootTest(properties = "app.atlas.directorio-base=${user.home}/.atlas")
@ActiveProfiles("test")
class GeneradorCapturasManual {

    private static final double ANCHO = 1200;
    private static final double ALTO = 760;
    private static final double ESCALA = 1.5;
    private static final Color ROJO = Color.web("#e11d48");

    record Marca(String selector, int numero, char lado, int dist, int desp) { }

    private static Marca m(String selector, int n, char lado, int dist) {
        return new Marca(selector, n, lado, dist, 0);
    }

    private static Marca m(String selector, int n, char lado, int dist, int desp) {
        return new Marca(selector, n, lado, dist, desp);
    }

    private double alto = ALTO;

    @Autowired private ApplicationContext contexto;
    @Autowired private SanchezServicioAutenticacion autenticacion;
    @Autowired private SanchezServicioDocumentos documentos;
    @Autowired private SanchezOrquestadorConversacion orquestador;

    private Stage ventana;
    private StackPane envoltorio;
    private Pane capa;
    private Path salida;

    @Test
    void generaTodasLasCapturas() throws Exception {
        salida = Path.of("docs", "capturas");
        Files.createDirectories(salida);
        Platform.startup(() -> { });
        fx(() -> {
            javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
            ventana = new Stage(StageStyle.UNDECORATED);
            capa = new Pane();
            capa.setMouseTransparent(true);
            envoltorio = new StackPane();
            Scene escena = new Scene(envoltorio, ANCHO, ALTO);
            escena.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            ventana.setScene(escena);
            ventana.setX(40);
            ventana.setY(40);
            ventana.show();
            return null;
        });

        autenticacion.sanchezregistrar("Administrador de Atlas", "admin", "Admin123", SanchezRol.ADMINISTRADOR);
        autenticacion.sanchezregistrar("Docente de prueba", "docente", "Docente123", SanchezRol.DOCENTE);
        autenticacion.sanchezregistrar("Estudiante de prueba", "estudiante", "Estudiante123", SanchezRol.ESTUDIANTE);
        autenticacion.sanchezregistrar("Estudiante de ejemplo", "estudiante2", "Ejemplo123", SanchezRol.ESTUDIANTE);

        marcamostrar("/view/login-view.fxml");
        fx(() -> {
            ((javafx.scene.control.TextField) buscar("#marcatxtUsuario")).setText("estudiante");
            ((javafx.scene.control.PasswordField) buscar("#sancheztxtClave")).setText("Estudiante123");
            return null;
        });
        capturar("01-login", 600, List.of(m("#marcatxtUsuario", 1, 'D', 90), m("#sancheztxtClave", 2, 'D', 90),
                m("#marcabtnEntrar", 3, 'D', 90), m("t:¿Eres estudiante y no tienes cuenta? Regístrate", 4, 'I', 90)));

        fx(() -> {
            ((javafx.scene.control.PasswordField) buscar("#sancheztxtClave")).setText("clave-equivocada");
            ((ButtonBase) buscar("#marcabtnEntrar")).fire();
            return null;
        });
        capturar("02-login-error", 2500, List.of(m("#marcalblError", 1, 'D', 90)));

        fx(() -> {
            ((ButtonBase) buscar("t:¿Eres estudiante y no tienes cuenta? Regístrate")).fire();
            ((javafx.scene.control.TextField) buscar("#sancheztxtNombre")).setText("Ana Torres Quispe");
            ((javafx.scene.control.TextField) buscar("#sancheztxtNuevoUsuario")).setText("ana.torres");
            ((javafx.scene.control.PasswordField) buscar("#marcatxtNuevaClave")).setText("secreto1");
            ((javafx.scene.control.PasswordField) buscar("#marcatxtRepetirClave")).setText("secreto1");
            return null;
        });
        capturar("03-registro", 600, List.of(m("#sancheztxtNombre", 1, 'D', 90), m("#sancheztxtNuevoUsuario", 2, 'D', 90),
                m("#marcatxtNuevaClave", 3, 'D', 90), m("#marcatxtRepetirClave", 4, 'D', 90),
                m("#sanchezbtnCrear", 5, 'D', 90), m("t:Ya tengo cuenta: volver", 6, 'I', 90)));

        autenticacion.marcainiciarSesion("admin", "Admin123");
        Path tmp = Files.createTempDirectory("atlas-capturas");
        Path apuntes = Files.writeString(tmp.resolve("Apuntes de Redes.txt"),
                "Una red LAN (Local Area Network) conecta computadoras en un área pequeña, como un laboratorio, "
                        + "una oficina o una casa. Suele usar cables Ethernet o Wi-Fi y ofrece alta velocidad.\n\n"
                        + "Una red WAN (Wide Area Network) conecta redes en grandes distancias, por ejemplo entre ciudades. "
                        + "Internet es la WAN más grande del mundo.\n\n"
                        + "El modelo OSI tiene siete capas: física, enlace de datos, red, transporte, sesión, presentación "
                        + "y aplicación. La capa de transporte se encarga de la entrega confiable de los datos.");
        Path ceviche = Files.writeString(tmp.resolve("Receta de ceviche.txt"),
                "El ceviche peruano se prepara con pescado fresco, jugo de limón, ají limo, cebolla roja, sal y "
                        + "cilantro. Se sirve con camote y choclo. El pescado se marina solo unos minutos.");
        documentos.marcaimportar(apuntes);
        documentos.marcaimportar(ceviche);

        FXMLLoader cargador = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));
        cargador.setControllerFactory(contexto::getBean);
        Parent principal = fx(() -> cargarSin(cargador));
        SanchezMainController main = cargador.getController();
        fx(() -> {
            envoltorio.getChildren().setAll(principal, capa);
            return null;
        });
        Thread.sleep(1500);

        orquestador.marcasetVozActivada(false);
        fx(() -> {
            main.marcamostrar("asistente");
            return null;
        });
        Thread.sleep(2500);
        sanchezenviar("¿Qué hora es?");
        sanchezenviar("¿Cuánto es 15 por 12 más 3?");
        sanchezenviar("Según mis documentos, ¿qué es una red LAN?");
        capturar("07-asistente", 800, List.of(m(".btn-microfono", 1, 'I', 110), m("#sancheztxtMensaje", 2, 'A', 60, -330),
                m("#sanchezbtnEnviar", 3, 'A', 70), m("#marcatglVoz", 4, 'A', 70), m("t:Nueva conversación", 5, 'I', 90),
                m("t:Qué hora es", 6, 'A', 70), m(".burbuja-herramienta", 7, 'D', 90), m("#marcalblEstado", 8, 'D', 100)));
        capturar("00-portada-asistente", 0, List.of());
        orquestador.sancheznuevaConversacion();
        sanchezenviar("Hola, ¿cómo te llamas?");

        pe.upeu.atlas.controller.MarcaAsistenteController chat = contexto.getBean(pe.upeu.atlas.controller.MarcaAsistenteController.class);
        fx(() -> {
            ((ButtonBase) buscar("t:Nueva conversación")).fire();
            return null;
        });
        chat.sanchezmensajeUsuario("Guarda en mi carpeta de documentos un archivo llamado lista de compras con leche, pan y huevos");
        chat.marcaherramienta("Guardó lista de compras.txt");
        chat.sanchezmensajeAsistente("Guardé el archivo «lista de compras.txt» en tu carpeta de documentos.");
        chat.sanchezmensajeUsuario("Elimina el archivo viejo.txt de documentos");
        chat.marcaherramienta("Envió a la papelera viejo.txt");
        chat.sanchezmensajeAsistente("Envié viejo.txt a la papelera de reciclaje; puedes restaurarlo desde allí.");
        capturar("07c-archivos", 900, List.of(m("t:Guardó lista de compras.txt", 1, 'D', 90), m("t:Envió a la papelera viejo.txt", 2, 'D', 90),
                m("t:Modo flotante", 3, 'B', 35)));
        chat.sanchezmensajeUsuario("Dime cuánto es 458 por 37");
        fx(() -> {
            chat.marcaestado(MarcaEstado.PENSANDO);
            return null;
        });
        capturar("07b-interrumpir", 700, List.of(m("#sanchezbtnInterrumpir", 1, 'A', 55), m("#marcalblEstado", 2, 'D', 100)));
        fx(() -> {
            chat.marcaestado(MarcaEstado.INACTIVO);
            return null;
        });

        fx(() -> {
            main.marcamostrar("historial");
            return null;
        });
        Thread.sleep(2500);
        fx(() -> {
            ((ListView<?>) buscar("#sanchezlista")).getSelectionModel().select(1);
            return null;
        });
        capturar("08-historial", 2000, List.of(m("#marcatxtBuscar", 1, 'I', 22), m("#sanchezlista", 2, 'I', 22),
                m("#sanchezlblTitulo", 3, 'D', 70), m("#marcabtnPdf", 4, 'A', 55), m("#sanchezbtnEliminar", 5, 'A', 55)));

        fx(() -> {
            main.marcamostrar("documentos");
            return null;
        });
        Thread.sleep(2000);
        fx(() -> {
            ((javafx.scene.control.TextField) buscar("#sancheztxtConsulta")).setText("¿Cuántas capas tiene el modelo OSI?");
            ((ButtonBase) buscar("#marcabtnBuscar")).fire();
            ((TableView<?>) buscar("#marcatabla")).getSelectionModel().select(0);
            return null;
        });
        capturar("09-documentos", 6000, List.of(m("t:Importar documento", 1, 'B', 30), m("#marcatabla", 2, 'B', 40, 200),
                m("#sanchezbtnEliminar", 3, 'D', 90), m("#sancheztxtConsulta", 4, 'I', 70), m("#marcabtnBuscar", 5, 'A', 45),
                m("#sanchezcontenedorResultados", 6, 'B', 30)));

        fx(() -> {
            main.marcamostrar("reportes");
            return null;
        });
        capturar("10-reportes", 1500, List.of(m("#sanchezbtnActividad", 1, 'B', 20), m("t:Generar informe (PDF)", 2, 'B', 40),
                m("t:Exportar usuarios (Excel)", 3, 'B', 55)));

        fx(() -> {
            main.marcamostrar("diagnostico");
            return null;
        });
        capturar("11-diagnostico", 9000, List.of(m("#sanchezbtnVerificar", 1, 'I', 90), m("#sanchezlblResumen", 2, 'D', 90),
                m("^t:Sistema", 3, 'B', 14)), 1000, 0);

        fx(() -> {
            main.marcamostrar("usuarios");
            return null;
        });
        Thread.sleep(2000);
        fx(() -> {
            ((TableView<?>) buscar("#marcatabla")).getSelectionModel().select(1);
            return null;
        });
        capturar("12-usuarios", 800, List.of(m("t:Nuevo usuario", 1, 'I', 90), m("#marcatabla", 2, 'A', 35, -100),
                m("#marcabtnActivar", 3, 'A', 45), m("#sanchezbtnRol", 4, 'A', 45), m("#sanchezbtnClave", 5, 'A', 45)));

        fx(() -> {
            main.marcamostrar("inicio");
            return null;
        });
        List<Marca> inicio = List.of(m(".chip-estado", 1, 'I', 90), m("^^^t:Procesador", 2, 'A', 55),
                m("#sanchezgrafico", 3, 'B', 50), m("#marcalistaActividad", 4, 'I', 90), m("^t:Accesos rápidos", 5, 'B', 25),
                m("^t:Tu equipo", 6, 'B', 25));
        capturar("05-inicio", 14000, inicio, 1000, 0);
        capturar("00-portada-inicio", 0, List.of(), 1000, 0);
        vaciarContenido();
        capturar("04-menu", 300, List.of(m("t:Inicio", 1, 'D', 70), m("t:Asistente", 2, 'D', 70), m("t:Historial", 3, 'D', 70),
                m("t:Documentos", 4, 'D', 70), m("t:Reportes", 5, 'D', 70), m("t:Diagnóstico", 6, 'D', 70),
                m("t:Usuarios", 7, 'D', 70), m("#marcachipUsuario", 8, 'D', 70), m("#sanchezbtnFlotante", 9, 'D', 70),
                m("t:Cerrar sesión", 10, 'D', 70), m("#marcabtnContraer", 11, 'D', 70)), ALTO, 400);
        fx(() -> {
            ((ButtonBase) buscar("#marcabtnContraer")).fire();
            return null;
        });
        capturar("06-menu-contraido", 900, List.of(m("#marcabtnContraer", 1, 'D', 50)), ALTO, 200);
        fx(() -> {
            ((ButtonBase) buscar("#marcabtnContraer")).fire();
            return null;
        });

        autenticacion.marcacerrarSesion();
        autenticacion.marcainiciarSesion("estudiante", "Estudiante123");
        FXMLLoader cargador2 = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));
        cargador2.setControllerFactory(contexto::getBean);
        Parent principal2 = fx(() -> cargarSin(cargador2));
        fx(() -> {
            envoltorio.getChildren().setAll(principal2, capa);
            return null;
        });
        vaciarContenido();
        capturar("13-estudiante", 800, List.of(m("t:Inicio", 1, 'D', 70), m("t:Asistente", 2, 'D', 70),
                m("t:Historial", 3, 'D', 70), m("t:Documentos", 4, 'D', 70), m("t:Reportes", 5, 'D', 70),
                m("#marcachipUsuario", 6, 'D', 70)), ALTO, 400);
        autenticacion.marcacerrarSesion();
        autenticacion.marcainiciarSesion("admin", "Admin123");
        FXMLLoader cargadorMini = new FXMLLoader(getClass().getResource("/view/mini-view.fxml"));
        cargadorMini.setControllerFactory(contexto::getBean);
        Parent mini = fx(() -> cargarSin(cargadorMini));
        pe.upeu.atlas.controller.SanchezMiniController mc = cargadorMini.getController();
        ((javafx.scene.layout.Region) mini).setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
        StackPane fondoMini = new StackPane(mini);
        fondoMini.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        fondoMini.setStyle("-fx-background-color: linear-gradient(to bottom right, #5f6f8f, #cfd8e6); -fx-padding: 24;");
        fx(() -> {
            envoltorio.getChildren().setAll(fondoMini, capa);
            return null;
        });
        mc.sanchezmensajeUsuario("Toma una captura de pantalla");
        mc.marcaherramienta("Tomó una captura de pantalla");
        mc.sanchezmensajeAsistente("Listo, guardé la captura en la carpeta de capturas de Atlas.");
        mc.marcaestado(MarcaEstado.INACTIVO);
        capturar("07d-mini", 700, List.of(m("#btnExpandir", 1, 'D', 32), m("#sanchezscrollRespuesta", 2, 'D', 32),
                m("#marcabtnMicrofono", 3, 'B', 26), m("#sancheztxtMensaje", 4, 'B', 26)), 340, 480);
        mc.sanchezmensajeUsuario("Dime cuánto es 458 por 37");
        mc.marcaestado(MarcaEstado.PENSANDO);
        capturar("07e-mini-pensando", 700, List.of(m("#sanchezbtnInterrumpir", 1, 'B', 26), m("#marcalblEstado", 2, 'A', 22, 60)), 340, 480);
        mc.marcaestado(MarcaEstado.INACTIVO);

        autenticacion.marcacerrarSesion();
        fx(() -> {
            ventana.close();
            return null;
        });
        System.out.println("CAPTURAS -> " + salida.toAbsolutePath());
    }

    private void vaciarContenido() throws Exception {
        fx(() -> {
            ((Pane) buscar("#sanchezcontenido")).getChildren().clear();
            return null;
        });
    }

    private static Parent cargarSin(FXMLLoader l) {
        try {
            return l.load();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void marcamostrar(String fxml) throws Exception {
        FXMLLoader c = new FXMLLoader(getClass().getResource(fxml));
        c.setControllerFactory(contexto::getBean);
        Parent nodo = fx(() -> cargarSin(c));
        fx(() -> {
            envoltorio.getChildren().setAll(nodo, capa);
            return null;
        });
    }

    private void sanchezenviar(String texto) throws Exception {
        boolean aceptado = false;
        for (int intento = 0; intento < 5 && !aceptado; intento++) {
            long libre = System.currentTimeMillis() + 60_000;
            while (orquestador.marcagetEstado() != SanchezOrquestadorConversacion.MarcaEstado.INACTIVO && System.currentTimeMillis() < libre) {
                Thread.sleep(300);
            }
            orquestador.sanchezturnoPorTexto(texto);
            long limite0 = System.currentTimeMillis() + 4000;
            while (orquestador.marcagetEstado() == SanchezOrquestadorConversacion.MarcaEstado.INACTIVO && System.currentTimeMillis() < limite0) {
                Thread.sleep(100);
            }
            aceptado = orquestador.marcagetEstado() != SanchezOrquestadorConversacion.MarcaEstado.INACTIVO;
        }
        long limite = System.currentTimeMillis() + 240_000;
        while (orquestador.marcagetEstado() != SanchezOrquestadorConversacion.MarcaEstado.INACTIVO && System.currentTimeMillis() < limite) {
            Thread.sleep(300);
        }
        Thread.sleep(700);
    }

    private void capturar(String nombre, long esperaMs, List<Marca> marcas) throws Exception {
        capturar(nombre, esperaMs, marcas, ALTO, 0);
    }

    private void capturar(String nombre, long esperaMs, List<Marca> marcas, double altoVentana, double recorteAncho) throws Exception {
        if (altoVentana != alto) {
            fx(() -> {
                ventana.setHeight(altoVentana);
                return null;
            });
            alto = altoVentana;
            Thread.sleep(800);
        }
        Thread.sleep(esperaMs);
        BufferedImage completa = fx(() -> {
            capa.getChildren().clear();
            envoltorio.applyCss();
            envoltorio.layout();
            for (Marca marca : marcas) {
                Node nodo = buscar(marca.selector());
                if (nodo == null) {
                    throw new IllegalStateException("No encontré " + marca.selector() + " en " + nombre);
                }
                dibujar(nodo, marca);
            }
            SnapshotParameters p = new SnapshotParameters();
            p.setTransform(Transform.scale(ESCALA, ESCALA));
            WritableImage wi = envoltorio.snapshot(p, new WritableImage((int) (ANCHO * ESCALA), (int) (alto * ESCALA)));
            capa.getChildren().clear();
            return aBuffered(wi);
        });
        BufferedImage imagen = recorteAncho > 0
                ? completa.getSubimage(0, 0, (int) (recorteAncho * ESCALA), completa.getHeight()) : completa;
        if (altoVentana != ALTO) {
            fx(() -> {
                ventana.setHeight(ALTO);
                return null;
            });
            alto = ALTO;
            Thread.sleep(500);
        }
        ImageIO.write(imagen, "png", salida.resolve(nombre + ".png").toFile());
        System.out.println("Captura " + nombre + " (" + imagen.getWidth() + "x" + imagen.getHeight() + ")");
    }

    private Node buscar(String selector) {
        int subir = 0;
        while (selector.startsWith("^")) {
            subir++;
            selector = selector.substring(1);
        }
        Node nodo;
        if (selector.startsWith("t:")) {
            nodo = porTexto(envoltorio, selector.substring(2));
        } else {
            nodo = envoltorio.lookup(selector);
        }
        for (int i = 0; nodo != null && i < subir; i++) {
            nodo = nodo.getParent();
        }
        return nodo;
    }

    private static Node porTexto(Node raiz, String texto) {
        Deque<Node> pila = new ArrayDeque<>();
        pila.push(raiz);
        while (!pila.isEmpty()) {
            Node n = pila.pop();
            if (n.isVisible() && n.getScene() != null) {
                if (n instanceof Labeled l && texto.equals(l.getText())) {
                    return n;
                }
                if (n instanceof TextInputControl t && texto.equals(t.getPromptText())) {
                    return n;
                }
            }
            if (n instanceof Parent p) {
                List<Node> hijos = p.getChildrenUnmodifiable();
                for (int i = hijos.size() - 1; i >= 0; i--) {
                    pila.push(hijos.get(i));
                }
            }
        }
        return null;
    }

    private void dibujar(Node nodo, Marca marca) {
        Bounds b = nodo.localToScene(nodo.getBoundsInLocal());
        double pad = 4;
        double x0 = b.getMinX() - pad, y0 = b.getMinY() - pad, x1 = b.getMaxX() + pad, y1 = b.getMaxY() + pad;
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;

        Rectangle marco = new Rectangle(x0, y0, x1 - x0, y1 - y0);
        marco.setArcWidth(12);
        marco.setArcHeight(12);
        marco.setFill(Color.TRANSPARENT);
        marco.setStroke(ROJO);
        marco.setStrokeWidth(3);

        double bx, by, ex, ey;
        int d = marca.desp();
        switch (marca.lado()) {
            case 'I' -> { bx = x0 - marca.dist(); by = cy + d; ex = x0; ey = by; }
            case 'D' -> { bx = x1 + marca.dist(); by = cy + d; ex = x1; ey = by; }
            case 'A' -> { bx = cx + d; by = y0 - marca.dist(); ex = bx; ey = y0; }
            default -> { bx = cx + d; by = y1 + marca.dist(); ex = bx; ey = y1; }
        }
        double radio = 15;
        bx = Math.max(radio + 4, Math.min(ANCHO - radio - 4, bx));
        by = Math.max(radio + 4, Math.min(alto - radio - 4, by));

        double dx = ex - bx, dy = ey - by;
        double largo = Math.max(1, Math.hypot(dx, dy));
        double ux = dx / largo, uy = dy / largo;
        double sx = bx + ux * radio, sy = by + uy * radio;

        Line linea = new Line(sx, sy, ex - ux * 10, ey - uy * 10);
        linea.setStroke(ROJO);
        linea.setStrokeWidth(3);
        Polygon punta = new Polygon(ex, ey,
                ex - ux * 16 - uy * 8, ey - uy * 16 + ux * 8,
                ex - ux * 16 + uy * 8, ey - uy * 16 - ux * 8);
        punta.setFill(ROJO);

        Circle insignia = new Circle(bx, by, radio, ROJO);
        insignia.setStroke(Color.WHITE);
        insignia.setStrokeWidth(2);
        insignia.setEffect(new DropShadow(4, Color.rgb(0, 0, 0, 0.35)));
        Text numero = new Text(String.valueOf(marca.numero()));
        numero.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        numero.setFill(Color.WHITE);
        numero.setX(bx - numero.getLayoutBounds().getWidth() / 2);
        numero.setY(by + numero.getLayoutBounds().getHeight() / 4 + 1);

        capa.getChildren().addAll(marco, linea, punta, insignia, numero);
    }

    private static BufferedImage aBuffered(WritableImage wi) {
        int w = (int) wi.getWidth(), h = (int) wi.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        PixelReader lector = wi.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                bi.setRGB(x, y, lector.getArgb(x, y));
            }
        }
        return bi;
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
