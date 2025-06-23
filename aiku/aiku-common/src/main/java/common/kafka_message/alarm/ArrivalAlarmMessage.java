package common.kafka_message.alarm;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class ArrivalAlarmMessage extends AlarmMessage {

    private long memberId;
    private long scheduleId;
    private String scheduleName;
    private LocalDateTime arrivalTime;
    private AlarmMemberInfo arriveMemberInfo;

    public ArrivalAlarmMessage(List<String> alarmReceiverTokens, AlarmMessageType alarmMessageType, long memberId, long scheduleId, String scheduleName, LocalDateTime arrivalTime, AlarmMemberInfo arriveMemberInfo) {
        super(alarmReceiverTokens, alarmMessageType);
        this.memberId = memberId;
        this.scheduleId = scheduleId;
        this.scheduleName = scheduleName;
        this.arrivalTime = arrivalTime;
        this.arriveMemberInfo = arriveMemberInfo;
    }

    @Override
    public String accept(AlarmMessageVisitor visitor) {
        return visitor.visit(this);
    }
}
