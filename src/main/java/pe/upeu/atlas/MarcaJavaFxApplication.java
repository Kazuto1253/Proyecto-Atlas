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
                .headless(false)
                .run(getParameters().getRaw().toArray(String[]::new));
    }

    @Override
    public void start(Stage marcastage) {
        var sancheztema = sanchezcontext.getBean(SanchezPropiedadesAtlas.class).getTema() == SanchezPropiedadesAtlas.SanchezTema.OSCURO
                ? new PrimerDark() : new PrimerLight();
        Application.setUserAgentStylesheet(sancheztema.getUserAgentStylesheet()); // K

        sanchezcontext.getBean(SanchezNavegador.class).sancheziniciar(marcastage);
    } // Y

    @Override
    public void stop() { // Y
        if (sanchezcontext != null) { // Y
            sanchezcontext.close();
        }
        Platform.exit(); // Y
    }
}
