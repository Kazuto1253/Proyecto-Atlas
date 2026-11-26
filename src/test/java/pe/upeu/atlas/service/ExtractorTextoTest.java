package pe.upeu.atlas.service;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.exception.SanchezNegocioException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExtractorTextoTest {

    @TempDir Path carpeta;

    private MarcaExtractorTexto extractor;
    private Path tessdata;

    @BeforeEach
    void preparar() {
        tessdata = Path.of(System.getProperty("user.home"), ".atlas", "modelos", "tessdata");
        SanchezPropiedadesAtlas props = new SanchezPropiedadesAtlas();
        props.getOcr().setDirectorioTessdata(tessdata);
        extractor = new MarcaExtractorTexto(props);
    }

    @Test
    void leeTextoPlano() throws IOException {
        Path txt = Files.writeString(carpeta.resolve("n.md"), "# Título\nContenido con tildes: canción");
        assertThat(extractor.sanchezextraer(txt)).contains("canción");
    }

    @Test
    void leeWord() throws IOException {
        Path docx = carpeta.resolve("t.docx");
        try (XWPFDocument d = new XWPFDocument(); OutputStream out = Files.newOutputStream(docx)) {
            d.createParagraph().createRun().setText("Apuntes de redes en Word");
            d.write(out);
        }
        assertThat(extractor.sanchezextraer(docx)).contains("Apuntes de redes en Word");
    }

    @Test
    void leePdfConTexto() throws IOException {
        Path pdf = carpeta.resolve("t.pdf");
        Document doc = new Document();
        try (OutputStream out = Files.newOutputStream(pdf)) {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("Modelo OSI de siete capas"));
            doc.close();
        }
        assertThat(extractor.sanchezextraer(pdf)).contains("Modelo OSI");
    }

    @Test
    void rechazaTiposYArchivosDanados() throws IOException {
        assertThatThrownBy(() -> extractor.sanchezextraer(carpeta.resolve("a.exe"))).isInstanceOf(SanchezNegocioException.class);
        Path falso = Files.writeString(carpeta.resolve("malo.docx"), "esto no es un docx");
        assertThatThrownBy(() -> extractor.sanchezextraer(falso)).isInstanceOf(SanchezNegocioException.class);
        Path pdfFalso = Files.writeString(carpeta.resolve("malo.pdf"), "tampoco");
        assertThatThrownBy(() -> extractor.sanchezextraer(pdfFalso)).isInstanceOf(SanchezNegocioException.class);
    }

    @Test
    void ocrReconoceTextoEnUnaImagen() throws IOException {
        assumeTrue(Files.isRegularFile(tessdata.resolve("spa.traineddata")), "Falta spa.traineddata en ~/.atlas");
        BufferedImage img = new BufferedImage(900, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 900, 160);
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("SansSerif", Font.BOLD, 56));
        g.drawString("Hola mundo redes", 30, 100);
        g.dispose();
        Path png = carpeta.resolve("ocr.png");
        ImageIO.write(img, "png", png.toFile());

        String texto = extractor.sanchezextraer(png).toLowerCase();
        System.out.println("OCR -> " + texto.strip());
        assertThat(texto).contains("hola").contains("redes");
    }

    @Test
    void ocrSinDatosDaUnMensajeClaro() throws IOException {
        SanchezPropiedadesAtlas props = new SanchezPropiedadesAtlas();
        props.getOcr().setDirectorioTessdata(carpeta.resolve("no-hay"));
        Path png = carpeta.resolve("x.png");
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", png.toFile());

        assertThatThrownBy(() -> new MarcaExtractorTexto(props).sanchezextraer(png))
                .isInstanceOf(SanchezNegocioException.class).hasMessageContaining("traineddata");
    }
}
