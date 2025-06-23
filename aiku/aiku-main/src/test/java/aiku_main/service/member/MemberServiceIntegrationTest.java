package aiku_main.service.member;

import aiku_main.dto.*;
import aiku_main.dto.member.AuthorityResDto;
import aiku_main.dto.member.AuthorityUpdateDto;
import aiku_main.dto.member.MemberResDto;
import aiku_main.dto.member.TitleMemberResDto;
import aiku_main.exception.TitleException;
import aiku_main.repository.title.TitleRepository;
import common.domain.member.Member;
import common.domain.member.MemberProfileBackground;
import common.domain.member.MemberProfileCharacter;
import common.domain.member.MemberProfileType;
import common.domain.title.Title;
import common.domain.title.TitleCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@SpringBootTest
class MemberServiceIntegrationTest {

    @Autowired
    EntityManager em;

    @Autowired
    MemberService memberService;

    @Autowired
    TitleRepository titleRepository;

    Member member;

    Member member2;

    @BeforeEach
    void init() {
        member = Member.builder()
                .oauthId(123L)
                .nickname("nickname1")
                .password("password1")
                .email("asdasd@gmail.com")
                .build();

        member.updateProfile(MemberProfileType.CHAR, "", MemberProfileCharacter.C01, MemberProfileBackground.PURPLE);

        member.updateAuth(true, true, false, true);

        em.persist(member);

        member2 = Member.builder()
                .oauthId(124L)
                .nickname("nickname2")
                .password("password2")
                .email("asdasd2@gmail.com")
                .build();

        member2.updateProfile(MemberProfileType.CHAR, "", MemberProfileCharacter.C01, MemberProfileBackground.PURPLE);

        member2.updateAuth(true, true, false, true);

        em.persist(member2);
    }

    @Test
    void 멤버_조회_타이틀x_정상() {
        // given
        Title title = Title.create("타이틀1", "타이틀설명", "타이틀설명", TitleCode.POINTS_MORE_THAN_10K);

        em.persist(title);

        // when
        MemberResDto memberDetail = memberService.getMemberDetail(member.getId());

        // then
        assertThat(memberDetail.getMemberProfile().getProfileType())
                .isEqualTo(MemberProfileType.CHAR);
        assertThat(memberDetail.getPoint())
                .isEqualTo(0);
        assertThat(memberDetail.getNickname())
                .isEqualTo("nickname1");
        assertThat(memberDetail.getKakaoId())
                .isEqualTo(123L);
    }

    @Test
    void 멤버_조회_타이틀o_정상() {
        // given
        Title title = Title.create("타이틀1", "타이틀설명", "타이틀설명", TitleCode.POINTS_MORE_THAN_10K);
        title.giveTitleToMember(member);
        em.persist(title);

        Long titleMemberId = titleRepository.findTitleMemberIdByMemberIdAndTitleId(member.getId(), title.getId())
                .orElseThrow();

        member.updateMainTitle(titleMemberId);

        // when
        MemberResDto memberDetail = memberService.getMemberDetail(member.getId());

        // then
        assertThat(memberDetail.getMemberProfile().getProfileType())
                .isEqualTo(MemberProfileType.CHAR);
        assertThat(memberDetail.getPoint())
                .isEqualTo(0);
        assertThat(memberDetail.getNickname())
                .isEqualTo("nickname1");
        assertThat(memberDetail.getKakaoId())
                .isEqualTo(123L);
        assertThat(memberDetail.getTitle().getTitleMemberId())
                .isEqualTo(titleMemberId);
        assertThat(memberDetail.getTitle().getTitleName())
                .isEqualTo("타이틀1");
    }

