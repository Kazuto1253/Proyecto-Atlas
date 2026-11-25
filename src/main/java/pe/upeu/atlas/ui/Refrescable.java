// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ui;

/**
 * Vista que recarga sus datos cada vez que se muestra desde el menu.
 * Se llama en el hilo de JavaFX: el trabajo pesado debe ir a un Task.
 */
public interface Refrescable {
    void refrescar();
} // K
