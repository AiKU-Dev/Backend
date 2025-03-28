package alarm.service;

import alarm.controller.dto.DataResDto;
import alarm.controller.dto.MemberMessageDto;
import alarm.exception.MemberNotFoundException;
import alarm.fcm.MessageSender;
import alarm.repository.MemberMessageRepository;
import alarm.repository.MemberRepository;
import alarm.util.AlarmMessageConverter;
import common.domain.MemberMessage;
import common.kafka_message.alarm.AlarmMessage;
import common.kafka_message.alarm.AlarmMessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class MemberMessageService {

    private final MemberMessageRepository memberMessageRepository;
    private final MemberRepository memberRepository;
    private final MessageSender messageSender;
    private final AlarmMessageConverter alarmMessageConverter;

    @Transactional
    public void sendAndSaveMessage(AlarmMessage message) {
        List<String> fcmTokens = message.getAlarmReceiverTokens();
        List<String> fcmTokensAlarmOn = memberRepository.findFirebaseTokenOnlyAlarmOn(fcmTokens);

        Map<String, String> messageData = alarmMessageConverter.getMessage(message);

        messageSender.sendMessage(messageData, fcmTokensAlarmOn);

        // 멤버 실시간 위치는 직접적인 알림이 아니기 때문에 제외
        List<Long> memberIds = memberRepository.findMemberIdsByFirebaseTokenList(fcmTokens);

        if (memberIds.isEmpty()) {
            throw new MemberNotFoundException();
        }

        String simpleAlarmInfo = alarmMessageConverter.getSimpleAlarmInfo(message);

        List<MemberMessage> memberMessages = new ArrayList<>();
        memberIds.forEach(memberId -> memberMessages.add(
                        new MemberMessage(message.getAlarmMessageType(),
                                memberId,
                                simpleAlarmInfo
                        )
                )
        );

        memberMessageRepository.saveAll(memberMessages);
    }

    public DataResDto<List<MemberMessageDto>> getMemberMessageByMemberId(Long memberId, int page) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(7);
        List<MemberMessageDto> memberMessageDtoList = memberMessageRepository
                .findAllByMemberId(memberId, startDate, now, PageRequest.of(page, 10)).stream()
                .map(MemberMessageDto::toDto)
                .collect(Collectors.toList());

        return new DataResDto<>(page, memberMessageDtoList);
    }
}
