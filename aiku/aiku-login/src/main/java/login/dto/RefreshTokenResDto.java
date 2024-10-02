package login.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RefreshTokenResDto {
    private String grantType;
    private String accessToken;
    private String refreshToken;
}