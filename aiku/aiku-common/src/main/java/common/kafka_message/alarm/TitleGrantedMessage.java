package common.kafka_message.alarm;

import common.domain.title.TitleCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class TitleGrantedMessage extends AlarmMessage {
    private Long titleId;
    private String titleName;
    private String titleDescription;
    private TitleCode titleCode;

    public TitleGrantedMessage(List<String> alarmReceiverTokens, AlarmMessageType alarmMessageType, Long titleId, String titleName, String titleDescription, TitleCode titleCode) {
        super(alarmReceiverTokens, alarmMessageType);
        this.titleId = titleId;
        this.titleName = titleName;
        this.titleDescription = titleDescription;
        this.titleCode = titleCode;
    }

    @Override
    public String accept(AlarmMessageVisitor visitor) {
        return visitor.visit(this);
    }

}
