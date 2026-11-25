// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seguridad;

/** Pide al usuario que confirme una acción delicada antes de ejecutarla. */
public interface SanchezConfirmadorAcciones {

    /** @return {@code true} solo si el usuario aceptó explícitamente. */
    boolean sanchezconfirmar(String sancheztitulo, String sanchezmensaje);
} // K
