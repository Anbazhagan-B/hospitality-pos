package com.pos.employee.service;

import com.pos.common.exception.BadRequestException;
import com.pos.common.security.JwtTokenProvider;
import com.pos.employee.dto.EmployeeDto;
import com.pos.employee.dto.LoginRequest;
import com.pos.employee.dto.LoginResponse;
import com.pos.employee.entity.Employee;
import com.pos.employee.mapper.EmployeeMapper;
import com.pos.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmployeeMapper employeeMapper;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());

        Employee employee = employeeRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!employee.isActive()) {
            throw new BadRequestException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), employee.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        List<String> roles = employee.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toList());

        String accessToken = tokenProvider.generateToken(employee.getUsername(), employee.getId(), roles);
        String refreshToken = tokenProvider.generateRefreshToken(employee.getUsername());

        log.info("Login successful for user: {}", request.getUsername());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration / 1000)
                .employee(employeeMapper.toDto(employee))
                .build();
    }

    public LoginResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        String username = tokenProvider.getUsernameFromToken(refreshToken);
        Employee employee = employeeRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!employee.isActive()) {
            throw new BadRequestException("Account is disabled");
        }

        List<String> roles = employee.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toList());

        String newAccessToken = tokenProvider.generateToken(employee.getUsername(), employee.getId(), roles);
        String newRefreshToken = tokenProvider.generateRefreshToken(employee.getUsername());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration / 1000)
                .employee(employeeMapper.toDto(employee))
                .build();
    }
}
