// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Cifrado de contraseñas: solo spring-security-crypto (BCrypt), sin Spring Security web. */
@Configuration
public class SanchezConfiguracionSeguridad {

    @Bean
    public PasswordEncoder marcacodificadorContrasenas() {
        return new BCryptPasswordEncoder(10);
    }
}
