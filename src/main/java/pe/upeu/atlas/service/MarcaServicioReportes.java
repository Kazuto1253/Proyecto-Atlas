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
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;
import pe.upeu.atlas.model.MarcaConversacion;
import pe.upeu.atlas.model.SanchezMensaje;
import pe.upeu.atlas.model.MarcaRegistroActividad;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.model.MarcaUsuario;
import pe.upeu.atlas.repository.MarcaRegistroActividadRepository;
import pe.upeu.atlas.repository.MarcaUsuarioRepository;
import pe.upeu.atlas.seguridad.SanchezSesionActual;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MarcaServicioReportes {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SanchezServicioHistorial sanchezhistorial;
    private final MarcaRegistroActividadRepository sanchezactividadRepo;
    private final MarcaUsuarioRepository sanchezusuarios;
    private final MarcaMonitorSistema marcamonitor;
    private final SanchezSesionActual sanchezsesion;
    private final SanchezServicioActividad sanchezactividad;

    public Path marcaconversacionPdf(Long sanchezconversacionId, Path marcadestino) {
        MarcaConversacion sanchezc = sanchezhistorial.sanchezobtener(sanchezconversacionId);
        List<SanchezMensaje> marcamensajes = sanchezhistorial.marcamensajes(sanchezconversacionId);
        Path marcaruta = sanchezvalidar(marcadestino, ".pdf");
        sanchezescribirPdf(marcaruta, marcadoc -> {
            marcadoc.add(titulo("Conversación con Atlas"));
            marcadoc.add(sanchezmeta(sanchezc.getTitulo()));
            marcadoc.add(sanchezmeta("Usuario: " + sanchezc.getUsuario() + " · Iniciada: " + sanchezc.getCreada().format(FECHA)));
            marcadoc.add(new Paragraph(" "));
            for (SanchezMensaje sanchezm : marcamensajes) {
                Paragraph marcaquien = new Paragraph((sanchezm.isDeUsuario() ? "Tú" : "Atlas") + "  ·  " + sanchezm.getFecha().format(FECHA),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, sanchezm.isDeUsuario() ? java.awt.Color.DARK_GRAY : new java.awt.Color(0, 90, 160)));
                marcaquien.setSpacingBefore(10);
                marcadoc.add(marcaquien);
                marcadoc.add(new Paragraph(sanchezm.getTexto(), FontFactory.getFont(FontFactory.HELVETICA, 11)));
            }
        });
        sanchezactividad.sanchezregistrar("Reporte generado", "Conversación en PDF");
        return marcaruta;
    }

    public Path marcaactividadExcel(Path marcadestino) {
        MarcaUsuario sanchezu = sanchezsesion.marcaactual().orElseThrow(() -> new SanchezPermisoDenegadoException("Inicia sesión."));
        boolean sancheztodos = sanchezu.getRol() == SanchezRol.ADMINISTRADOR;
        List<MarcaRegistroActividad> sanchezfilas = sancheztodos
                ? sanchezactividadRepo.findTop5000ByOrderByFechaDescIdDesc()
                : sanchezactividadRepo.findTop5000ByUsuarioOrderByFechaDescIdDesc(sanchezu.getUsuario());
        Path marcaruta = sanchezvalidar(marcadestino, ".xlsx");
        marcaescribirExcel(marcaruta, "Actividad", List.of("Fecha", "Usuario", "Acción", "Detalle"), sanchezfilas.stream()
                .map(marcar -> List.<Object>of(marcar.getFecha().format(FECHA), marcar.getUsuario(), marcar.getAccion(),
                        marcar.getDetalle() == null ? "" : marcar.getDetalle()))
                .toList());
        sanchezactividad.sanchezregistrar("Reporte generado", "Actividad en Excel (" + sanchezfilas.size() + " filas)");
        return marcaruta;
    }

    public Path marcausuariosExcel(Path marcadestino) {
        if (sanchezsesion.sanchezrol().filter(SanchezRol::marcapuedeAdministrarUsuarios).isEmpty()) {
            throw new SanchezPermisoDenegadoException("Solo un administrador puede exportar los usuarios.");
        }
        List<MarcaUsuario> sanchezlista = sanchezusuarios.findAllByOrderByNombreCompletoAsc();
        Path marcaruta = sanchezvalidar(marcadestino, ".xlsx");
        marcaescribirExcel(marcaruta, "Usuarios", List.of("Nombre", "Usuario", "Rol", "Estado", "Creado", "Último acceso"), sanchezlista.stream()
                .map(marcax -> List.<Object>of(marcax.getNombreCompleto(), marcax.getUsuario(), marcax.getRol().marcaetiqueta(),
                        marcax.isActivo() ? "Activo" : "Desactivado", marcax.getFechaCreacion().format(FECHA),
                        marcax.getUltimoAcceso() == null ? "Nunca" : marcax.getUltimoAcceso().format(FECHA)))
                .toList());
        sanchezactividad.sanchezregistrar("Reporte generado", "Usuarios en Excel");
        return marcaruta;
    }

    public Path sanchezequipoPdf(Path marcadestino) {
        MarcaUsuario sanchezu = sanchezsesion.marcaactual().orElseThrow(() -> new SanchezPermisoDenegadoException("Inicia sesión."));
        MarcaMonitorSistema.MarcaMetricas sanchezm = marcamonitor.sanchezmedir();
        MarcaMonitorSistema.SanchezInfoEquipo marcainfo = marcamonitor.info();
        Path marcaruta = sanchezvalidar(marcadestino, ".pdf");
        sanchezescribirPdf(marcaruta, marcadoc -> {
            marcadoc.add(titulo("Informe del equipo"));
            marcadoc.add(sanchezmeta("Generado el " + LocalDateTime.now().format(FECHA) + " por " + sanchezu.getNombreCompleto()));
            marcadoc.add(new Paragraph(" "));
            marcadoc.add(sanchezlinea("Sistema", marcainfo.marcasistema()));
            marcadoc.add(sanchezlinea("Procesador", marcainfo.marcaprocesador() + " (" + marcainfo.sancheznucleos() + " lógicos)"));
            marcadoc.add(sanchezlinea("Java", marcainfo.marcajava()));
            marcadoc.add(sanchezlinea("Encendido hace", MarcaMonitorSistema.sanchezduracion(sanchezm.sanchezsegundosEncendido())));
            marcadoc.add(new Paragraph(" "));
            marcadoc.add(sanchezlinea("Uso del procesador", Math.round(sanchezm.marcacpu() * 100) + " %"));
            marcadoc.add(sanchezlinea("Memoria", Math.round(sanchezm.marcaram() * 100) + " % (" + MarcaMonitorSistema.sanchezbytes(sanchezm.marcaramUsada())
                    + " de " + MarcaMonitorSistema.sanchezbytes(sanchezm.marcaramTotal()) + ")"));
            marcadoc.add(sanchezlinea("Disco", Math.round(sanchezm.marcadisco() * 100) + " % (" + MarcaMonitorSistema.sanchezbytes(sanchezm.marcadiscoUsado())
                    + " de " + MarcaMonitorSistema.sanchezbytes(sanchezm.marcadiscoTotal()) + ")"));
            marcadoc.add(sanchezlinea("Batería", sanchezm.marcahayBateria() ? Math.round(sanchezm.marcabateria() * 100) + " %"
                    + (sanchezm.sanchezcargando() ? " (conectada)" : "") : "El equipo no tiene batería"));
        });
        sanchezactividad.sanchezregistrar("Reporte generado", "Informe del equipo en PDF");
        return marcaruta;
    }

    @FunctionalInterface
    private interface SanchezContenido {
        void sanchezagregar(Document marcadoc) throws DocumentException;
    }

    private void sanchezescribirPdf(Path marcaruta, SanchezContenido sanchezcontenido) {
        Document marcadoc = new Document(PageSize.A4, 50, 50, 60, 50);
        try (OutputStream marcaout = Files.newOutputStream(marcaruta)) {
            PdfWriter.getInstance(marcadoc, marcaout);
            marcadoc.addAuthor("Atlas");
            marcadoc.open();
            sanchezcontenido.sanchezagregar(marcadoc);
            marcadoc.close();
        } catch (IOException | DocumentException sancheze) {
            throw new SanchezNegocioException("No pude crear el PDF: " + sancheze.getMessage(), sancheze);
        }
    }

    private void marcaescribirExcel(Path marcaruta, String marcahoja, List<String> marcacolumnas, List<List<Object>> sanchezfilas) {
        try (XSSFWorkbook marcalibro = new XSSFWorkbook(); OutputStream marcaout = Files.newOutputStream(marcaruta)) {
            Sheet marcah = marcalibro.createSheet(marcahoja);
            CellStyle marcanegrita = marcalibro.createCellStyle();
            XSSFFont sanchezfuente = marcalibro.createFont();
            sanchezfuente.setBold(true);
            marcanegrita.setFont(sanchezfuente);
            Row marcacab = marcah.createRow(0);
            for (int sanchezi = 0; sanchezi < marcacolumnas.size(); sanchezi++) {
                marcacab.createCell(sanchezi).setCellValue(marcacolumnas.get(sanchezi));
                marcacab.getCell(sanchezi).setCellStyle(marcanegrita);
            }
            for (int marcar = 0; marcar < sanchezfilas.size(); marcar++) {
                Row marcafila = marcah.createRow(marcar + 1);
                for (int sanchezc = 0; sanchezc < sanchezfilas.get(marcar).size(); sanchezc++) {
                    marcafila.createCell(sanchezc).setCellValue(String.valueOf(sanchezfilas.get(marcar).get(sanchezc)));
                }
            }
            for (int sanchezi = 0; sanchezi < marcacolumnas.size(); sanchezi++) {
                marcah.autoSizeColumn(sanchezi);
            }
            marcalibro.write(marcaout);
        } catch (IOException sancheze) {
            throw new SanchezNegocioException("No pude crear el Excel: " + sancheze.getMessage(), sancheze);
        }
    }

    private static Path sanchezvalidar(Path marcadestino, String sanchezextension) {
        if (marcadestino == null) {
            throw new SanchezNegocioException("Elige dónde guardar el archivo.");
        }
        Path marcaruta = marcadestino.toAbsolutePath().normalize();
        if (!marcaruta.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(sanchezextension)) {
            marcaruta = marcaruta.resolveSibling(marcaruta.getFileName() + sanchezextension);
        }
        if (marcaruta.getParent() == null || !Files.isDirectory(marcaruta.getParent())) {
            throw new SanchezNegocioException("La carpeta de destino no existe.");
        }
        return marcaruta;
    }

    private static Paragraph titulo(String marcatexto) {
        Paragraph marcap = new Paragraph(marcatexto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20));
        marcap.setAlignment(Element.ALIGN_LEFT);
        return marcap;
    }

    private static Paragraph sanchezmeta(String marcatexto) {
        return new Paragraph(marcatexto, FontFactory.getFont(FontFactory.HELVETICA, 10, java.awt.Color.GRAY));
    }

    private static Paragraph sanchezlinea(String marcaetiqueta, String marcavalor) {
        Paragraph marcap = new Paragraph();
        marcap.add(new com.lowagie.text.Chunk(marcaetiqueta + ": ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        marcap.add(new com.lowagie.text.Chunk(marcavalor, FontFactory.getFont(FontFactory.HELVETICA, 12)));
        marcap.setSpacingAfter(4);
        return marcap;
    }
}
