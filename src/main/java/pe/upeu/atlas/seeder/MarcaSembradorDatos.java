package pe.upeu.atlas.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.model.SanchezRol;
import pe.upeu.atlas.model.MarcaUsuario;
import pe.upeu.atlas.repository.MarcaUsuarioRepository;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
public class MarcaSembradorDatos implements ApplicationRunner {

    private final MarcaUsuarioRepository sanchezusuarios;
    private final PasswordEncoder sanchezcodificador;

    @Override
    public void run(ApplicationArguments sanchezargs) {
        if (sanchezusuarios.count() > 0) {
            return;
        }
        sanchezcrear("Administrador de Atlas", "admin", "Admin123", SanchezRol.ADMINISTRADOR);
        sanchezcrear("Docente de prueba", "docente", "Docente123", SanchezRol.DOCENTE);
        sanchezcrear("Estudiante de prueba", "estudiante", "Estudiante123", SanchezRol.ESTUDIANTE);
        log.info("Se crearon 3 cuentas de demostración (admin, docente, estudiante). Cambia sus contraseñas.");
    }

    private void sanchezcrear(String sancheznombre, String marcausuario, String sanchezclave, SanchezRol sanchezrol) {
        MarcaUsuario sanchezu = new MarcaUsuario();
        sanchezu.setNombreCompleto(sancheznombre);
        sanchezu.setUsuario(marcausuario);
        sanchezu.setContrasenaHash(sanchezcodificador.encode(sanchezclave));
        sanchezu.setRol(sanchezrol);
        sanchezusuarios.save(sanchezu);
    }
}
