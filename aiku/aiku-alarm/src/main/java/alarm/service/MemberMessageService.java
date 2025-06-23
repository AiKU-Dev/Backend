package alarm.service;

import alarm.controller.dto.DataResDto;
import alarm.controller.dto.MemberMessageDto;
import alarm.exception.MemberNotFoundException;
import alarm.fcm.MessageSender;
import alarm.repository.MemberMessageRepository;
import alarm.repository.MemberRepository;
import alarm.util.AlarmMessageConverter;
import alarm.util.ReflectionJsonUtil;
import common.domain.MemberMessage;
import common.kafka_message.alarm.AlarmMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class MemberMessageService {

    private final MemberMessageRepository memberMessageRepository;
    private final MessageSender messageSender;
    private final AlarmMessageConverter alarmMessageConverter;
    private final AlarmTokenFilter alarmTokenFilter;

    @Transactional
    public void sendAndSaveMessage(AlarmMessage message) {
        // 1. 토큰 필터링 및 검증
        AlarmTokens alarmTokens = alarmTokenFilter.filterAndValidate(message.getAlarmReceiverTokens());

        // 2. 메시지 전송
        sendMessage(message, alarmTokens.getActiveTokens());

        // 3. 메시지 저장
        saveMessage(message, alarmTokens.getAllMemberIds());
    }

    private void sendMessage(AlarmMessage message, List<String> activeTokens) {
        if (activeTokens.isEmpty()) {
            log.warn("No active tokens found for alarm type: {}", message.getAlarmMessageType());
            return;
        }

        Map<String, String> messageData = getFCMMessage(message);
        messageSender.sendMessage(messageData, activeTokens)
                .exceptionally(ex -> {
                    log.error("Failed to send alarm message: {}", ex.getMessage(), ex);
                    return null;
                });
    }

    private void saveMessage(AlarmMessage message, List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            throw new MemberNotFoundException();
        }

        String alarmInfo = message.accept(alarmMessageConverter);
        List<MemberMessage> memberMessages = createMemberMessages(message, memberIds, alarmInfo);

        memberMessageRepository.saveAll(memberMessages);
    }

    private List<MemberMessage> createMemberMessages(AlarmMessage message, List<Long> memberIds, String alarmInfo) {
        return memberIds.stream()
                .map(memberId -> new MemberMessage(
                        message.getAlarmMessageType(),
                        memberId,
                        alarmInfo
                ))
                .collect(Collectors.toList());
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

    private Map<String, String> getFCMMessage(AlarmMessage alarmMessage) {
        return ReflectionJsonUtil.getAllFieldValuesRecursive(alarmMessage);
    }
}
