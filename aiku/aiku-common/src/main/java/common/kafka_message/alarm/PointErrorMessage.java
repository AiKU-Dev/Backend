package common.kafka_message.alarm;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class PointErrorMessage extends AlarmMessage {
    public PointErrorMessage(List<String> alarmReceiverTokens, AlarmMessageType alarmMessageType) {
        super(alarmReceiverTokens, alarmMessageType);
    }

    @Override
    public String accept(AlarmMessageVisitor visitor) {
        return visitor.visit(this);
    }
}
