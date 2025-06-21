package common.kafka_message.alarm;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class RacingDeniedMessage extends AlarmMessage {

    private Long scheduleId;
    private String scheduleName;
    private Long racingId;
    private AlarmMemberInfo secondRacerInfo;

    public RacingDeniedMessage(List<String> alarmReceiverTokens, AlarmMessageType alarmMessageType, Long scheduleId, String scheduleName, Long racingId, AlarmMemberInfo secondRacerInfo) {
        super(alarmReceiverTokens, alarmMessageType);
        this.scheduleId = scheduleId;
        this.scheduleName = scheduleName;
        this.racingId = racingId;
        this.secondRacerInfo = secondRacerInfo;
    }

    @Override
    public String accept(AlarmMessageVisitor visitor) {
        return visitor.visit(this);
    }

}
