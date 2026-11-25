// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

/** Pide al usuario que confirme una acción delicada antes de ejecutarla. */
public interface ConfirmadorAcciones {

    /** @return {@code true} solo si el usuario aceptó explícitamente. */
    boolean confirmar(String titulo, String mensaje);
} // K
