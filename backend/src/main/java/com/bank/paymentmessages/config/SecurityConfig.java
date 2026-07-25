package com.bank.paymentmessages.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Ferme l'API : tout {@code /api/v1/**} exige un jeton, à l'exception de l'authentification.
 * <p>
 * Le service est à la fois émetteur et vérificateur des jetons (HMAC-SHA256, secret partagé
 * par {@code app.security.jwt.secret}) : aucun serveur d'autorisation externe n'est requis.
 * Le remplacement par un vrai fournisseur OAuth2 se limiterait à substituer le
 * {@link JwtDecoder} par {@code NimbusJwtDecoder.withJwkSetUri(...)} et à retirer l'émission.
 * <p>
 * La session est sans état : ni cookie de session, ni CSRF (rien à protéger sans cookie
 * d'authentification, le jeton étant porté explicitement par l'appelant).
 * <p>
 * Droits : la lecture et le rejeu unitaire sont ouverts à tout compte authentifié ; les
 * opérations destructives ou massives (suppression, forçage de statut, rejeu global) sont
 * réservées au rôle {@code ADMIN}.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    /** Longueur minimale imposée par HS256. */
    private static final int MIN_SECRET_LENGTH = 32;

    private static final String[] DOC_PATHS = {
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      SecurityProperties properties,
                                                      JsonMapper jsonMapper) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();
                    // Sondes de supervision : nécessaires à l'orchestrateur avant tout jeton.
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    if (properties.publicDocs()) {
                        auth.requestMatchers(DOC_PATHS).permitAll();
                    }
                    auth.requestMatchers(HttpMethod.DELETE, "/api/v1/messages/**").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.PUT, "/api/v1/messages/*/status").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/messages/batch/retry-failed").hasRole("ADMIN");
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeProblem(response, jsonMapper, HttpStatus.UNAUTHORIZED, "unauthorized",
                                        "Authentification requise", "Jeton absent, expiré ou invalide"))
                        .accessDeniedHandler(accessDeniedHandler(jsonMapper)));

        return http.build();
    }

    /**
     * Le décodeur valide la signature HMAC des jetons émis par {@code TokenService}.
     * Un secret trop court est refusé au démarrage plutôt qu'à la première requête.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecurityProperties properties) {
        return NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    /**
     * Les rôles voyagent dans une revendication {@code roles} (et non {@code scope}) :
     * ce sont des rôles applicatifs, pas des portées OAuth2 négociées.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Délégant : le préfixe du mot de passe configuré porte l'algorithme ({bcrypt}, {noop}…),
        // ce qui permet de migrer d'algorithme sans redéployer de code.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(SecurityProperties properties) {
        List<UserDetails> users = properties.users().stream()
                .map(u -> (UserDetails) User.withUsername(u.username())
                        .password(u.password())
                        .roles(u.roles().toArray(String[]::new))
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                      PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    private AccessDeniedHandler accessDeniedHandler(JsonMapper jsonMapper) {
        return (request, response, ex) ->
                writeProblem(response, jsonMapper, HttpStatus.FORBIDDEN, "forbidden",
                        "Accès refusé", "Cette opération requiert un rôle supplémentaire");
    }

    private CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private SecretKey secretKey(SecurityProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.security.jwt.secret doit faire au moins " + MIN_SECRET_LENGTH + " octets (HS256)");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * Les refus de la chaîne de filtres surviennent avant tout contrôleur : le
     * {@code GlobalExceptionHandler} ne les voit pas. Ils sont formatés ici dans le même
     * format {@code application/problem+json} que le reste de l'API.
     *
     * @see HttpStatusEntryPoint pour le comportement par défaut, sans corps de réponse
     */
    private static void writeProblem(HttpServletResponse response, JsonMapper jsonMapper,
                                     HttpStatus status, String type, String title, String detail)
            throws IOException {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:payment-messages:" + type));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(problem));
    }
}
