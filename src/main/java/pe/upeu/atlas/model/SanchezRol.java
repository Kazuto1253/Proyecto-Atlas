// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.model;

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

    public boolean marcapuedeVerDiagnostico() {
        return this != ESTUDIANTE;
    }

    public boolean marcapuedeAdministrarUsuarios() { // Y
        return this == ADMINISTRADOR;
    }

    @Override
    public String toString() { // Y
        return etiqueta;
    }
}
