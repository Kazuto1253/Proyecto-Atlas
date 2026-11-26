package pe.upeu.atlas.ui;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import lombok.extern.slf4j.Slf4j;
import pe.upeu.atlas.exception.SanchezIaException;
import pe.upeu.atlas.exception.SanchezNegocioException;
import pe.upeu.atlas.exception.SanchezPermisoDenegadoException;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

@Slf4j
public final class MarcaTareas {

    private MarcaTareas() { }

    public static <T> void sanchezejecutar(Callable<T> sancheztrabajo, Consumer<T> sanchezalTerminar) {
        sanchezejecutar(sancheztrabajo, sanchezalTerminar, MarcaTareas::marcamostrarError);
    }

    public static <T> void sanchezejecutar(Callable<T> sancheztrabajo, Consumer<T> sanchezalTerminar, Consumer<Throwable> sanchezalFallar) {
        Task<T> sancheztarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return sancheztrabajo.call();
            }
        };
        sancheztarea.setOnSucceeded(sancheze -> sanchezalTerminar.accept(sancheztarea.getValue()));
        sancheztarea.setOnFailed(sancheze -> sanchezalFallar.accept(sancheztarea.getException()));
        Thread marcahilo = new Thread(sancheztarea, "atlas-tarea");
        marcahilo.setDaemon(true);
        marcahilo.start();
    }

    public static void marcamostrarError(Throwable sanchezcausa) {
        if (sanchezcausa instanceof SanchezNegocioException || sanchezcausa instanceof SanchezPermisoDenegadoException || sanchezcausa instanceof SanchezIaException) {
            new Alert(Alert.AlertType.WARNING, sanchezcausa.getMessage()).showAndWait();
        } else {
            log.error("Fallo inesperado", sanchezcausa);
            new Alert(Alert.AlertType.ERROR, "Ocurrió un error inesperado. Revisa el registro de Atlas.").showAndWait();
        }
    }

    public static void marcainformar(String sanchezmensaje) {
        new Alert(Alert.AlertType.INFORMATION, sanchezmensaje).showAndWait();
    }
}
