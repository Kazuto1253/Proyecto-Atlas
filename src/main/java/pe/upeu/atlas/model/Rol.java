// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.model;

/** Rol de un usuario; define a qué vistas y acciones tiene acceso. */
public enum Rol { // K
    ESTUDIANTE("Estudiante"),
    DOCENTE("Docente"),
    ADMINISTRADOR("Administrador");

    private final String etiqueta;

    Rol(String etiqueta) {
        this.etiqueta = etiqueta;
    } // K

    public String etiqueta() { // K
        return etiqueta;
    }

    /** Diagnóstico de dependencias: docentes y administradores. */
    public boolean puedeVerDiagnostico() {
        return this != ESTUDIANTE;
    }

    /** Gestión de usuarios: solo administradores. */
    public boolean puedeAdministrarUsuarios() { // Y
        return this == ADMINISTRADOR;
    }

    @Override
    public String toString() { // Y
        return etiqueta;
    }
}
