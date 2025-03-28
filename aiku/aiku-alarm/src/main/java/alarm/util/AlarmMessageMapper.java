package alarm.util;

import alarm.exception.MessagingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.exception.JsonParseException;
import common.kafka_message.alarm.*;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static common.response.status.BaseErrorCode.FAIL_TO_SEND_MESSAGE;

@RequiredArgsConstructor
@Component
public class AlarmMessageMapper {

    private final ObjectMapper objectMapper;

    public AlarmMessage mapToAlarmMessage(ConsumerRecord<String, String> data) {
        Pattern pattern = Pattern.compile("\"alarmMessageType\":\"(.*?)\"");
        Matcher matcher = pattern.matcher(data.value());

        AlarmMessageType alarmMessageType;

        if (matcher.find()) {
            String extractedValue = matcher.group(1); // 첫 번째 그룹
            System.out.println("Extracted Value: " + extractedValue);

            alarmMessageType = AlarmMessageType.valueOf(extractedValue);
        } else {
            throw new JsonParseException();
        }

        Class<?> clazz = switch (alarmMessageType) {
            case SCHEDULE_ADD, SCHEDULE_ENTER, SCHEDULE_EXIT, SCHEDULE_UPDATE, SCHEDULE_OWNER, SCHEDULE_OPEN, SCHEDULE_AUTO_CLOSE ->
                data.value().contains("sourceMember") ? ScheduleMemberAlarmMessage.class : ScheduleAlarmMessage.class;
            case MEMBER_ARRIVAL -> ArrivalAlarmMessage.class;
            case SCHEDULE_MAP_CLOSE -> ScheduleClosedMessage.class;
            case EMOJI -> EmojiMessage.class;
            case ASK_RACING -> AskRacingMessage.class;
            case RACING_AUTO_DELETED -> RacingAutoDeletedMessage.class;
            case RACING_DENIED -> RacingDeniedMessage.class;
            case RACING_TERM -> RacingTermMessage.class;
            case RACING_START -> RacingStartMessage.class;
            case TITLE_GRANTED -> TitleGrantedMessage.class;
            case PAYMENT_SUCCESS -> PaymentSuccessMessage.class;
            case PAYMENT_FAILED -> PaymentFailedMessage.class;
            case POINT_ERROR -> PointErrorMessage.class;
            default -> null;
        };

        try {
            return (AlarmMessage) objectMapper.readValue(data.value(), clazz);
        } catch (JsonProcessingException e) {
            throw new MessagingException(FAIL_TO_SEND_MESSAGE);
        }

    }
}
