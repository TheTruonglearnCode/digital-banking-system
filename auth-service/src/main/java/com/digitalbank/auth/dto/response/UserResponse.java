package com.digitalbank.auth.dto.response;


import com.digitalbank.auth.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {
    private Long id;

    private String email;

    private String phoneNumber;

    private String fullName;

    private UserStatus status;

    private String role;

    //Không có private String passwordHash -> không trả password/passwordHash về client
}
