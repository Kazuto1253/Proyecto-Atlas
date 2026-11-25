// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.model.Conversacion;
import pe.upeu.atlas.model.Mensaje;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.seguridad.ServicioAutenticacion;

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

    @Autowired private ServicioHistorial historial;
    @Autowired private ServicioReportes reportes;
    @Autowired private ServicioAutenticacion autenticacion;

    @TempDir Path carpeta;

    private String entrar(String base, Rol rol) {
        String id = base + UUID.randomUUID().toString().substring(0, 8);
        autenticacion.registrar("Usuario " + base, id, "secreto1", rol);
        autenticacion.iniciarSesion(id, "secreto1");
        historial.terminarConversacion();
        return id;
    }

    @Test
    void guardaConversacionesBuscaYElimina() {
        entrar("hist", Rol.ESTUDIANTE);
        historial.registrar(true, "¿Qué es el protocolo TCP?\nExplícame con calma");
        historial.registrar(false, "TCP es un protocolo orientado a conexión.");
        historial.terminarConversacion();
        historial.registrar(true, "Dame una receta de ceviche");
        historial.registrar(false, "Pescado fresco, limón y ají.");

        List<Conversacion> todas = historial.listar(null);
        assertThat(todas).hasSize(2);
        assertThat(todas.get(0).getTitulo()).isEqualTo("Dame una receta de ceviche");
        assertThat(todas.get(1).getTitulo()).isEqualTo("¿Qué es el protocolo TCP?");

        // La búsqueda mira títulos y también el contenido de los mensajes
        assertThat(historial.listar("orientado")).extracting(Conversacion::getTitulo).containsExactly("¿Qué es el protocolo TCP?");
        assertThat(historial.listar("CEVICHE")).hasSize(1);
        assertThat(historial.listar("nada-de-esto")).isEmpty();

        List<Mensaje> mensajes = historial.mensajes(todas.get(1).getId());
        assertThat(mensajes).extracting(Mensaje::isDeUsuario).containsExactly(true, false);

        historial.eliminar(todas.get(1).getId());
        assertThat(historial.listar(null)).hasSize(1);
        autenticacion.cerrarSesion();
    }

    @Test
    void nadieVeLasConversacionesDeOtroNiGuardaSinSesion() {
        historial.registrar(true, "mensaje sin sesión"); // se ignora
        entrar("due", Rol.ESTUDIANTE);
        historial.registrar(true, "Mi conversación privada");
        Long ajena = historial.listar(null).get(0).getId();
        autenticacion.cerrarSesion();

        entrar("otro", Rol.DOCENTE);
        assertThat(historial.listar(null)).isEmpty();
        assertThatThrownBy(() -> historial.mensajes(ajena)).isInstanceOf(PermisoDenegadoException.class);
        assertThatThrownBy(() -> historial.eliminar(ajena)).isInstanceOf(PermisoDenegadoException.class);
        autenticacion.cerrarSesion();
    }

    @Test
    void generaPdfDeUnaConversacion() throws IOException {
        entrar("pdf", Rol.ESTUDIANTE);
        historial.registrar(true, "Hola Atlas, ¿cómo estás?");
        historial.registrar(false, "Muy bien, gracias. ¿En qué te ayudo?");
        Long id = historial.listar(null).get(0).getId();

        Path ruta = reportes.conversacionPdf(id, carpeta.resolve("chat")); // sin extensión: se agrega
        assertThat(ruta.getFileName().toString()).isEqualTo("chat.pdf");
        assertThat(Files.size(ruta)).isGreaterThan(500);
        assertThat(new String(Files.readAllBytes(ruta), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        autenticacion.cerrarSesion();
    }

    @Test
    void generaExcelDeActividadYSoloElAdministradorExportaUsuarios() throws IOException {
        String est = entrar("est", Rol.ESTUDIANTE);
        Path actividad = reportes.actividadExcel(carpeta.resolve("actividad.xlsx"));
        try (InputStream in = Files.newInputStream(actividad); Workbook libro = new XSSFWorkbook(in)) {
            var hoja = libro.getSheet("Actividad");
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fecha");
            // un estudiante solo ve su propia actividad
            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                assertThat(hoja.getRow(i).getCell(1).getStringCellValue()).isEqualTo(est);
            }
        }
        assertThatThrownBy(() -> reportes.usuariosExcel(carpeta.resolve("u.xlsx"))).isInstanceOf(PermisoDenegadoException.class);
        autenticacion.cerrarSesion();

        String adm = entrar("adm", Rol.ADMINISTRADOR);
        Path usuarios = reportes.usuariosExcel(carpeta.resolve("usuarios.xlsx"));
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
        autenticacion.cerrarSesion();
    }

    @Autowired private ServicioActividad actividad;

    @Test
    void laActividadRecienteEsPrivadaSalvoParaElAdministrador() {
        String uno = entrar("priv", Rol.ESTUDIANTE);
        actividad.registrar("Acción privada de " + uno, null);
        assertThat(actividad.recientes()).allMatch(r -> r.getUsuario().equals(uno));
        autenticacion.cerrarSesion();

        String otro = entrar("otro2", Rol.ESTUDIANTE);
        assertThat(actividad.recientes()).allMatch(r -> r.getUsuario().equals(otro))
                .noneMatch(r -> r.getAccion().contains(uno));
        autenticacion.cerrarSesion();

        entrar("adm2", Rol.ADMINISTRADOR);
        assertThat(actividad.recientes()).isNotEmpty();
        autenticacion.cerrarSesion();
    }

    @Test
    void validaElDestino() {
        entrar("dest", Rol.DOCENTE);
        assertThatThrownBy(() -> reportes.actividadExcel(null)).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> reportes.actividadExcel(carpeta.resolve("no-existe/a.xlsx"))).isInstanceOf(NegocioException.class);
        Path informe = reportes.equipoPdf(carpeta.resolve("equipo.pdf"));
        assertThat(Files.exists(informe)).isTrue();
        autenticacion.cerrarSesion();
    }
}
