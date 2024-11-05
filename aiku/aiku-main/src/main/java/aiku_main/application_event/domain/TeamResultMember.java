package aiku_main.application_event.domain;

import aiku_main.dto.MemberProfileResDto;
import com.querydsl.core.annotations.QueryProjection;
import common.domain.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static common.domain.Status.ALIVE;

@Getter @Setter
@NoArgsConstructor
public class TeamResultMember {

    private Long memberId;
    private String nickname;
    private MemberProfileResDto memberProfile;
    private int analysis;
    private int previousRank;
    private int rank;
    private boolean isTeamMember;

    @QueryProjection
    public TeamResultMember(Long memberId, String nickname, MemberProfileResDto memberProfile, int analysis, Status isTeamMember) {
        this.memberId = memberId;
        this.nickname = nickname;
        this.memberProfile = memberProfile;
        this.analysis = analysis;
        this.isTeamMember = isTeamMember == ALIVE ? true : false;
    }

    public TeamResultMember(Long memberId, String nickname, MemberProfileResDto memberProfile, int analysis, int previousRank, Status isTeamMember) {
        this.memberId = memberId;
        this.nickname = nickname;
        this.memberProfile = memberProfile;
        this.analysis = analysis;
        this.previousRank = previousRank;
        this.isTeamMember = isTeamMember == ALIVE ? true : false;
    }
}