    @Test
    void 멤버_타이틀_갱신_정상() {
        // given
        Title title = Title.create("타이틀1", "타이틀설명", "타이틀설명", TitleCode.POINTS_MORE_THAN_10K);
        title.giveTitleToMember(member);
        em.persist(title);

        Title titleNew = Title.create("타이틀2", "타이틀설명2", "타이틀설명2", TitleCode.POINTS_MORE_THAN_10K);
        titleNew.giveTitleToMember(member);
        em.persist(titleNew);

        Long titleMemberId = titleRepository.findTitleMemberIdByMemberIdAndTitleId(member.getId(), title.getId())
                .orElseThrow();

        Long titleNewMemberId = titleRepository.findTitleMemberIdByMemberIdAndTitleId(member.getId(), titleNew.getId())
                .orElseThrow();

        member.updateMainTitle(titleMemberId);
        member.updateMainTitle(titleNewMemberId);

        // when
        memberService.updateTitle(member.getId(), titleNew.getId());
        MemberResDto memberDetail = memberService.getMemberDetail(member.getId());

        // then
        assertThat(memberDetail.getTitle().getTitleMemberId())
                .isEqualTo(titleNewMemberId);
        assertThat(memberDetail.getTitle().getTitleName())
                .isEqualTo("타이틀2");
    }

    @Test
    void 멤버_타이틀_갱신_보유x_예외() {
        // given
        Title title = Title.create("타이틀1", "타이틀설명", "타이틀설명", TitleCode.POINTS_MORE_THAN_10K);
        title.giveTitleToMember(member);
        em.persist(title);

        Title titleNew = Title.create("타이틀2", "타이틀설명2", "타이틀설명2", TitleCode.POINTS_MORE_THAN_10K);
        titleNew.giveTitleToMember(member2);
        em.persist(titleNew);

        Long titleMemberId = titleRepository.findTitleMemberIdByMemberIdAndTitleId(member.getId(), title.getId())
                .orElseThrow();

        Long titleNewMemberId = titleRepository.findTitleMemberIdByMemberIdAndTitleId(member2.getId(), titleNew.getId())
                .orElseThrow();

        member.updateMainTitle(titleMemberId);
        member2.updateMainTitle(titleNewMemberId);

        // when
        // then
        org.junit.jupiter.api.Assertions.assertThrows(
                TitleException.class, () -> {
                    memberService.updateTitle(member.getId(), titleNew.getId());
                }
        );
    }

    @Test
    void 멤버_타이틀_리스트_조회() {
        // given
        Title title = Title.create("타이틀1", "타이틀설명", "타이틀설명", TitleCode.POINTS_MORE_THAN_10K);
        title.giveTitleToMember(member);
        em.persist(title);

        Title titleNew = Title.create("타이틀2", "타이틀설명2", "타이틀설명2", TitleCode.POINTS_MORE_THAN_10K);
        titleNew.giveTitleToMember(member);
        em.persist(titleNew);

        Long titleMemberId = titleRepository.findTitleMemberIdByMemberIdAndTitleId(member.getId(), title.getId())
                .orElseThrow();

        Long titleNewMemberId = titleRepository.findTitleMemberIdByMemberIdAndTitleId(member.getId(), titleNew.getId())
                .orElseThrow();

        member.updateMainTitle(titleMemberId);
        member.updateMainTitle(titleNewMemberId);

        // when
        DataResDto<List<TitleMemberResDto>> memberTitles = memberService.getMemberTitles(member.getId());
        List<TitleMemberResDto> resData = memberTitles.getData();

        // then
        assertThat(resData.size())
                .isEqualTo(2);

    }

    @Test
    void 멤버_권한_조회() {
        AuthorityResDto authDetail = memberService.getAuthDetail(member.getId());
        assertThat(authDetail.isPersonalInformationPolicyAgreed())
                .isTrue();
        assertThat(authDetail.isServicePolicyAgreed())
                .isTrue();
        assertThat(authDetail.isMarketingPolicyAgreed())
                .isTrue();
        assertThat(authDetail.isLocationPolicyAgreed())
                .isFalse();
    }

    @Test
    void 멤버_권한_갱신() {
        AuthorityUpdateDto authorityUpdateDto = new AuthorityUpdateDto(false, true, true, false);
        memberService.updateAuth(member.getId(), authorityUpdateDto);

        AuthorityResDto authDetail = memberService.getAuthDetail(member.getId());
        assertThat(authDetail.isPersonalInformationPolicyAgreed())
                .isTrue();
        assertThat(authDetail.isServicePolicyAgreed())
                .isFalse();
        assertThat(authDetail.isMarketingPolicyAgreed())
                .isFalse();
        assertThat(authDetail.isLocationPolicyAgreed())
                .isTrue();
    }
}