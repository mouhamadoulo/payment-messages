package com.bank.paymentmessages.service;

import com.bank.paymentmessages.config.SecurityProperties;
import com.bank.paymentmessages.dto.api.LoginResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Émet les jetons d'accès à partir d'une authentification réussie.
 * <p>
 * Les rôles sont portés par la revendication {@code roles}, décodée en autorités par le
 * convertisseur du serveur de ressources ({@code SecurityConfig}). Le préfixe
 * {@code ROLE_} est retiré à l'émission et remis à la vérification : le jeton reste
 * lisible et indépendant des conventions internes de Spring Security.
 */
@Service
public class TokenService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtEncoder encoder;
    private final SecurityProperties.Jwt properties;

    public TokenService(JwtEncoder encoder, SecurityProperties properties) {
        this.encoder = encoder;
        this.properties = properties.jwt();
    }

    public LoginResponse issue(Authentication authentication) {

        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.expiration());

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.startsWith(ROLE_PREFIX)
                        ? authority.substring(ROLE_PREFIX.length())
                        : authority)
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                .claim("roles", roles)
                .build();

        String token = encoder.encode(
                        JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        return new LoginResponse(token, "Bearer", properties.expiration().toSeconds(),
                authentication.getName(), roles);
    }
}
