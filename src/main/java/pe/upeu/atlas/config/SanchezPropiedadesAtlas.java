// Autores: K (Kevin) y Y (Yenifher)
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

/**
 * Configuracion de Atlas leida de {@code app.atlas.*} en application.properties.
 * Los modelos (Vosk, Piper, Ollama) viven fuera del JAR, en {@link #directorioModelos}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.atlas")
public class SanchezPropiedadesAtlas {

    /** Carpeta raiz de datos del usuario: base de datos, logs y modelos. */
    @NotNull
    private Path directorioBase;

    @NotNull
    private Path directorioModelos;

    @NotBlank
    private String idioma = "es";

    /** Tema visual de AtlantaFX. */
    @NotNull
    private SanchezTema tema = SanchezTema.CLARO; // K

    /** Donde se guardan las capturas de pantalla (fuera de ~/.atlas para que el usuario las vea). */
    @NotNull
    private Path carpetaCapturas;

    /**
     * Carpetas con nombre propio para el asistente ("pídele que abra proyectos"). Ya NO limita el acceso:
     * el asistente puede usar todo el equipo salvo las zonas protegidas.
     */
    private List<Path> carpetasPermitidas = new ArrayList<>(); // Y

    /** Carpetas adicionales que el asistente nunca debe leer ni modificar (además de las del sistema). */
    private List<Path> carpetasProtegidas = new ArrayList<>(); // Y

    /** Desactiva todas las herramientas de ESCRITURA y PELIGROSA. */
    private boolean modoSoloLectura = false;

    /** Atajo global para activar la escucha (formato JNativeHook: "Ctrl+Espacio"). */
    @NotBlank
    private String atajo = "Ctrl+Espacio";

    @Valid
    private final MarcaOcr ocr = new MarcaOcr();

    public enum SanchezTema { CLARO, OSCURO }

    @Valid
    private final SanchezLlm llm = new SanchezLlm();

    @Valid
    private final SanchezVoz voz = new SanchezVoz(); // Y

    @Getter
    @Setter
    public static class SanchezLlm { // K
        /** URL del servidor local de Ollama (cliente HTTP, no un servidor propio). */
        @NotBlank
        private String url = "http://localhost:11434";

        @NotBlank
        private String modelo = "qwen2.5:3b";

        @NotBlank
        private String modeloEmbeddings = "nomic-embed-text";

        /** Modelo de vision opcional para "describir la pantalla" (vacio = desactivado). */
        private String modeloVision = "qwen2.5vl:3b"; // Y

        @DecimalMin("0.0")
        @DecimalMax("2.0")
        private double temperatura = 0.3;

        @Min(5)
        private int tiempoEsperaSegundos = 120;

        /** Mensajes que conserva la memoria corta de la conversacion. */
        @Min(2)
        private int mensajesMemoria = 20;
    }

    @Getter
    @Setter
    public static class SanchezVoz {
        @NotNull
        private Path modeloVosk; // K

        /** Ruta del binario de Piper; en Windows se le agrega ".exe" si hace falta. */
        @NotNull
        private Path ejecutablePiper; // Y

        @NotNull
        private Path vozPiper;

        @Min(8000)
        private int frecuenciaMuestreo = 16000;

        /**
         * Energia RMS (muestras de 16 bits) por debajo de la cual se considera silencio.
         * 0 = automatico: se calcula a partir del ruido de fondo de tu microfono (recomendado).
         */
        @Min(0)
        private int umbralSilencio = 0;

        /** Parte del nombre del microfono a usar (ej. "Array"); vacio = el predeterminado de Windows. */
        private String microfono = ""; // Y

        /** Amplificacion maxima que el control automatico de volumen puede aplicar a tu voz. */
        @DecimalMin("1.0")
        @DecimalMax("20.0")
        private double gananciaMaxima = 3.5;

        /** Por debajo de esta confianza (0 a 1) el texto no se envia: se coloca en el cuadro para corregirlo. */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double confianzaMinima = 0.6;

        /** Reconocedor adicional de vocabulario acotado para los comandos que Atlas conoce (mucho mas preciso). */
        private boolean usarComandos = true;

        /**
         * Usa Whisper (whisper.cpp) para las frases libres: mucho mas preciso que Vosk con microfonos de laptop.
         * Es opcional: si no esta instalado, se usa solo Vosk.
         */
        private boolean usarWhisper = true;

        /** Ruta de {@code whisper-cli.exe}. */
        private Path whisperEjecutable;

        /** Ruta del modelo de Whisper ({@code ggml-base.bin}). */
        private Path whisperModelo;

        @Min(1)
        private int whisperHilos = 4;

        /** Guarda la ultima grabacion en {@code ~/.atlas/voz-debug/ultima.wav} para revisar problemas de voz. */
        private boolean guardarAudioDepuracion = false; // Y

        /** Silencio continuo que marca el fin de una frase. */
        @Min(200)
        private int milisegundosSilencio = 1200; // Y

        @Min(2)
        private int segundosMaximosFrase = 15;

        @NotBlank
        private String palabraActivacion = "oye atlas";

        /** Binario de Piper; en Windows agrega ".exe" si la ruta configurada no existe tal cual. */
        public Path ejecutablePiperResuelto() {
            if (ejecutablePiper == null) { // K
                return null;
            } // Y
            boolean sanchezwindows = System.getProperty("os.name", "").toLowerCase().contains("win"); // Y
            if (sanchezwindows && !java.nio.file.Files.isRegularFile(ejecutablePiper)
                    && !ejecutablePiper.getFileName().toString().endsWith(".exe")) {
                return ejecutablePiper.resolveSibling(ejecutablePiper.getFileName() + ".exe");
            }
            return ejecutablePiper;
        } // K
    }

    @Getter
    @Setter
    public static class MarcaOcr {
        /** Carpeta con los archivos *.traineddata de Tesseract. */
        @NotNull
        private Path directorioTessdata;

        @NotBlank
        private String idioma = "spa";
    }
} // Y
