// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;
import pe.upeu.atlas.ui.SanchezNavegador;

public class MarcaJavaFxApplication extends Application { // Y

    private ConfigurableApplicationContext sanchezcontext;

    @Override
    public void init() { // K
        this.sanchezcontext = new SpringApplicationBuilder(SanchezAtlasApplication.class)
                .web(WebApplicationType.NONE)
                // Spring Boot arranca en modo headless por defecto; sin esto Robot (capturas) no funciona
                .headless(false)
                .run(getParameters().getRaw().toArray(String[]::new));
    }

    @Override
    public void start(Stage marcastage) {
        var sancheztema = sanchezcontext.getBean(SanchezPropiedadesAtlas.class).getTema() == SanchezPropiedadesAtlas.SanchezTema.OSCURO
                ? new PrimerDark() : new PrimerLight();
        Application.setUserAgentStylesheet(sancheztema.getUserAgentStylesheet()); // K
        // Platform.setImplicitExit(false) se activa en la Fase 7, junto con el icono de bandeja:
        // sin bandeja, cerrar la ventana dejaria la app viva e inaccesible.

        // El Navegador crea la escena y muestra primero el acceso (login)
        sanchezcontext.getBean(SanchezNavegador.class).sancheziniciar(marcastage);
    } // Y

    @Override
    public void stop() { // Y
        // Al cerrar el contexto, los beans de voz liberan microfono, audio y procesos
        // externos (Piper) en sus metodos @PreDestroy.
        if (sanchezcontext != null) { // Y
            sanchezcontext.close();
        }
        Platform.exit(); // Y
    }
}
