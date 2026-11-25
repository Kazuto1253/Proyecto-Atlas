// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.model;

/** Rol de un usuario; define a qué vistas y acciones tiene acceso. */
public enum SanchezRol { // K
    ESTUDIANTE("Estudiante"),
    DOCENTE("Docente"),
    ADMINISTRADOR("Administrador");

    private final String etiqueta;

    SanchezRol(String marcaetiqueta) {
        this.etiqueta = marcaetiqueta;
    } // K

    public String marcaetiqueta() { // K
        return etiqueta;
    }

    /** Diagnóstico de dependencias: docentes y administradores. */
    public boolean marcapuedeVerDiagnostico() {
        return this != ESTUDIANTE;
    }

    /** Gestión de usuarios: solo administradores. */
    public boolean marcapuedeAdministrarUsuarios() { // Y
        return this == ADMINISTRADOR;
    }

    @Override
    public String toString() { // Y
        return etiqueta;
    }
}
