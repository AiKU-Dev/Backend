package aiku_main.dto.schedule;

import aiku_main.dto.MemberProfileResDto;
import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ScheduleArrivalMember{

    private Long memberId;
    private String nickname;
    private MemberProfileResDto memberProfile;
    private int arrivalTimeDiff;

    @QueryProjection
    public ScheduleArrivalMember(Long memberId, String nickname, MemberProfileResDto memberProfile, int arrivalTimeDiff) {
        this.memberId = memberId;
        this.nickname = nickname;
        this.memberProfile = memberProfile;
        this.arrivalTimeDiff = arrivalTimeDiff;
    }
}
