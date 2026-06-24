package com.mtole.taskmanager.auth;

import com.mtole.taskmanager.security.JwtProperties;
import com.mtole.taskmanager.security.JwtService;
import com.mtole.taskmanager.users.User;
import com.mtole.taskmanager.users.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static com.mtole.taskmanager.users.UserTestDataBuilder.aUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;


    @Test
    @DisplayName("login with valid credentials returns LoginResponse with tokens")
    void login_withValidCredentials_returnsLoginResponse() {

        // Arrange
        // ...
        User existingUser = aUser().withId(1L).withEmail("alice@test.com").build();
        given(userRepository.findByEmail("alice@test.com")).willReturn(Optional.of(existingUser));
        given(jwtProperties.refreshExpiration()).willReturn(Duration.ofDays(7));
        given(jwtProperties.accessExpiration()).willReturn(Duration.ofMinutes(15));
        given(jwtService.generateAccessToken(1L)).willReturn("fake-jwt-access-token");
        //given(refreshTokenRepository.save()).willReturn()

        // Act
        LoginResponse response = authService.login(new LoginRequest("alice@test.com","password-hardcored"));

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("fake-jwt-access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.refreshToken()).isNotNull().isNotBlank();

        then(authenticationManager).should().authenticate(any(UsernamePasswordAuthenticationToken.class));
        then(refreshTokenRepository).should().save(any(RefreshToken.class));
        then(jwtService).should().generateAccessToken(1L);

    }

}
