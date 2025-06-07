package common.kafka_message.alarm;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.CUSTOM,
        include = JsonTypeInfo.As.PROPERTY,
        property = "alarmMessageType",
        visible = true
)
@JsonTypeIdResolver(AlarmMessageTypeIdResolver.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AlarmMessage {

    private List<String> alarmReceiverTokens;
    private AlarmMessageType alarmMessageType;

}
