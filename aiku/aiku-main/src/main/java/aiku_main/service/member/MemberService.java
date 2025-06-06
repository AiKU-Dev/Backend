package aiku_main.service.member;

import aiku_main.application_event.event.PointChangeType;
import aiku_main.dto.*;
import aiku_main.dto.member.*;
import aiku_main.exception.MemberNotFoundException;
import aiku_main.exception.TitleException;
import aiku_main.repository.member.MemberRepository;
import aiku_main.repository.title.TitleRepository;
import aiku_main.s3.S3ImageProvider;
import common.domain.member.ServiceAgreement;
import common.domain.member.Member;
import common.domain.member.MemberProfile;
import common.domain.member.MemberProfileType;
import common.domain.value_reference.TitleMemberValue;
import common.exception.NotEnoughPointException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static common.response.status.BaseErrorCode.MEMBER_NOT_WITH_TITLE;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class MemberService {
    private final MemberRepository memberRepository;
    private final TitleRepository titleRepository;
    private final S3ImageProvider s3ImageProvider;

    public MemberResDto getMemberDetail(Long accessMemberId) {
        Member member = getMemberById(accessMemberId);

        MemberProfile profile = member.getProfile();
        MemberProfileResDto memberProfileResDto
                = new MemberProfileResDto(
                profile.getProfileType(), profile.getProfileImg(),
                profile.getProfileCharacter(), profile.getProfileBackground());

        TitleMemberValue titleMemberValue = member.getMainTitle();

        TitleMemberResDto mainTitle = null;
        // 장착된 칭호가 존재하는 경우에만 조회
        if (!Objects.isNull(titleMemberValue)) {
            mainTitle = titleRepository.getTitleMemberResDtoByTitleId(titleMemberValue.getId());
        }

        return new MemberResDto(
                member.getId(), member.getNickname(), member.getOauthId(),
                memberProfileResDto, mainTitle, member.getPoint());
    }

    @Transactional
    public Long updateMember(Long accessMemberId, MemberUpdateDto memberUpdateDto) {
        Member member = getMemberById(accessMemberId);

        MemberProfileDto afterProfile = memberUpdateDto.getMemberProfileDto();
        MemberProfile beforeProfile = member.getProfile();
        MemberProfileType beforeProfileType = beforeProfile.getProfileType();

        String profileImg = "";
        if (afterProfile.getProfileImg().isEmpty()) {
            if (beforeProfileType.equals(MemberProfileType.IMG)
                    && afterProfile.getProfileType().equals(MemberProfileType.CHAR)) {
                // 이미지 -> 캐릭터, 이전 이미지는 삭제
                s3ImageProvider.deleteImageFromS3(beforeProfile.getProfileImg());
            }
        } else {
            // 이미지 변경된 경우
            if (beforeProfileType.equals(MemberProfileType.IMG)) {
                // 이미지 -> 이미지, 이전 이미지는 삭제
                s3ImageProvider.deleteImageFromS3(beforeProfile.getProfileImg());
            }
            profileImg = s3ImageProvider.upload(afterProfile.getProfileImg());
        }

        member.updateMember(
                memberUpdateDto.getNickname(), afterProfile.getProfileType(),
                profileImg, afterProfile.getProfileCharacter(), afterProfile.getProfileBackground()
        );

        return member.getId();
    }

    @Transactional
    public Long deleteMember(Long accessMemberId) {
        Member member = getMemberById(accessMemberId);

        if (member.getProfile().getProfileType().equals(MemberProfileType.IMG)) {
            s3ImageProvider.deleteImageFromS3(member.getProfile().getProfileImg());
        }

        member.deleteMember();

        return accessMemberId;
    }

    @Transactional
    public Long updateTitle(Long accessMemberId, Long titleId) {
        Member member = getMemberById(accessMemberId);

        validateTitleMember(member.getId(), titleId);
        Long titleMemberId = getTitleMemberId(titleId, member);

        member.updateMainTitle(titleMemberId);

        return member.getId();
    }

    @Transactional
    public Long muteAlarm(Long accessMemberId) {
        Member member = getMemberById(accessMemberId);
        member.muteAlarm();

        return member.getId();
    }

    @Transactional
    public Long turnAlarmOn(Long accessMemberId) {
        Member member = getMemberById(accessMemberId);
        member.turnAlarmOn();

        return member.getId();
    }

    @Transactional
    public void updateMemberPoint(Long memberId, PointChangeType pointChangeType, int pointAmount) {
        Member member = memberRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new MemberNotFoundException());

        int signedChangeAmount;
        if (pointChangeType.equals(PointChangeType.PLUS)) {
            signedChangeAmount = pointAmount;
        }
        else {
            checkEnoughPoint(member.getPoint(), pointAmount);
            signedChangeAmount = (-1) * pointAmount;
        }

        member.updatePointAmount(signedChangeAmount);
    }

    private Long getTitleMemberId(Long titleId, Member member) {
        return titleRepository.findTitleMemberIdByMemberIdAndTitleId(member.getId(), titleId)
                .orElseThrow(() -> new TitleException(MEMBER_NOT_WITH_TITLE));
    }

    private void validateTitleMember(Long memberId, Long titleId) {
        if (!titleRepository.existTitleMember(memberId, titleId)) {
            throw new TitleException(MEMBER_NOT_WITH_TITLE);
        }
    }

    private void checkEnoughPoint(int memberPoint, int changePoint) {
        if (memberPoint < changePoint) {
            throw new NotEnoughPointException();
        }
    }

    @Transactional
    public Long logout(Long accessMemberId) {
        // 멤버 리프레시 토큰 삭제
        Member member = getMemberById(accessMemberId);

        member.logout();

        return member.getId();
    }


    @Transactional
    public Long updateAuth(Long accessMemberId, AuthorityUpdateDto authorityUpdateDto) {
        Member member = getMemberById(accessMemberId);

        member.updateAuth(authorityUpdateDto.isServicePolicyAgreed(), authorityUpdateDto.isPersonalInformationPolicyAgreed(),
                authorityUpdateDto.isLocationPolicyAgreed(), authorityUpdateDto.isMarketingPolicyAgreed());

        return member.getId();
    }

    public AuthorityResDto getAuthDetail(Long accessMemberId) {
        Member member = getMemberById(accessMemberId);

        ServiceAgreement serviceAgreement = member.getServiceAgreement();

        return new AuthorityResDto(
                serviceAgreement.isServicePolicyAgreed(), serviceAgreement.isPersonalInformationPolicyAgreed(),
                serviceAgreement.isLocationPolicyAgreed(), serviceAgreement.isMarketingPolicyAgreed());
    }

    public DataResDto<List<TitleMemberResDto>> getMemberTitles(Long accessMemberId) {
        Member member = getMemberById(accessMemberId);

        List<TitleMemberResDto> titleMembers = titleRepository.getTitleMembers(member.getId());

        return new DataResDto(1, titleMembers);
    }

    private Member getMemberById(Long accessMemberId) {
        return memberRepository.findById(accessMemberId)
                .orElseThrow(() -> new MemberNotFoundException());
    }
}
