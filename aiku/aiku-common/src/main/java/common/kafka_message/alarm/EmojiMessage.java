package common.kafka_message.alarm;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class EmojiMessage extends AlarmMessage {

    // List에 Sender, Receiver 순서로 담긴다
    private long scheduleId;
    private String scheduleName;
    private String emojiType;
    private AlarmMemberInfo senderInfo;
    private AlarmMemberInfo receiverInfo;

    public EmojiMessage(List<String> alarmReceiverTokens, AlarmMessageType alarmMessageType, long scheduleId, String scheduleName, String emojiType, AlarmMemberInfo senderInfo, AlarmMemberInfo receiverInfo) {
        super(alarmReceiverTokens, alarmMessageType);
        this.scheduleId = scheduleId;
        this.scheduleName = scheduleName;
        this.emojiType = emojiType;
        this.senderInfo = senderInfo;
        this.receiverInfo = receiverInfo;
    }

    @Override
    public String accept(AlarmMessageVisitor visitor) {
        return visitor.visit(this);
    }

}
