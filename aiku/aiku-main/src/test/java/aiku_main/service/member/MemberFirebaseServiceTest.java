package aiku_main.service.member;

import aiku_main.dto.FirebaseTokenDto;
import aiku_main.exception.MemberNotFoundException;
import aiku_main.repository.member.MemberRepository;
import common.domain.member.Member;
import common.domain.member.MemberProfileBackground;
import common.domain.member.MemberProfileCharacter;
import common.domain.member.MemberProfileType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@SpringBootTest
class MemberFirebaseServiceTest {

    @Autowired
    EntityManager em;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberFirebaseService memberFirebaseService;

    Member member;

    FirebaseTokenDto originalFcm;
    FirebaseTokenDto newFcm;

    @BeforeEach
    void setUp() {
        member = Member.builder()
                .oauthId(123L)
                .nickname("nickname1")
                .password("password1")
                .email("asdasd@gmail.com")
                .build();

        member.updateProfile(MemberProfileType.CHAR, "", MemberProfileCharacter.C01, MemberProfileBackground.PURPLE);

        member.updateAuth(true, true, false, true);

        em.persist(member);

        originalFcm = new FirebaseTokenDto("fcmToken1");
        newFcm = new FirebaseTokenDto("fcmToken2");
    }

    @Test
    void 멤버x_토큰저장_예외() {
        Long id = member.getId() + 1;
        Assertions.assertThrows(MemberNotFoundException.class, () -> {
            memberFirebaseService.saveToken(id, originalFcm);
        });
    }

    @Test
    void 멤버x_토큰수정_예외() {
        Long id = member.getId() + 1;
        Assertions.assertThrows(MemberNotFoundException.class, () -> {
            memberFirebaseService.saveToken(id, originalFcm);
        });
    }

    @Test
    void 토큰x_토큰저장_정상() {
        memberFirebaseService.saveToken(member.getId(), originalFcm);

        assertThat(member.getFirebaseToken()).isEqualTo("fcmToken1");
    }

//    @Test
//    void 토큰o_토큰저장_예외() {
//        memberFirebaseService.saveToken(member.getId(), originalFcm);
//
//        Assertions.assertThrows(FcmException.class, () -> {
//            memberFirebaseService.saveToken(member.getId(), newFcm);
//        });
//    }

    @Test
    void 토큰o_토큰수정_정상() {
        memberFirebaseService.saveToken(member.getId(), originalFcm);
        memberFirebaseService.saveToken(member.getId(), newFcm);

        assertThat(member.getFirebaseToken()).isEqualTo("fcmToken2");
    }

//    @Test
//    void 토큰x_토큰수정_예() {
//        Assertions.assertThrows(FcmException.class, () -> {
//            memberFirebaseService.updateToken(member.getId(), newFcm);
//        });
//    }
}