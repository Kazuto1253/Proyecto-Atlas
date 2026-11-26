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

public class MarcaJavaFxApplication extends Application {

    private ConfigurableApplicationContext sanchezcontext;

    @Override
    public void init() {
        this.sanchezcontext = new SpringApplicationBuilder(SanchezAtlasApplication.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run(getParameters().getRaw().toArray(String[]::new));
    }

    @Override
    public void start(Stage marcastage) {
        var sancheztema = sanchezcontext.getBean(SanchezPropiedadesAtlas.class).getTema() == SanchezPropiedadesAtlas.SanchezTema.OSCURO
                ? new PrimerDark() : new PrimerLight();
        Application.setUserAgentStylesheet(sancheztema.getUserAgentStylesheet());

        sanchezcontext.getBean(SanchezNavegador.class).sancheziniciar(marcastage);
    }

    @Override
    public void stop() {
        if (sanchezcontext != null) {
            sanchezcontext.close();
        }
        Platform.exit();
    }
}
