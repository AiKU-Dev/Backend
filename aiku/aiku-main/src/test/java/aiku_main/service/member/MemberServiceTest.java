package aiku_main.service.member;

import aiku_main.application_event.event.PointChangeType;
import aiku_main.dto.*;
import aiku_main.dto.member.*;
import aiku_main.exception.MemberNotFoundException;
import aiku_main.exception.TitleException;
import aiku_main.repository.member.MemberRepository;
import aiku_main.repository.title.TitleRepository;
import aiku_main.s3.S3ImageProvider;
import common.domain.member.*;
import common.domain.value_reference.TitleMemberValue;
import common.exception.NotEnoughPointException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MemberServiceTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    TitleRepository titleRepository;
    @Mock
    S3ImageProvider s3ImageProvider;
    @InjectMocks
    MemberService memberService;

    private final Long MEMBER_ID = 1L;
    private final Long TITLE_ID = 10L;
    private final Long TITLE_MEMBER_ID = 100L;
    private final String NICKNAME = "testNickname";
    private final Long OAUTH_ID = 12345L;
    private final int POINT_AMOUNT = 1000;
    private final String PROFILE_IMG_URL = "https://test.com/image.jpg";

    @Test
    void getMemberDetail_타이틀있음_정상() {
        // Given
        Member member = mock(Member.class);
        MemberProfile profile = mock(MemberProfile.class);
        TitleMemberValue titleMemberValue = mock(TitleMemberValue.class);
        TitleMemberResDto titleMemberResDto = new TitleMemberResDto(TITLE_MEMBER_ID, "Test Title", "Test Description", "https://test.com/title.jpg");
        
        given(member.getId()).willReturn(MEMBER_ID);
        given(member.getNickname()).willReturn(NICKNAME);
        given(member.getOauthId()).willReturn(OAUTH_ID);
        given(member.getPoint()).willReturn(POINT_AMOUNT);
        given(member.getProfile()).willReturn(profile);
        given(member.getMainTitle()).willReturn(titleMemberValue);
        
        given(profile.getProfileType()).willReturn(MemberProfileType.CHAR);
        given(profile.getProfileImg()).willReturn("");
        given(profile.getProfileCharacter()).willReturn(MemberProfileCharacter.C01);
        given(profile.getProfileBackground()).willReturn(MemberProfileBackground.PURPLE);
        
        given(titleMemberValue.getId()).willReturn(TITLE_MEMBER_ID);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(titleRepository.getTitleMemberResDtoByTitleId(TITLE_MEMBER_ID)).willReturn(titleMemberResDto);

        // When
        MemberResDto result = memberService.getMemberDetail(MEMBER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(result.getNickname()).isEqualTo(NICKNAME);
        assertThat(result.getKakaoId()).isEqualTo(OAUTH_ID);
        assertThat(result.getTitle()).isEqualTo(titleMemberResDto);
        assertThat(result.getPoint()).isEqualTo(POINT_AMOUNT);
    }

    @Test
    void getMemberDetail_타이틀없음_정상() {
        // Given
        Member member = mock(Member.class);
        MemberProfile profile = mock(MemberProfile.class);
        
        given(member.getId()).willReturn(MEMBER_ID);
        given(member.getNickname()).willReturn(NICKNAME);
        given(member.getOauthId()).willReturn(OAUTH_ID);
        given(member.getPoint()).willReturn(POINT_AMOUNT);
        given(member.getProfile()).willReturn(profile);
        given(member.getMainTitle()).willReturn(null);
        
        given(profile.getProfileType()).willReturn(MemberProfileType.CHAR);
        given(profile.getProfileImg()).willReturn("");
        given(profile.getProfileCharacter()).willReturn(MemberProfileCharacter.C01);
        given(profile.getProfileBackground()).willReturn(MemberProfileBackground.PURPLE);
        
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        MemberResDto result = memberService.getMemberDetail(MEMBER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(result.getTitle()).isNull();
    }

    @Test
    void getMemberDetail_멤버없음_예외() {
        // Given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, () -> memberService.getMemberDetail(MEMBER_ID));
    }

    @Test
    void updateMember_이미지에서캐릭터_정상() {
        // Given
        Member member = mock(Member.class);
        MemberProfile beforeProfile = mock(MemberProfile.class);
        MemberProfileDto profileDto = mock(MemberProfileDto.class);
        MemberUpdateDto updateDto = mock(MemberUpdateDto.class);
        
        given(member.getId()).willReturn(MEMBER_ID);
        given(member.getProfile()).willReturn(beforeProfile);
        given(beforeProfile.getProfileType()).willReturn(MemberProfileType.IMG);
        given(beforeProfile.getProfileImg()).willReturn(PROFILE_IMG_URL);
        
        given(updateDto.getNickname()).willReturn(NICKNAME);
        given(updateDto.getMemberProfileDto()).willReturn(profileDto);
        given(profileDto.getProfileType()).willReturn(MemberProfileType.CHAR);
        given(profileDto.getProfileImg()).willReturn(new MockMultipartFile("empty", new byte[0]));
        given(profileDto.getProfileCharacter()).willReturn(MemberProfileCharacter.C01);
        given(profileDto.getProfileBackground()).willReturn(MemberProfileBackground.PURPLE);
        
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        Long result = memberService.updateMember(MEMBER_ID, updateDto);

        // Then
        assertThat(result).isEqualTo(MEMBER_ID);
        then(s3ImageProvider).should().deleteImageFromS3(PROFILE_IMG_URL);
        then(member).should().updateMember(eq(NICKNAME), eq(MemberProfileType.CHAR), eq(""), 
                any(MemberProfileCharacter.class), any(MemberProfileBackground.class));
    }

    @Test
    void updateMember_이미지에서이미지_정상() {
        // Given
        Member member = mock(Member.class);
        MemberProfile beforeProfile = mock(MemberProfile.class);
        MemberProfileDto profileDto = mock(MemberProfileDto.class);
        MemberUpdateDto updateDto = mock(MemberUpdateDto.class);
        MockMultipartFile newImage = new MockMultipartFile("image", "test.jpg", "image/jpeg", "test".getBytes());
        String newImageUrl = "https://test.com/new-image.jpg";
        
        given(member.getId()).willReturn(MEMBER_ID);
        given(member.getProfile()).willReturn(beforeProfile);
        given(beforeProfile.getProfileType()).willReturn(MemberProfileType.IMG);
        given(beforeProfile.getProfileImg()).willReturn(PROFILE_IMG_URL);
        
        given(updateDto.getNickname()).willReturn(NICKNAME);
        given(updateDto.getMemberProfileDto()).willReturn(profileDto);
        given(profileDto.getProfileType()).willReturn(MemberProfileType.IMG);
        given(profileDto.getProfileImg()).willReturn(newImage);
        given(profileDto.getProfileCharacter()).willReturn(MemberProfileCharacter.C01);
        given(profileDto.getProfileBackground()).willReturn(MemberProfileBackground.PURPLE);
        
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(s3ImageProvider.upload(newImage)).willReturn(newImageUrl);

        // When
        Long result = memberService.updateMember(MEMBER_ID, updateDto);

        // Then
        assertThat(result).isEqualTo(MEMBER_ID);
        then(s3ImageProvider).should().deleteImageFromS3(PROFILE_IMG_URL);
        then(s3ImageProvider).should().upload(newImage);
        then(member).should().updateMember(eq(NICKNAME), eq(MemberProfileType.IMG), eq(newImageUrl), 
                any(MemberProfileCharacter.class), any(MemberProfileBackground.class));
    }

    @Test
    void updateMember_캐릭터에서이미지_정상() {
        // Given
        Member member = mock(Member.class);
        MemberProfile beforeProfile = mock(MemberProfile.class);
        MemberProfileDto profileDto = mock(MemberProfileDto.class);
        MemberUpdateDto updateDto = mock(MemberUpdateDto.class);
        MockMultipartFile newImage = new MockMultipartFile("image", "test.jpg", "image/jpeg", "test".getBytes());
        String newImageUrl = "https://test.com/new-image.jpg";
        
        given(member.getId()).willReturn(MEMBER_ID);
        given(member.getProfile()).willReturn(beforeProfile);
        given(beforeProfile.getProfileType()).willReturn(MemberProfileType.CHAR);
        
        given(updateDto.getNickname()).willReturn(NICKNAME);
        given(updateDto.getMemberProfileDto()).willReturn(profileDto);
        given(profileDto.getProfileType()).willReturn(MemberProfileType.IMG);
        given(profileDto.getProfileImg()).willReturn(newImage);
        given(profileDto.getProfileCharacter()).willReturn(MemberProfileCharacter.C01);
        given(profileDto.getProfileBackground()).willReturn(MemberProfileBackground.PURPLE);
        
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(s3ImageProvider.upload(newImage)).willReturn(newImageUrl);

        // When
        Long result = memberService.updateMember(MEMBER_ID, updateDto);

        // Then
        assertThat(result).isEqualTo(MEMBER_ID);
        then(s3ImageProvider).should().upload(newImage);
        then(s3ImageProvider).should(never()).deleteImageFromS3(anyString());
        then(member).should().updateMember(eq(NICKNAME), eq(MemberProfileType.IMG), eq(newImageUrl), 
                any(MemberProfileCharacter.class), any(MemberProfileBackground.class));
    }

    @Test
    void updateMember_멤버없음_예외() {
        // Given
        MemberUpdateDto updateDto = mock(MemberUpdateDto.class);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, () -> memberService.updateMember(MEMBER_ID, updateDto));
    }

    @Test
    void deleteMember_이미지프로필_정상() {
        // Given
        Member member = mock(Member.class);
        MemberProfile profile = mock(MemberProfile.class);
        given(member.getProfile()).willReturn(profile);
        given(profile.getProfileType()).willReturn(MemberProfileType.IMG);
        given(profile.getProfileImg()).willReturn(PROFILE_IMG_URL);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        Long result = memberService.deleteMember(MEMBER_ID);

        // Then
        assertThat(result).isEqualTo(MEMBER_ID);
        then(s3ImageProvider).should().deleteImageFromS3(PROFILE_IMG_URL);
        then(member).should().deleteMember();
    }

    @Test
    void deleteMember_캐릭터프로필_정상() {
        // Given
        Member member = mock(Member.class);
        MemberProfile profile = mock(MemberProfile.class);
        given(member.getProfile()).willReturn(profile);
        given(profile.getProfileType()).willReturn(MemberProfileType.CHAR);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        Long result = memberService.deleteMember(MEMBER_ID);

        // Then
        assertThat(result).isEqualTo(MEMBER_ID);
        then(s3ImageProvider).should(never()).deleteImageFromS3(anyString());
        then(member).should().deleteMember();
    }

    @Test
    void deleteMember_멤버없음_예외() {
        // Given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, () -> memberService.deleteMember(MEMBER_ID));
    }

    @Test
    void updateTitle_정상() {
        // Given
        Member member = mock(Member.class);
        given(member.getId()).willReturn(MEMBER_ID);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(titleRepository.existTitleMember(MEMBER_ID, TITLE_ID)).willReturn(true);
        given(titleRepository.findTitleMemberIdByMemberIdAndTitleId(MEMBER_ID, TITLE_ID))
                .willReturn(Optional.of(TITLE_MEMBER_ID));

        // When
        Long result = memberService.updateTitle(MEMBER_ID, TITLE_ID);

        // Then
        assertThat(result).isEqualTo(MEMBER_ID);
        then(member).should().updateMainTitle(TITLE_MEMBER_ID);
    }

    @Test
    void updateTitle_멤버없음_예외() {
        // Given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, () -> memberService.updateTitle(MEMBER_ID, TITLE_ID));
    }

    @Test
    void updateTitle_타이틀보유안함_예외() {
        // Given
        Member member = mock(Member.class);
        given(member.getId()).willReturn(MEMBER_ID);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(titleRepository.existTitleMember(MEMBER_ID, TITLE_ID)).willReturn(false);

        // When & Then
        assertThrows(TitleException.class, () -> memberService.updateTitle(MEMBER_ID, TITLE_ID));
    }

    @Test
    void muteAlarm_정상() {
        // Given
        Member member = mock(Member.class);
        given(member.getId()).willReturn(MEMBER_ID);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        Long result = memberService.muteAlarm(MEMBER_ID);

        // Then
        assertThat(result).isEqualTo(MEMBER_ID);
        then(member).should().muteAlarm();
    }

    @Test
    void muteAlarm_멤버없음_예외() {
        // Given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, () -> memberService.muteAlarm(MEMBER_ID));
    }

    @Test
    void turnAlarmOn_정상() {
        // Given
        Member member = mock(Member.class);
        given(member.getId()).willReturn(MEMBER_ID);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        Long result = memberService.turnAlarmOn(MEMBER_ID);

        // Then
        assertThat(result).isEqualTo(MEMBER_ID);
        then(member).should().turnAlarmOn();
    }

    @Test
    void turnAlarmOn_멤버없음_예외() {
        // Given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, () -> memberService.turnAlarmOn(MEMBER_ID));
    }

    @Test
    void updateMemberPoint_PLUS타입_정상() {
        // Given
        Member member = mock(Member.class);
        given(memberRepository.findByMemberIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        memberService.updateMemberPoint(MEMBER_ID, PointChangeType.PLUS, 500);

        // Then
        then(member).should().updatePointAmount(500);
    }

    @Test
    void updateMemberPoint_MINUS타입_정상() {
        // Given
        Member member = mock(Member.class);
        given(member.getPoint()).willReturn(POINT_AMOUNT);
        given(memberRepository.findByMemberIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        memberService.updateMemberPoint(MEMBER_ID, PointChangeType.MINUS, 500);

        // Then
        then(member).should().updatePointAmount(-500);
    }

    @Test
    void updateMemberPoint_멤버없음_예외() {
        // Given
        given(memberRepository.findByMemberIdForUpdate(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, 
                () -> memberService.updateMemberPoint(MEMBER_ID, PointChangeType.PLUS, 500));
    }

    @Test
    void updateMemberPoint_포인트부족_예외() {
        // Given
        Member member = mock(Member.class);
        given(member.getPoint()).willReturn(100); // 부족한 포인트
        given(memberRepository.findByMemberIdForUpdate(MEMBER_ID)).willReturn(Optional.of(member));

        // When & Then
        assertThrows(NotEnoughPointException.class, 
                () -> memberService.updateMemberPoint(MEMBER_ID, PointChangeType.MINUS, 500));
    }

    @Test
    void logout_정상() {
        // Given
        Member member = mock(Member.class);
        given(member.getId()).willReturn(MEMBER_ID);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        Long result = memberService.logout(MEMBER_ID);

        // Then
        assertThat(result).isEqualTo(MEMBER_ID);
        then(member).should().logout();
    }

    @Test
    void logout_멤버없음_예외() {
        // Given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, () -> memberService.logout(MEMBER_ID));
    }

    @Test
    void updateAuth_정상() {
        // Given
        Member member = mock(Member.class);
        given(member.getId()).willReturn(MEMBER_ID);
        AuthorityUpdateDto updateDto = new AuthorityUpdateDto(true, false, true, false);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        Long result = memberService.updateAuth(MEMBER_ID, updateDto);

        // Then
        assertThat(result).isEqualTo(MEMBER_ID);
        then(member).should().updateAuth(true, false, true, false);
    }

    @Test
    void updateAuth_멤버없음_예외() {
        // Given
        AuthorityUpdateDto updateDto = new AuthorityUpdateDto(true, false, true, false);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, () -> memberService.updateAuth(MEMBER_ID, updateDto));
    }

    @Test
    void getAuthDetail_정상() {
        // Given
        Member member = mock(Member.class);
        ServiceAgreement serviceAgreement = mock(ServiceAgreement.class);
        given(serviceAgreement.isServicePolicyAgreed()).willReturn(true);
        given(serviceAgreement.isPersonalInformationPolicyAgreed()).willReturn(false);
        given(serviceAgreement.isLocationPolicyAgreed()).willReturn(true);
        given(serviceAgreement.isMarketingPolicyAgreed()).willReturn(false);
        given(member.getServiceAgreement()).willReturn(serviceAgreement);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // When
        AuthorityResDto result = memberService.getAuthDetail(MEMBER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isServicePolicyAgreed()).isTrue();
        assertThat(result.isPersonalInformationPolicyAgreed()).isFalse();
        assertThat(result.isLocationPolicyAgreed()).isTrue();
        assertThat(result.isMarketingPolicyAgreed()).isFalse();
    }

    @Test
    void getAuthDetail_멤버없음_예외() {
        // Given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, () -> memberService.getAuthDetail(MEMBER_ID));
    }

    @Test
    void getMemberTitles_정상() {
        // Given
        Member member = mock(Member.class);
        given(member.getId()).willReturn(MEMBER_ID);
        TitleMemberResDto titleMemberResDto = new TitleMemberResDto(TITLE_MEMBER_ID, "Test Title", "Test Description", "https://test.com/title.jpg");
        List<TitleMemberResDto> titleList = List.of(titleMemberResDto);
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(titleRepository.getTitleMembers(MEMBER_ID)).willReturn(titleList);

        // When
        DataResDto<List<TitleMemberResDto>> result = memberService.getMemberTitles(MEMBER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getData()).isEqualTo(titleList);
    }

    @Test
    void getMemberTitles_멤버없음_예외() {
        // Given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(MemberNotFoundException.class, () -> memberService.getMemberTitles(MEMBER_ID));
    }
}
