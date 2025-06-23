package common.kafka_message.alarm;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class LocationAlarmMessage extends AlarmMessage {

    private long scheduleId;
    private AlarmMemberInfo memberInfo;
    private double latitude;
    private double longitude;

    public LocationAlarmMessage(List<String> alarmReceiverTokens, AlarmMessageType alarmMessageType, long scheduleId, AlarmMemberInfo memberInfo, double latitude, double longitude) {
        super(alarmReceiverTokens, alarmMessageType);
        this.scheduleId = scheduleId;
        this.memberInfo = memberInfo;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public String accept(AlarmMessageVisitor visitor) {
        return visitor.visit(this);
    }

}
