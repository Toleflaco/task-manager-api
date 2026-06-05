package com.mtole.taskmanager.auth;

import com.mtole.taskmanager.security.JwtService;
import com.mtole.taskmanager.users.User;
import com.mtole.taskmanager.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {

        log.info("Login attempt for email={}", request.email());
        // 1. Delegar validación de credenciales en AuthenticationManager.
        //    Si las credenciales son malas, lanza BadCredentialsException
        //    (que el GlobalExceptionHandler convertirá en 401).
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(),request.password()));

        // 2. Si llegamos aquí, las credenciales son válidas.
        //    Recupero el User del repo para obtener el userId (necesario para el JWT).

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(()-> new IllegalStateException("User authenticated but not found in repository — inconsistent state"));

        // 3. Generar el JWT con el userId del user autenticado.
        String token = jwtService.generateToken(user.getId());
        log.info("Login successful for userId={}", user.getId());
        return new LoginResponse(token);
    }
}
