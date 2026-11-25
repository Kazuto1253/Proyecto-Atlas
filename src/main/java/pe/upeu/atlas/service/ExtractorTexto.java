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
import pe.upeu.atlas.config.PropiedadesAtlas;
import pe.upeu.atlas.exception.NegocioException;

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
public class ExtractorTexto {

    public static final Set<String> TIPOS = Set.of("pdf", "docx", "txt", "md", "png", "jpg", "jpeg");
    private static final int MAX_PAGINAS_OCR = 15; // K

    private final PropiedadesAtlas propiedades;

    /** Extensión en minúsculas, o "" si no tiene. */
    public static String tipoDe(Path archivo) {
        String n = archivo.getFileName().toString();
        int punto = n.lastIndexOf('.');
        return punto < 0 ? "" : n.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    public String extraer(Path archivo) {
        String tipo = tipoDe(archivo);
        try {
            return switch (tipo) {
                case "txt", "md" -> Files.readString(archivo, StandardCharsets.UTF_8); // Y
                case "docx" -> leerDocx(archivo);
                case "pdf" -> leerPdf(archivo);
                case "png", "jpg", "jpeg" -> ocr(leerImagen(archivo)); // K
                default -> throw new NegocioException("Tipo no admitido: ." + tipo
                        + ". Usa " + String.join(", ", TIPOS.stream().sorted().toList()) + ".");
            };
        } catch (IOException e) {
            throw new NegocioException("No pude leer el archivo: " + e.getMessage(), e); // Y
        } // Y
    }

    private String leerDocx(Path archivo) throws IOException { // K
        try (InputStream in = Files.newInputStream(archivo);
             XWPFDocument doc = new XWPFDocument(in); // Y
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText(); // K
        } catch (RuntimeException e) {
            throw new NegocioException("El archivo Word no es válido o está dañado.", e);
        } // Y
    } // Y

    private String leerPdf(Path archivo) throws IOException {
        try (PDDocument doc = Loader.loadPDF(archivo.toFile())) {
            String texto = new PDFTextStripper().getText(doc); // K
            if (!texto.isBlank() || doc.getNumberOfPages() == 0) { // Y
                return texto;
            }
            // PDF escaneado: sin capa de texto, se reconoce página por página
            log.info("El PDF no tiene texto; se aplica OCR a las primeras {} páginas", MAX_PAGINAS_OCR);
            PDFRenderer render = new PDFRenderer(doc);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(doc.getNumberOfPages(), MAX_PAGINAS_OCR); i++) {
                sb.append(ocr(render.renderImageWithDPI(i, 200))).append("\n\n");
            }
            return sb.toString(); // Y
        } catch (RuntimeException e) {
            throw new NegocioException("El PDF no es válido o está protegido.", e);
        }
    }

    private BufferedImage leerImagen(Path archivo) throws IOException {
        BufferedImage imagen = ImageIO.read(archivo.toFile()); // K
        if (imagen == null) { // K
            throw new NegocioException("No pude abrir la imagen."); // K
        }
        return imagen;
    }

    /** Reconoce el texto de una imagen con Tesseract (datos en español en la carpeta de modelos). */
    public String ocr(BufferedImage imagen) { // Y
        Path datos = propiedades.getOcr().getDirectorioTessdata();
        String idioma = propiedades.getOcr().getIdioma();
        if (!Files.isRegularFile(datos.resolve(idioma + ".traineddata"))) {
            throw new NegocioException("Falta " + idioma + ".traineddata para el OCR. "
                    + "Ejecuta scripts\\instalar-dependencias.ps1.");
        } // K
        try {
            Tesseract t = new Tesseract();
            t.setDatapath(datos.toString());
            t.setLanguage(idioma);
            return t.doOCR(imagen);
        } catch (Throwable e) { // K
            throw new NegocioException("No se pudo ejecutar el OCR: " + e.getMessage(), e);
        }
    }
} // K
