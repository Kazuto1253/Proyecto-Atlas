// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ui;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import lombok.extern.slf4j.Slf4j;
import pe.upeu.atlas.exception.SanchezIaException;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Ejecuta trabajo pesado fuera del hilo de JavaFX y devuelve el resultado (o el error) a la interfaz. */
@Slf4j
public final class MarcaTareas {

    private MarcaTareas() { } // K

    public static <T> void sanchezejecutar(Callable<T> sancheztrabajo, Consumer<T> sanchezalTerminar) {
        sanchezejecutar(sancheztrabajo, sanchezalTerminar, MarcaTareas::marcamostrarError);
    }

    public static <T> void sanchezejecutar(Callable<T> sancheztrabajo, Consumer<T> sanchezalTerminar, Consumer<Throwable> sanchezalFallar) {
        Task<T> sancheztarea = new Task<>() { // Y
            @Override
            protected T call() throws Exception {
                return sancheztrabajo.call();
            } // K
        };
        sancheztarea.setOnSucceeded(sancheze -> sanchezalTerminar.accept(sancheztarea.getValue()));
        sancheztarea.setOnFailed(sancheze -> sanchezalFallar.accept(sancheztarea.getException()));
        Thread marcahilo = new Thread(sancheztarea, "atlas-tarea");
        marcahilo.setDaemon(true);
        marcahilo.start();
    } // K

    /** Avisos entendibles para errores esperados; el resto se registra y se muestra de forma genérica. */
    public static void marcamostrarError(Throwable sanchezcausa) { // K
        if (sanchezcausa instanceof SanchezNegocioException || sanchezcausa instanceof SanchezPermisoDenegadoException || sanchezcausa instanceof SanchezIaException) { // Y
            new Alert(Alert.AlertType.WARNING, sanchezcausa.getMessage()).showAndWait(); // Y
        } else {
            log.error("Fallo inesperado", sanchezcausa);
            new Alert(Alert.AlertType.ERROR, "Ocurrió un error inesperado. Revisa el registro de Atlas.").showAndWait(); // K
        } // K
    }

    public static void marcainformar(String sanchezmensaje) {
        new Alert(Alert.AlertType.INFORMATION, sanchezmensaje).showAndWait(); // K
    }
}
