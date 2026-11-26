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
            Files.createDirectories(marcapropiedades.getDirectorioBase());
            Files.createDirectories(marcapropiedades.getDirectorioModelos());
            Files.createDirectories(marcapropiedades.getDirectorioBase().resolve("logs"));
            Files.createDirectories(marcapropiedades.getDirectorioBase().resolve("documentos"));
            Files.createDirectories(marcapropiedades.getCarpetaCapturas());
        } catch (IOException sancheze) {
            throw new UncheckedIOException("No se pudieron crear las carpetas de Atlas", sancheze);
        }
        log.info("Carpeta de datos: {}", marcapropiedades.getDirectorioBase());
        log.info("Modelo LLM: {} en {}", marcapropiedades.getLlm().getModelo(), marcapropiedades.getLlm().getUrl());
        log.info("Capturas: {} | Solo lectura: {}", marcapropiedades.getCarpetaCapturas(), marcapropiedades.isModoSoloLectura());
    }
}
