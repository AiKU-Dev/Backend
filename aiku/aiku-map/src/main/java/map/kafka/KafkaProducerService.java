package map.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.exception.JsonParseException;
import common.kafka_message.KafkaTopic;
import common.util.ObjectMapperUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;;

    public void sendMessage(KafkaTopic topic, Object message){
        String messageStr = ObjectMapperUtil.toJson(message);
        kafkaTemplate.send(topic.getName(), messageStr);
    }
}
