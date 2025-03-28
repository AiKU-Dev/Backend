package map.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.kafka_message.RacingPointChangedFailedMessage;
import common.kafka_message.ScheduleCloseMessage;
import common.kafka_message.alarm.AlarmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import map.application_event.publisher.RacingEventPublisher;
import map.application_event.publisher.ScheduleEventPublisher;
import map.service.RacingSagaService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumerService {

    private final ObjectMapper objectMapper;
    private final RacingSagaService racingSagaService;
    private final ScheduleEventPublisher scheduleEventPublisher;

    @KafkaListener(topics = {"racing-point-failed"}, groupId = "aiku-main", concurrency = "1")
    public void consumeRacingPointChangedFailedMessage(ConsumerRecord<String, String> data, Acknowledgment ack) {
        try {
            RacingPointChangedFailedMessage message = objectMapper.readValue(data.value(), RacingPointChangedFailedMessage.class);
            racingSagaService.rollbackRacing(message.getMemberId(), message.getPointChangedType(), message.getPointAmount(), message.getReason(), message.getReasonId());
        } catch (JsonMappingException e) {
            log.error("KafkaConsumerService.consumeRacingPointChangedFailedMessage에서 RacingPointChangedFailedMessage파싱 오류가 발생하였습니다. message = {}", data.value(), e);
        } catch (JsonProcessingException e) {
            log.error("KafkaConsumerService.consumeRacingPointChangedFailedMessage에서 RacingPointChangedFailedMessage파싱 오류가 발생하였습니다. message = {}", data.value(), e);
        }

        ack.acknowledge();
    }

    @KafkaListener(topics = {"schedule-close"}, groupId = "aiku-main", concurrency = "1")
    public void consumeScheduleClose(ConsumerRecord<String, String> data, Acknowledgment ack) {
        try {
            ScheduleCloseMessage message = objectMapper.readValue(data.value(), ScheduleCloseMessage.class);
            scheduleEventPublisher.publishScheduleCloseEvent(message.getScheduleId());
        } catch (JsonMappingException e) {
            log.error("KafkaConsumerService.consumeScheduleClose에서 ScheduleCloseMessage파싱 오류가 발생하였습니다. message = {}", data.value(), e);
        } catch (JsonProcessingException e) {
            log.error("KafkaConsumerService.consumeScheduleClose에서 ScheduleCloseMessage파싱 오류가 발생하였습니다. message = {}", data.value(), e);
        }

        ack.acknowledge();
    }


}
