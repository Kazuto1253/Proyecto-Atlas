// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * Configuracion general: activa las propiedades de Atlas, la programacion de tareas
 * (recordatorios) y garantiza que existan las carpetas de datos del usuario.
 */
@Slf4j
@Configuration
@EnableScheduling
@EnableConfigurationProperties(SanchezPropiedadesAtlas.class)
@RequiredArgsConstructor
public class MarcaConfiguracionAplicacion {

    private final SanchezPropiedadesAtlas marcapropiedades;

    @EventListener(ApplicationStartedEvent.class)
    public void marcaprepararDirectorios() {
        try {
            Files.createDirectories(marcapropiedades.getDirectorioBase()); // K
            Files.createDirectories(marcapropiedades.getDirectorioModelos());
            Files.createDirectories(marcapropiedades.getDirectorioBase().resolve("logs")); // Y
            Files.createDirectories(marcapropiedades.getDirectorioBase().resolve("documentos")); // Y
            Files.createDirectories(marcapropiedades.getCarpetaCapturas());
        } catch (IOException sancheze) { // K
            throw new UncheckedIOException("No se pudieron crear las carpetas de Atlas", sancheze); // Y
        }
        log.info("Carpeta de datos: {}", marcapropiedades.getDirectorioBase());
        log.info("Modelo LLM: {} en {}", marcapropiedades.getLlm().getModelo(), marcapropiedades.getLlm().getUrl());
        log.info("Capturas: {} | Solo lectura: {}", marcapropiedades.getCarpetaCapturas(), marcapropiedades.isModoSoloLectura());
    } // K
}
