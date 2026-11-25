// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ui;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import lombok.extern.slf4j.Slf4j;
import pe.upeu.atlas.exception.IaException;
import pe.upeu.atlas.exception.NegocioException;
import pe.upeu.atlas.exception.PermisoDenegadoException;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Ejecuta trabajo pesado fuera del hilo de JavaFX y devuelve el resultado (o el error) a la interfaz. */
@Slf4j
public final class Tareas {

    private Tareas() { } // K

    public static <T> void ejecutar(Callable<T> trabajo, Consumer<T> alTerminar) {
        ejecutar(trabajo, alTerminar, Tareas::mostrarError);
    }

    public static <T> void ejecutar(Callable<T> trabajo, Consumer<T> alTerminar, Consumer<Throwable> alFallar) {
        Task<T> tarea = new Task<>() { // Y
            @Override
            protected T call() throws Exception {
                return trabajo.call();
            } // K
        };
        tarea.setOnSucceeded(e -> alTerminar.accept(tarea.getValue()));
        tarea.setOnFailed(e -> alFallar.accept(tarea.getException()));
        Thread hilo = new Thread(tarea, "atlas-tarea");
        hilo.setDaemon(true);
        hilo.start();
    } // K

    /** Avisos entendibles para errores esperados; el resto se registra y se muestra de forma genérica. */
    public static void mostrarError(Throwable causa) { // K
        if (causa instanceof NegocioException || causa instanceof PermisoDenegadoException || causa instanceof IaException) { // Y
            new Alert(Alert.AlertType.WARNING, causa.getMessage()).showAndWait(); // Y
        } else {
            log.error("Fallo inesperado", causa);
            new Alert(Alert.AlertType.ERROR, "Ocurrió un error inesperado. Revisa el registro de Atlas.").showAndWait(); // K
        } // K
    }

    public static void informar(String mensaje) {
        new Alert(Alert.AlertType.INFORMATION, mensaje).showAndWait(); // K
    }
}
