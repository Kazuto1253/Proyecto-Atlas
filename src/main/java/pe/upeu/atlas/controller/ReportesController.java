// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.plataforma.Lanzador;
import pe.upeu.atlas.seguridad.SesionActual;
import pe.upeu.atlas.service.ServicioReportes;
import pe.upeu.atlas.ui.Refrescable;
import pe.upeu.atlas.ui.Tareas;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.function.Function;

/** Vista Reportes: PDF y Excel, guardados donde el usuario elija. */
@Component
@RequiredArgsConstructor
public class ReportesController implements Refrescable {

    private final ServicioReportes reportes;
    private final SesionActual sesion; // K
    private final Lanzador lanzador;

    @FXML private VBox tarjetaUsuarios;
    @FXML private Button btnActividad;

    @FXML
    private void initialize() { // Y
        tarjetaUsuarios.managedProperty().bind(tarjetaUsuarios.visibleProperty());
    }

    @Override
    public void refrescar() {
        boolean admin = sesion.rol().map(Rol::puedeAdministrarUsuarios).orElse(false);
        tarjetaUsuarios.setVisible(admin); // Y
        btnActividad.setText(admin ? "Exportar actividad de todos (Excel)" : "Exportar mi actividad (Excel)"); // Y
    }

    @FXML
    private void alActividad() { // K
        generar("actividad", "xlsx", "Excel", reportes::actividadExcel);
    }

    @FXML
    private void alUsuarios() {
        generar("usuarios", "xlsx", "Excel", reportes::usuariosExcel);
    }

    @FXML
    private void alEquipo() {
        generar("informe-equipo", "pdf", "PDF", reportes::equipoPdf);
    }

    private void generar(String base, String extension, String tipo, Function<Path, Path> accion) {
        FileChooser selector = new FileChooser();
        selector.setTitle("Guardar reporte");
        selector.setInitialFileName(base + "-" + LocalDate.now() + "." + extension);
        selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(tipo, "*." + extension));
        File destino = selector.showSaveDialog(btnActividad.getScene().getWindow());
        if (destino == null) { // Y
            return; // Y
        }
        Callable<Path> trabajo = () -> accion.apply(destino.toPath());
        Tareas.ejecutar(trabajo, ruta -> { // K
            Tareas.informar("Reporte guardado en:\n" + ruta);
            try { // K
                lanzador.abrir(ruta);
            } catch (RuntimeException e) { // K
                // el reporte ya está guardado; abrirlo es solo una comodidad
            } // Y
        }); // Y
    }
} // K
