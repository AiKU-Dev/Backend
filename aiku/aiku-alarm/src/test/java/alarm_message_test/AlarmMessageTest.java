package alarm_message_test;

import alarm.util.AlarmMessageConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.domain.member.MemberProfile;
import common.domain.member.MemberProfileBackground;
import common.domain.member.MemberProfileCharacter;
import common.domain.member.MemberProfileType;
import common.kafka_message.alarm.AlarmMemberInfo;
import common.kafka_message.alarm.AlarmMessage;
import common.kafka_message.alarm.AlarmMessageType;
import common.kafka_message.alarm.EmojiMessage;
import common.util.ObjectMapperUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;


public class AlarmMessageTest {

//    @Test
//    void abc() {
//        EmojiMessage emojiMessage = new EmojiMessage(List.of("123"), AlarmMessageType.EMOJI,
//                1L, "abc", "HAPPY",
//                new AlarmMemberInfo(1L, "abcd",
//                        new MemberProfile(MemberProfileType.CHAR, "", MemberProfileCharacter.C01, MemberProfileBackground.GRAY),
//                        "1234123"),
//                new AlarmMemberInfo(2L, "abcd2",
//                        new MemberProfile(MemberProfileType.CHAR, "", MemberProfileCharacter.C02, MemberProfileBackground.GRAY),
//                        "12341232"));
//        Map<String, String> allFields = AlarmMessageConverter.getAllFieldValuesRecursive(emojiMessage);
//
//        System.out.println("allFields = " + allFields);
//
//        for (Object value : allFields.values()) {
//            System.out.println("value = " + value);
//        }
//    }

    @Test
    void convert() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        String json = """
            {
              "alarmMessageType": "POINT_ERROR",
              "alarmReceiverTokens": ["token1", "token2"]
            }
            """;

        AlarmMessage message = objectMapper.readValue(json, AlarmMessage.class);
        System.out.println(message.getClass()); // PointErrorMessage
    }
}
