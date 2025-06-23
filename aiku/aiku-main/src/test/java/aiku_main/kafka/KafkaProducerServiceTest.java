package aiku_main.kafka;

import common.kafka_message.alarm.AlarmMessage;
import common.kafka_message.alarm.AlarmMessageType;
import common.kafka_message.alarm.AlarmMessageVisitor;
import common.kafka_message.alarm.ArrivalAlarmMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        AlarmMessage message = new ArrivalAlarmMessage(
                List.of("member1", "member2"),
                AlarmMessageType.MEMBER_ARRIVAL,
                123L,
                234L,
                "schedule1",
                LocalDateTime.now(),
                null
        );
        //when
        kafkaProducerService.sendMessage(TEST, message);
    }
}
