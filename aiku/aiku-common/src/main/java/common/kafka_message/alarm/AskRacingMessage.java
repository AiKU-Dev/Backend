package common.kafka_message.alarm;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// AlarmMessageType = SCHEDULE_ADD, SCHEDULE_UPDATE, SCHEDULE_OWNER, SCHEDULE_OPEN, SCHEDULE_AUTO_CLOSE
@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class AskRacingMessage extends AlarmMessage{

    private Long scheduleId;
    private String scheduleName;
    private Long racingId;
    private int point;
    private AlarmMemberInfo firstRacerInfo;

    public AskRacingMessage(List<String> alarmReceiverTokens, AlarmMessageType alarmMessageType, Long scheduleId, String scheduleName, Long racingId, int point, AlarmMemberInfo firstRacerInfo) {
        super(alarmReceiverTokens, alarmMessageType);
        this.scheduleId = scheduleId;
        this.scheduleName = scheduleName;
        this.racingId = racingId;
        this.point = point;
        this.firstRacerInfo = firstRacerInfo;
    }

    @Override
    public String accept(AlarmMessageVisitor visitor) {
        return visitor.visit(this);
    }
}
