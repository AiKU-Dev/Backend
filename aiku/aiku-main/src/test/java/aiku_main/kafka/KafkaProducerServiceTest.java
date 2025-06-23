package aiku_main.kafka;

import common.kafka_message.alarm.AlarmMessage;
import common.kafka_message.alarm.AlarmMessageType;
import common.kafka_message.alarm.AlarmMessageVisitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static common.kafka_message.KafkaTopic.TEST;

@Transactional
@SpringBootTest
class KafkaProducerServiceTest {

    @Autowired
    KafkaProducerService kafkaProducerService;

    @Test
    void sendMessage() {
        //given
        AlarmMessage message = new AlarmMessage(null, null) {
            @Override
            public String accept(AlarmMessageVisitor visitor) {
                return "";
            }

            @Override
            public List<String> getAlarmReceiverTokens() {
                return super.getAlarmReceiverTokens();
            }

            @Override
            public AlarmMessageType getAlarmMessageType() {
                return super.getAlarmMessageType();
            }
        };

        //when
        kafkaProducerService.sendMessage(TEST, message);
    }
}
