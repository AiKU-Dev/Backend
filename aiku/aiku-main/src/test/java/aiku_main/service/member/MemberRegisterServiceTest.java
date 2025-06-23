package aiku_main.service.member;

import aiku_main.dto.member.MemberProfileDto;
import aiku_main.dto.member.MemberRegisterDto;
import aiku_main.dto.member.NicknameExistResDto;
import aiku_main.oauth.KakaoOauthHelper;
import aiku_main.oauth.OauthInfo;
import common.domain.Status;
import common.domain.event.RecommendEvent;
import common.domain.member.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Transactional
@SpringBootTest
class MemberRegisterServiceTest {

    @Autowired
    EntityManager em;

    @MockBean
    KakaoOauthHelper kakaoOauthHelper;
    
    @Autowired
    MemberRegisterService memberRegisterService;

    @Test
    void 멤버_추천인X_정상_회원가입() {
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
        Member member = em.find(Member.class, registerMemberId);

        assertThat(member.getNickname()).isEqualTo("nickname1");
        assertThat(member.getEmail()).isEqualTo("asd@gmail.com");
        assertThat(member.getOauthId()).isEqualTo(123L);
        assertThat(member.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(member.getPoint()).isEqualTo(0);
        assertThat(member.getStatus()).isEqualTo(Status.ALIVE);

        MemberProfile profile = member.getProfile();
        assertThat(profile.getProfileImg()).isBlank();
        assertThat(profile.getProfileType()).isEqualTo(MemberProfileType.CHAR);
        assertThat(profile.getProfileCharacter()).isEqualTo(MemberProfileCharacter.C01);
        assertThat(profile.getProfileBackground()).isEqualTo(MemberProfileBackground.PURPLE);
    }

    @Test
    void 멤버_추천인O_정상_회원가입() {
        // given
        OauthInfo mockOauthInfo = new OauthInfo("123");
        when(kakaoOauthHelper.getOauthInfoByIdToken(any())).thenReturn(mockOauthInfo);

        MemberProfileDto recommenderProfileDto = new MemberProfileDto(MemberProfileType.CHAR, null,
                MemberProfileCharacter.C01, MemberProfileBackground.PURPLE);
        MemberRegisterDto recommenderRegisterDto = new MemberRegisterDto(
                "recommender", "asd@gmail.com", OauthProvider.KAKAO, "rIdToken", recommenderProfileDto,
                true, true, true, true, ""
        );
        Long recommenderId = memberRegisterService.register(recommenderRegisterDto);

        MemberProfileDto memberProfileDto = new MemberProfileDto(MemberProfileType.CHAR, null,
                null, null);
        MemberRegisterDto memberRegisterDto = new MemberRegisterDto(
                "nickname1", "asd@gmail.com", OauthProvider.KAKAO, "mIdToken", memberProfileDto,
                true, true, true, true,
                "recommender"
        );

        // when
        Long registerMemberId = memberRegisterService.register(memberRegisterDto);

        // then
        RecommendEvent recommendEvent = (RecommendEvent) em.createQuery("select c FROM RecommendEvent c WHERE c.member.id = :registerMemberId")
                .setParameter("registerMemberId", registerMemberId)
                .getSingleResult();

        assertThat(recommendEvent.getMember().getId()).isEqualTo(registerMemberId);
        assertThat(recommendEvent.getRecommender().getId()).isEqualTo(recommenderId);
    }

    @Test
    void 닉네임_중복_체크() {
        // given
        OauthInfo mockOauthInfo = new OauthInfo("123");
        when(kakaoOauthHelper.getOauthInfoByIdToken(any())).thenReturn(mockOauthInfo);

        MemberProfileDto member1ProfileDto = new MemberProfileDto(MemberProfileType.CHAR, null,
                MemberProfileCharacter.C01, MemberProfileBackground.PURPLE);
        MemberRegisterDto member1RegisterDto = new MemberRegisterDto(
                "member1", "asd@gmail.com", OauthProvider.KAKAO, "rIdToken", member1ProfileDto,
                true, true, true, true, ""
        );
        memberRegisterService.register(member1RegisterDto);

        // when
        NicknameExistResDto member1ExistResDto = memberRegisterService.checkNickname("member1");
        Boolean member1Exist = member1ExistResDto.getExist();

        NicknameExistResDto member2ExistResDto = memberRegisterService.checkNickname("member2");
        Boolean member2Exist = member2ExistResDto.getExist();

        // then
        assertThat(member1Exist).isTrue();
        assertThat(member2Exist).isFalse();
    }
}