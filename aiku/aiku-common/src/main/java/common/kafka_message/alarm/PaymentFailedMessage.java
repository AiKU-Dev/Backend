package common.kafka_message.alarm;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class PaymentFailedMessage extends AlarmMessage {

    private String purchaseToken;
    private int price;
    private int point;

    public PaymentFailedMessage(List<String> alarmReceiverTokens, AlarmMessageType alarmMessageType, String purchaseToken, int price, int point) {
        super(alarmReceiverTokens, alarmMessageType);
        this.purchaseToken = purchaseToken;
        this.price = price;
        this.point = point;
    }

    @Override
    public String accept(AlarmMessageVisitor visitor) {
        return visitor.visit(this);
    }
}
