package dev.vaultauth.web.config;

import dev.vaultauth.web.filter.AuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationFilter authenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // disable Spring Security's default form login — we handle auth ourselves
            .formLogin(AbstractHttpConfigurer::disable)
            // disable HTTP Basic — same reason
            .httpBasic(AbstractHttpConfigurer::disable)
            // disable CSRF for REST API (SPA sends no form tokens)
            .csrf(AbstractHttpConfigurer::disable)
            // all request-level authorization is handled by our filter + @PreAuthorize
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            // register our custom filter before Spring's default auth filter
            .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // use server-side session — this is intentional for the multi-step flow
            // stateless JWT can't hold intermediate auth state between step 1 and step 2
            .securityContext(ctx -> ctx
                .securityContextRepository(new HttpSessionSecurityContextRepository()));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
