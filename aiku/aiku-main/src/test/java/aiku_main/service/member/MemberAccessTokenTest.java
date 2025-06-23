package aiku_main.service.member;

import aiku_main.dto.member.MemberProfileDto;
import aiku_main.dto.member.MemberRegisterDto;
import aiku_main.dto.member.login.SignInTokenResDto;
import aiku_main.filter.security.JwtToken;
import aiku_main.filter.security.JwtTokenProvider;
import aiku_main.oauth.KakaoOauthHelper;
import aiku_main.oauth.OauthInfo;
import common.domain.member.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Transactional
@SpringBootTest
public class MemberAccessTokenTest {
    @Autowired
    EntityManager em;

    @MockBean
    KakaoOauthHelper kakaoOauthHelper;

    @Autowired
    MemberRegisterService memberRegisterService;

    @Test
    void 멤버_추천인X_토큰_생성() {
        // given
        OauthInfo mockOauthInfo = new OauthInfo("123");
        when(kakaoOauthHelper.getOauthInfoByIdToken(any())).thenReturn(mockOauthInfo);

        MemberProfileDto memberProfileDto = new MemberProfileDto(MemberProfileType.CHAR, null,
                MemberProfileCharacter.C01, MemberProfileBackground.PURPLE);
        MemberRegisterDto memberRegisterDto = new MemberRegisterDto(
                "nickname1", "asd@gmail.com", OauthProvider.KAKAO, "idToken", memberProfileDto,
                true, true, true, true, ""
        );

        // when
        Long registerMemberId = memberRegisterService.register(memberRegisterDto);

        // then
        System.out.println(registerMemberId);
        SignInTokenResDto signInTokenResDto = signInWithoutOIDC(registerMemberId);

        System.out.println(signInTokenResDto.getAccessToken());
    }

    @Autowired
    JwtTokenProvider jwtTokenProvider;
    @Autowired
    AuthenticationManagerBuilder authenticationManagerBuilder;

    @Transactional
    public SignInTokenResDto signInWithoutOIDC(Long memberId) {
        Member member = em.find(Member.class, memberId);

        UsernamePasswordAuthenticationToken authenticationFilter
                = new UsernamePasswordAuthenticationToken(member.getId(), member.getOauthId());

        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationFilter);

        // JWT Token 발급
        JwtToken jwtToken = jwtTokenProvider.generateToken(authentication, OauthProvider.APPLE);

        member.reissueRefreshToken(jwtToken.getRefreshToken());

        return SignInTokenResDto.toDto(jwtToken);
    }
}
