package com.mtole.taskmanager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // Leer el header Authorization de forma null-safe
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        // Extraer el token del header
        String token = authHeader.substring(7);
        //  Parsear el token dentro de try-catch
        try {
            Claims claims = jwtService.parseClaims(token);
            Long userId = Long.parseLong(claims.getSubject());

            // Construir el Authentication y ponerlo en el contexto
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        } catch (JwtException | IllegalArgumentException ex){
            // token inválido (firma mala, expirado, formato corrupto, sub no es un número)
            // NO autenticamos, simplemente seguimos la cadena sin SecurityContext
        }
        // Continuar la cadena al final
        filterChain.doFilter(request, response);
    }
}
