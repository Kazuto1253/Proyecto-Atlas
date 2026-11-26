// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.model.MarcaConversacion;
import pe.upeu.atlas.model.SanchezMensaje;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.seguridad.SanchezServicioAutenticacion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ServicioHistorialYReportesTest {

    @Autowired private SanchezServicioHistorial historial;
    @Autowired private MarcaServicioReportes reportes;
    @Autowired private SanchezServicioAutenticacion autenticacion;

    @TempDir Path carpeta;

    private String entrar(String base, SanchezRol rol) {
        String id = base + UUID.randomUUID().toString().substring(0, 8);
        autenticacion.sanchezregistrar("Usuario " + base, id, "secreto1", rol);
        autenticacion.marcainiciarSesion(id, "secreto1");
        historial.marcaterminarConversacion();
        return id;
    }

    @Test
    void guardaConversacionesBuscaYElimina() {
        entrar("hist", SanchezRol.ESTUDIANTE);
        historial.sanchezregistrar(true, "¿Qué es el protocolo TCP?\nExplícame con calma");
        historial.sanchezregistrar(false, "TCP es un protocolo orientado a conexión.");
        historial.marcaterminarConversacion();
        historial.sanchezregistrar(true, "Dame una receta de ceviche");
        historial.sanchezregistrar(false, "Pescado fresco, limón y ají.");

        List<MarcaConversacion> todas = historial.sanchezlistar(null);
        assertThat(todas).hasSize(2);
        assertThat(todas.get(0).getTitulo()).isEqualTo("Dame una receta de ceviche");
        assertThat(todas.get(1).getTitulo()).isEqualTo("¿Qué es el protocolo TCP?");

        assertThat(historial.sanchezlistar("orientado")).extracting(MarcaConversacion::getTitulo).containsExactly("¿Qué es el protocolo TCP?");
        assertThat(historial.sanchezlistar("CEVICHE")).hasSize(1);
        assertThat(historial.sanchezlistar("nada-de-esto")).isEmpty();

        List<SanchezMensaje> mensajes = historial.marcamensajes(todas.get(1).getId());
        assertThat(mensajes).extracting(SanchezMensaje::isDeUsuario).containsExactly(true, false);

        historial.eliminar(todas.get(1).getId());
        assertThat(historial.sanchezlistar(null)).hasSize(1);
        autenticacion.marcacerrarSesion();
    }

    @Test
    void nadieVeLasConversacionesDeOtroNiGuardaSinSesion() {
        historial.sanchezregistrar(true, "mensaje sin sesión");
        entrar("due", SanchezRol.ESTUDIANTE);
        historial.sanchezregistrar(true, "Mi conversación privada");
        Long ajena = historial.sanchezlistar(null).get(0).getId();
        autenticacion.marcacerrarSesion();

        entrar("otro", SanchezRol.DOCENTE);
        assertThat(historial.sanchezlistar(null)).isEmpty();
        assertThatThrownBy(() -> historial.marcamensajes(ajena)).isInstanceOf(SanchezPermisoDenegadoException.class);
        assertThatThrownBy(() -> historial.eliminar(ajena)).isInstanceOf(SanchezPermisoDenegadoException.class);
        autenticacion.marcacerrarSesion();
    }

    @Test
    void generaPdfDeUnaConversacion() throws IOException {
        entrar("pdf", SanchezRol.ESTUDIANTE);
        historial.sanchezregistrar(true, "Hola Atlas, ¿cómo estás?");
        historial.sanchezregistrar(false, "Muy bien, gracias. ¿En qué te ayudo?");
        Long id = historial.sanchezlistar(null).get(0).getId();

        Path ruta = reportes.marcaconversacionPdf(id, carpeta.resolve("chat"));
        assertThat(ruta.getFileName().toString()).isEqualTo("chat.pdf");
        assertThat(Files.size(ruta)).isGreaterThan(500);
        assertThat(new String(Files.readAllBytes(ruta), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        autenticacion.marcacerrarSesion();
    }

    @Test
    void generaExcelDeActividadYSoloElAdministradorExportaUsuarios() throws IOException {
        String est = entrar("est", SanchezRol.ESTUDIANTE);
        Path actividad = reportes.marcaactividadExcel(carpeta.resolve("actividad.xlsx"));
        try (InputStream in = Files.newInputStream(actividad); Workbook libro = new XSSFWorkbook(in)) {
            var hoja = libro.getSheet("Actividad");
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fecha");
            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                assertThat(hoja.getRow(i).getCell(1).getStringCellValue()).isEqualTo(est);
            }
        }
        assertThatThrownBy(() -> reportes.marcausuariosExcel(carpeta.resolve("u.xlsx"))).isInstanceOf(SanchezPermisoDenegadoException.class);
        autenticacion.marcacerrarSesion();

        String adm = entrar("adm", SanchezRol.ADMINISTRADOR);
        Path usuarios = reportes.marcausuariosExcel(carpeta.resolve("usuarios.xlsx"));
        try (InputStream in = Files.newInputStream(usuarios); Workbook libro = new XSSFWorkbook(in)) {
            var hoja = libro.getSheet("Usuarios");
            assertThat(hoja.getLastRowNum()).isGreaterThanOrEqualTo(2);
            boolean contieneHash = false;
            for (int i = 0; i <= hoja.getLastRowNum(); i++) {
                for (var celda : hoja.getRow(i)) {
                    contieneHash |= celda.getStringCellValue().startsWith("$2");
                }
            }
            assertThat(contieneHash).as("el reporte no debe incluir hashes de contraseñas").isFalse();
        }
        assertThat(adm).isNotBlank();
        autenticacion.marcacerrarSesion();
    }

    @Autowired private SanchezServicioActividad actividad;

    @Test
    void laActividadRecienteEsPrivadaSalvoParaElAdministrador() {
        String uno = entrar("priv", SanchezRol.ESTUDIANTE);
        actividad.sanchezregistrar("Acción privada de " + uno, null);
        assertThat(actividad.marcarecientes()).allMatch(r -> r.getUsuario().equals(uno));
        autenticacion.marcacerrarSesion();

        String otro = entrar("otro2", SanchezRol.ESTUDIANTE);
        assertThat(actividad.marcarecientes()).allMatch(r -> r.getUsuario().equals(otro))
                .noneMatch(r -> r.getAccion().contains(uno));
        autenticacion.marcacerrarSesion();

        entrar("adm2", SanchezRol.ADMINISTRADOR);
        assertThat(actividad.marcarecientes()).isNotEmpty();
        autenticacion.marcacerrarSesion();
    }

    @Test
    void validaElDestino() {
        entrar("dest", SanchezRol.DOCENTE);
        assertThatThrownBy(() -> reportes.marcaactividadExcel(null)).isInstanceOf(SanchezNegocioException.class);
        assertThatThrownBy(() -> reportes.marcaactividadExcel(carpeta.resolve("no-existe/a.xlsx"))).isInstanceOf(SanchezNegocioException.class);
        Path informe = reportes.sanchezequipoPdf(carpeta.resolve("equipo.pdf"));
        assertThat(Files.exists(informe)).isTrue();
        autenticacion.marcacerrarSesion();
    }
}
