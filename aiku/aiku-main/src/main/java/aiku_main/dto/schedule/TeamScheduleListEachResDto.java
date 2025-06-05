package aiku_main.dto.schedule;

import aiku_main.dto.LocationDto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.querydsl.core.annotations.QueryProjection;
import common.domain.ExecStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class TeamScheduleListEachResDto {

    private Long scheduleId;
    private String scheduleName;
    private LocationDto location;
    private LocalDateTime scheduleTime;
    private ExecStatus scheduleStatus;
    private int memberSize;
    private boolean accept = false;

    @JsonIgnore
    Set<Long> membersIdList;

    @QueryProjection
    public TeamScheduleListEachResDto(Long scheduleId,
                                      String scheduleName,
                                      LocationDto location,
                                      LocalDateTime scheduleTime,
                                      ExecStatus scheduleStatus,
                                      String membersIdStringList) {
        this.scheduleId = scheduleId;
        this.scheduleName = scheduleName;
        this.location = location;
        this.scheduleTime = scheduleTime;
        this.scheduleStatus = scheduleStatus;

        membersIdList = Arrays.stream(membersIdStringList.split(","))
                .map(Long::parseLong)
                .collect(Collectors.toSet());
        this.memberSize = membersIdList.size();
    }

    public void SetAcceptIfEntered(Long memberId){
        if(membersIdList.contains(memberId)){
            this.accept = true;
        }
    }
}
