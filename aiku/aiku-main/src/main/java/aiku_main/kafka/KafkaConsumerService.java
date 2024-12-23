package aiku_main.kafka;

import aiku_main.service.ScheduleService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.kafka_message.ScheduleArrivalMessage;
import common.kafka_message.ScheduleCloseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumerService {

    private final ScheduleService scheduleService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {"test"}, groupId = "test", concurrency = "1")
    public void consumeTest(ConsumerRecord<String, String> data, Acknowledgment ack){
        log.info("KafkaConsumerService Test : value = {}", data.value());
        ack.acknowledge();
    }

    @KafkaListener(topics = {"schedule-close"}, groupId = "aiku-main", concurrency = "1")
    public void consumeScheduleClose(ConsumerRecord<String, String> data, Acknowledgment ack){
        try {
            ScheduleCloseMessage message = objectMapper.readValue(data.value(), ScheduleCloseMessage.class);
            scheduleService.closeSchedule(message.getScheduleId(), message.getScheduleCloseTime());
        } catch (JsonMappingException e) {
            log.error("KafkaConsumerService.consumeScheduleClose에서 ScheduleCloseMessage파싱 오류가 발생하였습니다. message = {}", data.value(), e);
        } catch (JsonProcessingException e) {
            log.error("KafkaConsumerService.consumeScheduleClose에서 ScheduleCloseMessage파싱 오류가 발생하였습니다. message = {}", data.value(), e);
        }

        ack.acknowledge();
    }

    @KafkaListener(topics = {"schedule-arrival"}, groupId = "aiku-main", concurrency = "1")
    public void consumeScheduleArrival(ConsumerRecord<String, String> data, Acknowledgment ack) {
        try {
            ScheduleArrivalMessage message = objectMapper.readValue(data.value(), ScheduleArrivalMessage.class);
            scheduleService.arriveSchedule(message.getScheduleId(), message.getMemberId(), message.getArrivalTime());
        } catch (JsonMappingException e) {
            log.error("KafkaConsumerService.consumeScheduleArrival에서 ScheduleArrivalMessage파싱 오류가 발생하였습니다. message = {}", data.value(), e);
        } catch (JsonProcessingException e) {
            log.error("KafkaConsumerService.consumeScheduleArrival에서 ScheduleArrivalMessage파싱 오류가 발생하였습니다. message = {}", data.value(), e);
        }
    }
}
