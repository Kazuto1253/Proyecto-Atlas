package pe.upeu.atlas.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.atlas")
public class SanchezPropiedadesAtlas {

    @NotNull
    private Path directorioBase;

    @NotNull
    private Path directorioModelos;

    @NotBlank
    private String idioma = "es";

    @NotNull
    private SanchezTema tema = SanchezTema.CLARO;

    @NotNull
    private Path carpetaCapturas;

    private List<Path> carpetasPermitidas = new ArrayList<>();

    private List<Path> carpetasProtegidas = new ArrayList<>();

    private boolean modoSoloLectura = false;

    @NotBlank
    private String atajo = "Ctrl+Espacio";

    @Valid
    private final MarcaOcr ocr = new MarcaOcr();

    public enum SanchezTema { CLARO, OSCURO }

    @Valid
    private final SanchezLlm llm = new SanchezLlm();

    @Valid
    private final SanchezVoz voz = new SanchezVoz();

    @Getter
    @Setter
    public static class SanchezLlm {
        @NotBlank
        private String url = "http://localhost:11434";

        @NotBlank
        private String modelo = "qwen2.5:3b";

        @NotBlank
        private String modeloEmbeddings = "nomic-embed-text";

        private String modeloVision = "qwen2.5vl:3b";

        @DecimalMin("0.0")
        @DecimalMax("2.0")
        private double temperatura = 0.3;

        @Min(5)
        private int tiempoEsperaSegundos = 120;

        @Min(2)
        private int mensajesMemoria = 20;
    }

    @Getter
    @Setter
    public static class SanchezVoz {
        @NotNull
        private Path modeloVosk;

        @NotNull
        private Path ejecutablePiper;

        @NotNull
        private Path vozPiper;

        @Min(8000)
        private int frecuenciaMuestreo = 16000;

        @Min(0)
        private int umbralSilencio = 0;

        private String microfono = "";

        @DecimalMin("1.0")
        @DecimalMax("20.0")
        private double gananciaMaxima = 3.5;

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double confianzaMinima = 0.6;

        private boolean usarComandos = true;

        private boolean usarWhisper = true;

        private Path whisperEjecutable;

        private Path whisperModelo;

        @Min(1)
        private int whisperHilos = 4;

        private boolean guardarAudioDepuracion = false;

        @Min(200)
        private int milisegundosSilencio = 1200;

        @Min(2)
        private int segundosMaximosFrase = 15;

        @NotBlank
        private String palabraActivacion = "oye atlas";

        public Path ejecutablePiperResuelto() {
            if (ejecutablePiper == null) {
                return null;
            }
            boolean sanchezwindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            if (sanchezwindows && !java.nio.file.Files.isRegularFile(ejecutablePiper)
                    && !ejecutablePiper.getFileName().toString().endsWith(".exe")) {
                return ejecutablePiper.resolveSibling(ejecutablePiper.getFileName() + ".exe");
            }
            return ejecutablePiper;
        }
    }

    @Getter
    @Setter
    public static class MarcaOcr {
        @NotNull
        private Path directorioTessdata;

        @NotBlank
        private String idioma = "spa";
    }
}
