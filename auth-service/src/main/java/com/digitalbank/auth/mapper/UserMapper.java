package com.digitalbank.auth.mapper;


import com.digitalbank.auth.dto.response.UserResponse;
import com.digitalbank.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "role", source = "role.name")
    UserResponse toResponse(User user);
}
