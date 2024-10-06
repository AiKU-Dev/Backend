package aiku_main.controller;

import aiku_main.dto.RefreshTokenResDto;
import aiku_main.dto.SignInDto;
import aiku_main.dto.SignInTokenResDto;
import aiku_main.service.LoginService;
import common.domain.member.Member;
import common.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/login")
@RequiredArgsConstructor
@RestController
public class LoginController {

    private final LoginService loginService;

    @PostMapping("/sign-in")
    public BaseResponse<SignInTokenResDto> signIn(@RequestBody @Valid SignInDto signInDto){
        SignInTokenResDto signInTokenResDto = loginService.signIn(signInDto.getIdToken());

        return new BaseResponse<>(signInTokenResDto);
    }

    @PostMapping("/refresh")
    public BaseResponse<RefreshTokenResDto> refreshToken(
            @RequestBody Member member,
            @CookieValue("refreshToken") String refreshToken){
        RefreshTokenResDto refreshTokenResDto = loginService.refreshToken(member, refreshToken);

        return new BaseResponse<>(refreshTokenResDto);
    }
}