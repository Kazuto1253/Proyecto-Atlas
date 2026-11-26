package pe.upeu.atlas.model;

public enum SanchezRol {
    ESTUDIANTE("Estudiante"),
    DOCENTE("Docente"),
    ADMINISTRADOR("Administrador");

    private final String etiqueta;

    SanchezRol(String marcaetiqueta) {
        this.etiqueta = marcaetiqueta;
    }

    public String marcaetiqueta() {
        return etiqueta;
    }

    public boolean marcapuedeVerDiagnostico() {
        return this != ESTUDIANTE;
    }

    public boolean marcapuedeAdministrarUsuarios() {
        return this == ADMINISTRADOR;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
