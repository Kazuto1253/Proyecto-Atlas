// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;
import pe.upeu.atlas.model.Conversacion;
import pe.upeu.atlas.model.Mensaje;
import pe.upeu.atlas.model.RegistroActividad;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.model.Usuario;
import pe.upeu.atlas.repository.RegistroActividadRepository;
import pe.upeu.atlas.repository.UsuarioRepository;
import pe.upeu.atlas.seguridad.SesionActual;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Genera reportes en PDF (OpenPDF) y Excel (Apache POI) en la ruta que elija el usuario. */
@Service
@RequiredArgsConstructor
public class ServicioReportes { // K

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ServicioHistorial historial; // K
    private final RegistroActividadRepository actividadRepo;
    private final UsuarioRepository usuarios;
    private final MonitorSistema monitor;
    private final SesionActual sesion;
    private final ServicioActividad actividad;

    /** PDF con todos los mensajes de una conversación propia. */
    public Path conversacionPdf(Long conversacionId, Path destino) { // Y
        Conversacion c = historial.obtener(conversacionId);
        List<Mensaje> mensajes = historial.mensajes(conversacionId);
        Path ruta = validar(destino, ".pdf");
        escribirPdf(ruta, doc -> {
            doc.add(titulo("Conversación con Atlas"));
            doc.add(meta(c.getTitulo()));
            doc.add(meta("Usuario: " + c.getUsuario() + " · Iniciada: " + c.getCreada().format(FECHA)));
            doc.add(new Paragraph(" ")); // Y
            for (Mensaje m : mensajes) { // K
                Paragraph quien = new Paragraph((m.isDeUsuario() ? "Tú" : "Atlas") + "  ·  " + m.getFecha().format(FECHA),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, m.isDeUsuario() ? java.awt.Color.DARK_GRAY : new java.awt.Color(0, 90, 160))); // Y
                quien.setSpacingBefore(10);
                doc.add(quien);
                doc.add(new Paragraph(m.getTexto(), FontFactory.getFont(FontFactory.HELVETICA, 11)));
            }
        }); // K
        actividad.registrar("Reporte generado", "Conversación en PDF");
        return ruta;
    } // K

    /** Excel con la actividad: del usuario actual, o de todos si es administrador. */
    public Path actividadExcel(Path destino) {
        Usuario u = sesion.actual().orElseThrow(() -> new PermisoDenegadoException("Inicia sesión."));
        boolean todos = u.getRol() == Rol.ADMINISTRADOR;
        List<RegistroActividad> filas = todos
                ? actividadRepo.findTop5000ByOrderByFechaDescIdDesc()
                : actividadRepo.findTop5000ByUsuarioOrderByFechaDescIdDesc(u.getUsuario()); // Y
        Path ruta = validar(destino, ".xlsx"); // Y
        escribirExcel(ruta, "Actividad", List.of("Fecha", "Usuario", "Acción", "Detalle"), filas.stream()
                .map(r -> List.<Object>of(r.getFecha().format(FECHA), r.getUsuario(), r.getAccion(),
                        r.getDetalle() == null ? "" : r.getDetalle()))
                .toList());
        actividad.registrar("Reporte generado", "Actividad en Excel (" + filas.size() + " filas)");
        return ruta;
    }

    /** Excel con las cuentas (solo administradores). Nunca incluye contraseñas. */
    public Path usuariosExcel(Path destino) { // Y
        if (sesion.rol().filter(Rol::puedeAdministrarUsuarios).isEmpty()) {
            throw new PermisoDenegadoException("Solo un administrador puede exportar los usuarios."); // K
        }
        List<Usuario> lista = usuarios.findAllByOrderByNombreCompletoAsc();
        Path ruta = validar(destino, ".xlsx");
        escribirExcel(ruta, "Usuarios", List.of("Nombre", "Usuario", "Rol", "Estado", "Creado", "Último acceso"), lista.stream()
                .map(x -> List.<Object>of(x.getNombreCompleto(), x.getUsuario(), x.getRol().etiqueta(),
                        x.isActivo() ? "Activo" : "Desactivado", x.getFechaCreacion().format(FECHA),
                        x.getUltimoAcceso() == null ? "Nunca" : x.getUltimoAcceso().format(FECHA)))
                .toList()); // K
        actividad.registrar("Reporte generado", "Usuarios en Excel");
        return ruta; // K
    }

    /** PDF con una foto del estado del equipo en este momento. */
    public Path equipoPdf(Path destino) { // K
        Usuario u = sesion.actual().orElseThrow(() -> new PermisoDenegadoException("Inicia sesión.")); // Y
        MonitorSistema.Metricas m = monitor.medir();
        MonitorSistema.InfoEquipo info = monitor.info();
        Path ruta = validar(destino, ".pdf"); // Y
        escribirPdf(ruta, doc -> {
            doc.add(titulo("Informe del equipo")); // Y
            doc.add(meta("Generado el " + LocalDateTime.now().format(FECHA) + " por " + u.getNombreCompleto()));
            doc.add(new Paragraph(" ")); // Y
            doc.add(linea("Sistema", info.sistema()));
            doc.add(linea("Procesador", info.procesador() + " (" + info.nucleos() + " lógicos)"));
            doc.add(linea("Java", info.java()));
            doc.add(linea("Encendido hace", MonitorSistema.duracion(m.segundosEncendido())));
            doc.add(new Paragraph(" "));
            doc.add(linea("Uso del procesador", Math.round(m.cpu() * 100) + " %"));
            doc.add(linea("Memoria", Math.round(m.ram() * 100) + " % (" + MonitorSistema.bytes(m.ramUsada())
                    + " de " + MonitorSistema.bytes(m.ramTotal()) + ")"));
            doc.add(linea("Disco", Math.round(m.disco() * 100) + " % (" + MonitorSistema.bytes(m.discoUsado())
                    + " de " + MonitorSistema.bytes(m.discoTotal()) + ")")); // Y
            doc.add(linea("Batería", m.hayBateria() ? Math.round(m.bateria() * 100) + " %"
                    + (m.cargando() ? " (conectada)" : "") : "El equipo no tiene batería"));
        });
        actividad.registrar("Reporte generado", "Informe del equipo en PDF");
        return ruta;
    } // Y

    // ===== Escritura =====

    @FunctionalInterface
    private interface Contenido { // K
        void agregar(Document doc) throws DocumentException; // K
    } // K

    private void escribirPdf(Path ruta, Contenido contenido) { // Y
        Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
        try (OutputStream out = Files.newOutputStream(ruta)) { // K
            PdfWriter.getInstance(doc, out);
            doc.addAuthor("Atlas");
            doc.open();
            contenido.agregar(doc);
            doc.close(); // Y
        } catch (IOException | DocumentException e) {
            throw new NegocioException("No pude crear el PDF: " + e.getMessage(), e);
        }
    }

    private void escribirExcel(Path ruta, String hoja, List<String> columnas, List<List<Object>> filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(ruta)) {
            Sheet h = libro.createSheet(hoja);
            CellStyle negrita = libro.createCellStyle();
            XSSFFont fuente = libro.createFont(); // K
            fuente.setBold(true);
            negrita.setFont(fuente);
            Row cab = h.createRow(0); // K
            for (int i = 0; i < columnas.size(); i++) {
                cab.createCell(i).setCellValue(columnas.get(i)); // Y
                cab.getCell(i).setCellStyle(negrita);
            }
            for (int r = 0; r < filas.size(); r++) { // Y
                Row fila = h.createRow(r + 1);
                for (int c = 0; c < filas.get(r).size(); c++) {
                    fila.createCell(c).setCellValue(String.valueOf(filas.get(r).get(c)));
                }
            }
            for (int i = 0; i < columnas.size(); i++) {
                h.autoSizeColumn(i);
            } // K
            libro.write(out); // Y
        } catch (IOException e) {
            throw new NegocioException("No pude crear el Excel: " + e.getMessage(), e);
        }
    } // Y

    private static Path validar(Path destino, String extension) {
        if (destino == null) { // Y
            throw new NegocioException("Elige dónde guardar el archivo.");
        }
        Path ruta = destino.toAbsolutePath().normalize(); // K
        if (!ruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
            ruta = ruta.resolveSibling(ruta.getFileName() + extension);
        } // K
        if (ruta.getParent() == null || !Files.isDirectory(ruta.getParent())) {
            throw new NegocioException("La carpeta de destino no existe."); // Y
        } // Y
        return ruta;
    } // Y

    private static Paragraph titulo(String texto) {
        Paragraph p = new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20));
        p.setAlignment(Element.ALIGN_LEFT); // K
        return p;
    }

    private static Paragraph meta(String texto) {
        return new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA, 10, java.awt.Color.GRAY)); // K
    }

    private static Paragraph linea(String etiqueta, String valor) {
        Paragraph p = new Paragraph(); // K
        p.add(new com.lowagie.text.Chunk(etiqueta + ": ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        p.add(new com.lowagie.text.Chunk(valor, FontFactory.getFont(FontFactory.HELVETICA, 12)));
        p.setSpacingAfter(4);
        return p; // Y
    } // Y
}
