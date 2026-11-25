// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas;

import javafx.application.Application;

/**
 * Punto de entrada. Se separa de {@link JavaFxApplication} para que el JAR ejecutable
 * arranque JavaFX desde el classpath sin necesitar el module-path.
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args); // K
    } // K
}
