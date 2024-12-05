package alarm.kafka;

import alarm.exception.MessagingException;
import alarm.service.MemberMessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.kafka_message.alarm.AlarmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import static common.response.status.BaseErrorCode.FAIL_TO_SEND_MESSAGE;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumerService {

    private final MemberMessageService messageService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"alarm"}, groupId = "aiku-alarm", concurrency = "1")
    public void consumeAlarmEvent(ConsumerRecord<String, String> data, Acknowledgment ack){
        try {
            AlarmMessage message = objectMapper.readValue(data.value(), AlarmMessage.class);

            // TODO : 캐스팅 안되는 문제 발생,  getAlarmMessageType으로 타입 비교해 캐스팅할 것
            messageService.sendAndSaveMessage(message);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new MessagingException(FAIL_TO_SEND_MESSAGE);
        }

        ack.acknowledge();
    }
}
