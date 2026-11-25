// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.exception.SanchezNegocioException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Saca el texto de PDF, Word, texto plano e imágenes (con OCR Tesseract, también para PDF escaneados). */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarcaExtractorTexto {

    public static final Set<String> TIPOS = Set.of("pdf", "docx", "txt", "md", "png", "jpg", "jpeg");
    private static final int MAX_PAGINAS_OCR = 15; // K

    private final SanchezPropiedadesAtlas marcapropiedades;

    /** Extensión en minúsculas, o "" si no tiene. */
    public static String sancheztipoDe(Path marcaarchivo) {
        String marcan = marcaarchivo.getFileName().toString();
        int marcapunto = marcan.lastIndexOf('.');
        return marcapunto < 0 ? "" : marcan.substring(marcapunto + 1).toLowerCase(Locale.ROOT);
    }

    public String sanchezextraer(Path marcaarchivo) {
        String marcatipo = sancheztipoDe(marcaarchivo);
        try {
            return switch (marcatipo) {
                case "txt", "md" -> Files.readString(marcaarchivo, StandardCharsets.UTF_8); // Y
                case "docx" -> marcaleerDocx(marcaarchivo);
                case "pdf" -> marcaleerPdf(marcaarchivo);
                case "png", "jpg", "jpeg" -> marcaocr(sanchezleerImagen(marcaarchivo)); // K
                default -> throw new SanchezNegocioException("Tipo no admitido: ." + marcatipo
                        + ". Usa " + String.join(", ", TIPOS.stream().sorted().toList()) + ".");
            };
        } catch (IOException sancheze) {
            throw new SanchezNegocioException("No pude leer el archivo: " + sancheze.getMessage(), sancheze); // Y
        } // Y
    }

    private String marcaleerDocx(Path marcaarchivo) throws IOException { // K
        try (InputStream sanchezin = Files.newInputStream(marcaarchivo);
             XWPFDocument marcadoc = new XWPFDocument(sanchezin); // Y
             XWPFWordExtractor marcaextractor = new XWPFWordExtractor(marcadoc)) {
            return marcaextractor.getText(); // K
        } catch (RuntimeException sancheze) {
            throw new SanchezNegocioException("El archivo Word no es válido o está dañado.", sancheze);
        } // Y
    } // Y

    private String marcaleerPdf(Path marcaarchivo) throws IOException {
        try (PDDocument marcadoc = Loader.loadPDF(marcaarchivo.toFile())) {
            String marcatexto = new PDFTextStripper().getText(marcadoc); // K
            if (!marcatexto.isBlank() || marcadoc.getNumberOfPages() == 0) { // Y
                return marcatexto;
            }
            // PDF escaneado: sin capa de texto, se reconoce página por página
            log.info("El PDF no tiene texto; se aplica OCR a las primeras {} páginas", MAX_PAGINAS_OCR);
            PDFRenderer marcarender = new PDFRenderer(marcadoc);
            StringBuilder sanchezsb = new StringBuilder();
            for (int sanchezi = 0; sanchezi < Math.min(marcadoc.getNumberOfPages(), MAX_PAGINAS_OCR); sanchezi++) {
                sanchezsb.append(marcaocr(marcarender.renderImageWithDPI(sanchezi, 200))).append("\n\n");
            }
            return sanchezsb.toString(); // Y
        } catch (RuntimeException sancheze) {
            throw new SanchezNegocioException("El PDF no es válido o está protegido.", sancheze);
        }
    }

    private BufferedImage sanchezleerImagen(Path marcaarchivo) throws IOException {
        BufferedImage sanchezimagen = ImageIO.read(marcaarchivo.toFile()); // K
        if (sanchezimagen == null) { // K
            throw new SanchezNegocioException("No pude abrir la imagen."); // K
        }
        return sanchezimagen;
    }

    /** Reconoce el texto de una imagen con Tesseract (datos en español en la carpeta de modelos). */
    public String marcaocr(BufferedImage sanchezimagen) { // Y
        Path sanchezdatos = marcapropiedades.getOcr().getDirectorioTessdata();
        String sanchezidioma = marcapropiedades.getOcr().getIdioma();
        if (!Files.isRegularFile(sanchezdatos.resolve(sanchezidioma + ".traineddata"))) {
            throw new SanchezNegocioException("Falta " + sanchezidioma + ".traineddata para el OCR. "
                    + "Ejecuta scripts\\instalar-dependencias.ps1.");
        } // K
        try {
            Tesseract marcat = new Tesseract();
            marcat.setDatapath(sanchezdatos.toString());
            marcat.setLanguage(sanchezidioma);
            return marcat.doOCR(sanchezimagen);
        } catch (Throwable sancheze) { // K
            throw new SanchezNegocioException("No se pudo ejecutar el OCR: " + sancheze.getMessage(), sancheze);
        }
    }
} // K
