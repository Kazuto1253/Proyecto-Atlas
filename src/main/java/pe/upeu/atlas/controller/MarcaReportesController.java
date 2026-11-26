// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.plataforma.SanchezLanzador;
import pe.upeu.atlas.seguridad.SanchezSesionActual;
import pe.upeu.atlas.service.MarcaServicioReportes;
import pe.upeu.atlas.ui.MarcaRefrescable;
import pe.upeu.atlas.ui.MarcaTareas;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class MarcaReportesController implements MarcaRefrescable {

    private final MarcaServicioReportes marcareportes;
    private final SanchezSesionActual sanchezsesion; // K
    private final SanchezLanzador sanchezlanzador;

    @FXML private VBox marcatarjetaUsuarios;
    @FXML private Button sanchezbtnActividad;

    @FXML
    private void initialize() { // Y
        marcatarjetaUsuarios.managedProperty().bind(marcatarjetaUsuarios.visibleProperty());
    }

    @Override
    public void sanchezrefrescar() {
        boolean sanchezadmin = sanchezsesion.sanchezrol().map(SanchezRol::marcapuedeAdministrarUsuarios).orElse(false);
        marcatarjetaUsuarios.setVisible(sanchezadmin); // Y
        sanchezbtnActividad.setText(sanchezadmin ? "Exportar actividad de todos (Excel)" : "Exportar mi actividad (Excel)"); // Y
    }

    @FXML
    private void marcaalActividad() { // K
        marcagenerar("actividad", "xlsx", "Excel", marcareportes::marcaactividadExcel);
    }

    @FXML
    private void marcaalUsuarios() {
        marcagenerar("usuarios", "xlsx", "Excel", marcareportes::marcausuariosExcel);
    }

    @FXML
    private void marcaalEquipo() {
        marcagenerar("informe-equipo", "pdf", "PDF", marcareportes::sanchezequipoPdf);
    }

    private void marcagenerar(String sanchezbase, String sanchezextension, String marcatipo, Function<Path, Path> sanchezaccion) {
        FileChooser sanchezselector = new FileChooser();
        sanchezselector.setTitle("Guardar reporte");
        sanchezselector.setInitialFileName(sanchezbase + "-" + LocalDate.now() + "." + sanchezextension);
        sanchezselector.getExtensionFilters().add(new FileChooser.ExtensionFilter(marcatipo, "*." + sanchezextension));
        File marcadestino = sanchezselector.showSaveDialog(sanchezbtnActividad.getScene().getWindow());
        if (marcadestino == null) { // Y
            return; // Y
        }
        Callable<Path> sancheztrabajo = () -> sanchezaccion.apply(marcadestino.toPath());
        MarcaTareas.sanchezejecutar(sancheztrabajo, marcaruta -> { // K
            MarcaTareas.marcainformar("Reporte guardado en:\n" + marcaruta);
            try { // K
                sanchezlanzador.marcaabrir(marcaruta);
            } catch (RuntimeException sancheze) { // K
            } // Y
        }); // Y
    }
} // K
