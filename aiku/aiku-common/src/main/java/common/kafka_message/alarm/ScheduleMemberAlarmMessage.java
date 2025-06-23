package common.kafka_message.alarm;

import common.domain.Location;
import common.domain.schedule.Schedule;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// AlarmMessageType = SCHEDULE_ENTER, SCHEDULE_EXIT
@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class ScheduleMemberAlarmMessage extends AlarmMessage{

    private AlarmMemberInfo sourceMember;
    private Long scheduleId;
    private String scheduleName;
    private LocalDateTime scheduleTime;
    private Location location;

    public ScheduleMemberAlarmMessage(List<String> alarmReceiverTokens, AlarmMessageType alarmMessageType, AlarmMemberInfo sourceMember, Schedule schedule) {
        super(alarmReceiverTokens, alarmMessageType);
        this.sourceMember = sourceMember;
        this.scheduleId = schedule.getId();
        this.scheduleName = schedule.getScheduleName();
        this.scheduleTime = schedule.getScheduleTime();
        this.location = schedule.getLocation();
    }

    @Override
    public String accept(AlarmMessageVisitor visitor) {
        return visitor.visit(this);
    }

}
