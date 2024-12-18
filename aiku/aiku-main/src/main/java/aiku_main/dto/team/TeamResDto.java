package aiku_main.dto.team;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TeamResDto {

    private Long groupId;
    private String groupName;
    private int memberSize;
    private LocalDateTime lastScheduleTime;

    @QueryProjection
    public TeamResDto(Long groupId, String groupName, int memberSize, LocalDateTime lastScheduleTime) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.memberSize = memberSize;
        this.lastScheduleTime = lastScheduleTime;
    }
}
