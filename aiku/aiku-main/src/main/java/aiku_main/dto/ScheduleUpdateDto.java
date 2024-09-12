package aiku_main.dto;

import common.domain.Location;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ScheduleUpdateDto {
    public String scheduleName;
    public Location location;
    public LocalDateTime scheduleTime;
}
