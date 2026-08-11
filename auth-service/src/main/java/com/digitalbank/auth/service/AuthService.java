package com.digitalbank.auth.service;


import com.digitalbank.auth.dto.request.RegisterRequest;
import com.digitalbank.auth.dto.response.UserResponse;
import com.digitalbank.auth.entity.Role;
import com.digitalbank.auth.entity.User;
import com.digitalbank.auth.enums.UserStatus;
import com.digitalbank.auth.exception.EmailAlreadyExistsException;
import com.digitalbank.auth.exception.PhoneNumberAlreadyExistsException;
import com.digitalbank.auth.exception.RoleNotFoundException;
import com.digitalbank.auth.mapper.UserMapper;
import com.digitalbank.auth.repository.RoleRepository;
import com.digitalbank.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new PhoneNumberAlreadyExistsException(request.getPhoneNumber());
        }

        Role role = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new RoleNotFoundException("CUSTOMER"));

        User user = User.builder()
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.PENDING)
                .role(role)
                .build();

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

}
