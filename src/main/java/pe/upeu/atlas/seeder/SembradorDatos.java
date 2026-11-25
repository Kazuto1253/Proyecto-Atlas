// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.upeu.atlas.model.Rol;
import pe.upeu.atlas.model.Usuario;
import pe.upeu.atlas.repository.UsuarioRepository;

/**
 * Crea cuentas de demostración la primera vez (solo si no existe ningún usuario).
 * Se desactiva con {@code app.seeder.enabled=false}. Las credenciales están en docs/MANUAL_DE_USO.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
public class SembradorDatos implements ApplicationRunner { // Y

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;

    @Override
    public void run(ApplicationArguments args) { // Y
        if (usuarios.count() > 0) {
            return;
        }
        crear("Administrador de Atlas", "admin", "Admin123", Rol.ADMINISTRADOR); // K
        crear("Docente de prueba", "docente", "Docente123", Rol.DOCENTE);
        crear("Estudiante de prueba", "estudiante", "Estudiante123", Rol.ESTUDIANTE);
        log.info("Se crearon 3 cuentas de demostración (admin, docente, estudiante). Cambia sus contraseñas."); // Y
    } // K

    private void crear(String nombre, String usuario, String clave, Rol rol) { // Y
        Usuario u = new Usuario();
        u.setNombreCompleto(nombre);
        u.setUsuario(usuario); // K
        u.setContrasenaHash(codificador.encode(clave));
        u.setRol(rol); // Y
        usuarios.save(u);
    }
} // Y
