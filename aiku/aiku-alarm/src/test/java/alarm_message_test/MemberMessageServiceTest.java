package alarm_message_test;

import alarm.controller.dto.DataResDto;
import alarm.controller.dto.MemberMessageDto;
import alarm.exception.MemberNotFoundException;
import alarm.fcm.MessageSender;
import alarm.repository.MemberMessageRepository;
import alarm.repository.MemberRepository;
import alarm.service.MemberMessageService;
import alarm.util.AlarmMessageConverter;
import alarm.util.ReflectionJsonUtil;
import common.domain.MemberMessage;
import common.kafka_message.alarm.AlarmMessage;
import common.kafka_message.alarm.AlarmMessageType;
import common.kafka_message.alarm.ScheduleAlarmMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

// MemberMessageService에 대한 BDD Mockito 단위테스트
@ExtendWith(MockitoExtension.class)
public class MemberMessageServiceTest {

    @Mock MemberMessageRepository memberMessageRepository;
    @Mock MemberRepository memberRepository;
    @Mock MessageSender messageSender;
    @Mock AlarmMessageConverter alarmMessageConverter;
    @InjectMocks MemberMessageService memberMessageService;

    @Test
    void sendAndSaveMessage_정상() {
        // Given
        AlarmMessage alarmMessage = mock(AlarmMessage.class);
        List<String> fcmTokens = Arrays.asList("token1", "token2", "token3");
        List<String> fcmTokensAlarmOn = Arrays.asList("token1", "token3");
        List<Long> memberIds = Arrays.asList(1L, 2L, 3L);
        Map<String, String> messageData = Map.of("key", "value");
        String simpleAlarmInfo = "테스트 알람 메시지";

        given(alarmMessage.getAlarmReceiverTokens()).willReturn(fcmTokens);
        given(alarmMessage.getAlarmMessageType()).willReturn(AlarmMessageType.SCHEDULE_ADD);
        given(memberRepository.findFirebaseTokenOnlyAlarmOn(fcmTokens)).willReturn(fcmTokensAlarmOn);
        given(memberRepository.findMemberIdsByFirebaseTokenList(fcmTokens)).willReturn(memberIds);
        given(alarmMessage.accept(alarmMessageConverter)).willReturn(simpleAlarmInfo);

        try (MockedStatic<ReflectionJsonUtil> mockedReflectionUtil = mockStatic(ReflectionJsonUtil.class)) {
            mockedReflectionUtil.when(() -> ReflectionJsonUtil.getAllFieldValuesRecursive(alarmMessage))
                    .thenReturn(messageData);

            // When
            memberMessageService.sendAndSaveMessage(alarmMessage);

            // Then
            then(memberRepository).should().findFirebaseTokenOnlyAlarmOn(fcmTokens);
            then(messageSender).should().sendMessage(messageData, fcmTokensAlarmOn);
            then(memberRepository).should().findMemberIdsByFirebaseTokenList(fcmTokens);
            then(alarmMessage).should().accept(alarmMessageConverter);
            then(memberMessageRepository).should().saveAll(argThat(memberMessages -> {
                List<MemberMessage> messages = (List<MemberMessage>) memberMessages;
                return messages.size() == 3 &&
                       messages.stream().allMatch(msg -> 
                           msg.getMessageType() == AlarmMessageType.SCHEDULE_ADD &&
                           msg.getAlarmMessageInfo().equals(simpleAlarmInfo) &&
                           memberIds.contains(msg.getMemberId()));
            }));
        }
    }

    @Test
    void sendAndSaveMessage_멤버없음_예외() {
        // Given
        AlarmMessage alarmMessage = mock(AlarmMessage.class);
        List<String> fcmTokens = Arrays.asList("token1", "token2");
        List<String> fcmTokensAlarmOn = Arrays.asList("token1");
        List<Long> memberIds = Collections.emptyList();
        Map<String, String> messageData = Map.of("key", "value");

        given(alarmMessage.getAlarmReceiverTokens()).willReturn(fcmTokens);
        given(memberRepository.findFirebaseTokenOnlyAlarmOn(fcmTokens)).willReturn(fcmTokensAlarmOn);
        given(memberRepository.findMemberIdsByFirebaseTokenList(fcmTokens)).willReturn(memberIds);

        try (MockedStatic<ReflectionJsonUtil> mockedReflectionUtil = mockStatic(ReflectionJsonUtil.class)) {
            mockedReflectionUtil.when(() -> ReflectionJsonUtil.getAllFieldValuesRecursive(alarmMessage))
                    .thenReturn(messageData);

            // When & Then
            assertThrows(MemberNotFoundException.class, () -> {
                memberMessageService.sendAndSaveMessage(alarmMessage);
            });

            then(memberRepository).should().findFirebaseTokenOnlyAlarmOn(fcmTokens);
            then(messageSender).should().sendMessage(messageData, fcmTokensAlarmOn);
            then(memberRepository).should().findMemberIdsByFirebaseTokenList(fcmTokens);
            then(alarmMessage).should(never()).accept(any());
            then(memberMessageRepository).should(never()).saveAll(any());
        }
    }

    @Test
    void sendAndSaveMessage_빈FCM토큰_예외() {
        // Given
        AlarmMessage alarmMessage = mock(AlarmMessage.class);
        List<String> fcmTokens = Collections.emptyList();
        List<String> fcmTokensAlarmOn = Collections.emptyList();
        List<Long> memberIds = Collections.emptyList();
        Map<String, String> messageData = Map.of("key", "value");

        given(alarmMessage.getAlarmReceiverTokens()).willReturn(fcmTokens);
        given(memberRepository.findFirebaseTokenOnlyAlarmOn(fcmTokens)).willReturn(fcmTokensAlarmOn);
        given(memberRepository.findMemberIdsByFirebaseTokenList(fcmTokens)).willReturn(memberIds);

        try (MockedStatic<ReflectionJsonUtil> mockedReflectionUtil = mockStatic(ReflectionJsonUtil.class)) {
            mockedReflectionUtil.when(() -> ReflectionJsonUtil.getAllFieldValuesRecursive(alarmMessage))
                    .thenReturn(messageData);

            // When & Then
            assertThrows(MemberNotFoundException.class, () -> {
                memberMessageService.sendAndSaveMessage(alarmMessage);
            });

            then(memberRepository).should().findFirebaseTokenOnlyAlarmOn(fcmTokens);
            then(messageSender).should().sendMessage(messageData, fcmTokensAlarmOn);
            then(memberRepository).should().findMemberIdsByFirebaseTokenList(fcmTokens);
        }
    }

    @Test
    void sendAndSaveMessage_알람꺼진사용자만있음_정상() {
        // Given
        AlarmMessage alarmMessage = mock(AlarmMessage.class);
        List<String> fcmTokens = Arrays.asList("token1", "token2");
        List<String> fcmTokensAlarmOn = Collections.emptyList(); // 모든 사용자가 알람 꺼짐
        List<Long> memberIds = Arrays.asList(1L, 2L);
        Map<String, String> messageData = Map.of("key", "value");
        String simpleAlarmInfo = "테스트 알람 메시지";

        given(alarmMessage.getAlarmReceiverTokens()).willReturn(fcmTokens);
        given(alarmMessage.getAlarmMessageType()).willReturn(AlarmMessageType.SCHEDULE_ADD);
        given(memberRepository.findFirebaseTokenOnlyAlarmOn(fcmTokens)).willReturn(fcmTokensAlarmOn);
        given(memberRepository.findMemberIdsByFirebaseTokenList(fcmTokens)).willReturn(memberIds);
        given(alarmMessage.accept(alarmMessageConverter)).willReturn(simpleAlarmInfo);

        try (MockedStatic<ReflectionJsonUtil> mockedReflectionUtil = mockStatic(ReflectionJsonUtil.class)) {
            mockedReflectionUtil.when(() -> ReflectionJsonUtil.getAllFieldValuesRecursive(alarmMessage))
                    .thenReturn(messageData);

            // When
            memberMessageService.sendAndSaveMessage(alarmMessage);

            // Then
            then(memberRepository).should().findFirebaseTokenOnlyAlarmOn(fcmTokens);
            then(messageSender).should().sendMessage(messageData, fcmTokensAlarmOn);
            then(memberRepository).should().findMemberIdsByFirebaseTokenList(fcmTokens);
            then(alarmMessage).should().accept(alarmMessageConverter);
            then(memberMessageRepository).should().saveAll(any());
        }
    }

    @Test
    void getMemberMessageByMemberId_정상() {
        // Given
        Long memberId = 1L;
        int page = 1;
        
        MemberMessage message1 = new MemberMessage(AlarmMessageType.SCHEDULE_ADD, memberId, "메시지 1");
        MemberMessage message2 = new MemberMessage(AlarmMessageType.SCHEDULE_UPDATE, memberId, "메시지 2");
        List<MemberMessage> memberMessages = Arrays.asList(message1, message2);

        given(memberMessageRepository.findAllByMemberId(eq(memberId), any(LocalDateTime.class), 
                any(LocalDateTime.class), eq(PageRequest.of(page, 10))))
                .willReturn(memberMessages);

        // When
        DataResDto<List<MemberMessageDto>> result = memberMessageService.getMemberMessageByMemberId(memberId, page);

        // Then
        assertThat(result.getPage()).isEqualTo(page);
        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).getMessageType()).isEqualTo(AlarmMessageType.SCHEDULE_ADD);
        assertThat(result.getData().get(0).getAlarmMessageInfo()).isEqualTo("메시지 1");
        assertThat(result.getData().get(1).getMessageType()).isEqualTo(AlarmMessageType.SCHEDULE_UPDATE);
        assertThat(result.getData().get(1).getAlarmMessageInfo()).isEqualTo("메시지 2");

        then(memberMessageRepository).should().findAllByMemberId(eq(memberId), 
                any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(page, 10)));
    }

    @Test
    void getMemberMessageByMemberId_빈결과_정상() {
        // Given
        Long memberId = 1L;
        int page = 1;
        List<MemberMessage> memberMessages = Collections.emptyList();

        given(memberMessageRepository.findAllByMemberId(eq(memberId), any(LocalDateTime.class), 
                any(LocalDateTime.class), eq(PageRequest.of(page, 10))))
                .willReturn(memberMessages);

        // When
        DataResDto<List<MemberMessageDto>> result = memberMessageService.getMemberMessageByMemberId(memberId, page);

        // Then
        assertThat(result.getPage()).isEqualTo(page);
        assertThat(result.getData()).isEmpty();

        then(memberMessageRepository).should().findAllByMemberId(eq(memberId), 
                any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(page, 10)));
    }

    @Test
    void getMemberMessageByMemberId_다른페이지_정상() {
        // Given
        Long memberId = 1L;
        int page = 2;
        
        MemberMessage message = new MemberMessage(AlarmMessageType.EMOJI, memberId, "이모지 메시지");
        List<MemberMessage> memberMessages = Arrays.asList(message);

        given(memberMessageRepository.findAllByMemberId(eq(memberId), any(LocalDateTime.class), 
                any(LocalDateTime.class), eq(PageRequest.of(page, 10))))
                .willReturn(memberMessages);

        // When
        DataResDto<List<MemberMessageDto>> result = memberMessageService.getMemberMessageByMemberId(memberId, page);

        // Then
        assertThat(result.getPage()).isEqualTo(page);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getMessageType()).isEqualTo(AlarmMessageType.EMOJI);

        then(memberMessageRepository).should().findAllByMemberId(eq(memberId), 
                any(LocalDateTime.class), any(LocalDateTime.class), eq(PageRequest.of(page, 10)));
    }

    @Test
    void sendAndSaveMessage_단일멤버_정상() {
        // Given
        AlarmMessage alarmMessage = mock(AlarmMessage.class);
        List<String> fcmTokens = Arrays.asList("single_token");
        List<String> fcmTokensAlarmOn = Arrays.asList("single_token");
        List<Long> memberIds = Arrays.asList(1L);
        Map<String, String> messageData = Map.of("key", "value");
        String simpleAlarmInfo = "단일 사용자 메시지";

        given(alarmMessage.getAlarmReceiverTokens()).willReturn(fcmTokens);
        given(alarmMessage.getAlarmMessageType()).willReturn(AlarmMessageType.MEMBER_ARRIVAL);
        given(memberRepository.findFirebaseTokenOnlyAlarmOn(fcmTokens)).willReturn(fcmTokensAlarmOn);
        given(memberRepository.findMemberIdsByFirebaseTokenList(fcmTokens)).willReturn(memberIds);
        given(alarmMessage.accept(alarmMessageConverter)).willReturn(simpleAlarmInfo);

        try (MockedStatic<ReflectionJsonUtil> mockedReflectionUtil = mockStatic(ReflectionJsonUtil.class)) {
            mockedReflectionUtil.when(() -> ReflectionJsonUtil.getAllFieldValuesRecursive(alarmMessage))
                    .thenReturn(messageData);

            // When
            memberMessageService.sendAndSaveMessage(alarmMessage);

            // Then
            then(memberRepository).should().findFirebaseTokenOnlyAlarmOn(fcmTokens);
            then(messageSender).should().sendMessage(messageData, fcmTokensAlarmOn);
            then(memberRepository).should().findMemberIdsByFirebaseTokenList(fcmTokens);
            then(alarmMessage).should().accept(alarmMessageConverter);
            then(memberMessageRepository).should().saveAll(argThat(memberMessages -> {
                List<MemberMessage> messages = (List<MemberMessage>) memberMessages;
                return messages.size() == 1 &&
                       messages.get(0).getMessageType() == AlarmMessageType.MEMBER_ARRIVAL &&
                       messages.get(0).getAlarmMessageInfo().equals(simpleAlarmInfo) &&
                       messages.get(0).getMemberId().equals(1L);
            }));
        }
    }

    @Test
    void sendAndSaveMessage_다양한알람타입_정상() {
        // Given
        AlarmMessage alarmMessage = mock(AlarmMessage.class);
        List<String> fcmTokens = Arrays.asList("token1");
        List<String> fcmTokensAlarmOn = Arrays.asList("token1");
        List<Long> memberIds = Arrays.asList(1L);
        Map<String, String> messageData = Map.of("racingId", "123", "point", "50");
        String simpleAlarmInfo = "레이싱 메시지";

        given(alarmMessage.getAlarmReceiverTokens()).willReturn(fcmTokens);
        given(alarmMessage.getAlarmMessageType()).willReturn(AlarmMessageType.RACING_START);
        given(memberRepository.findFirebaseTokenOnlyAlarmOn(fcmTokens)).willReturn(fcmTokensAlarmOn);
        given(memberRepository.findMemberIdsByFirebaseTokenList(fcmTokens)).willReturn(memberIds);
        given(alarmMessage.accept(alarmMessageConverter)).willReturn(simpleAlarmInfo);

        try (MockedStatic<ReflectionJsonUtil> mockedReflectionUtil = mockStatic(ReflectionJsonUtil.class)) {
            mockedReflectionUtil.when(() -> ReflectionJsonUtil.getAllFieldValuesRecursive(alarmMessage))
                    .thenReturn(messageData);

            // When
            memberMessageService.sendAndSaveMessage(alarmMessage);

            // Then
            then(memberMessageRepository).should().saveAll(argThat(memberMessages -> {
                List<MemberMessage> messages = (List<MemberMessage>) memberMessages;
                return messages.size() == 1 &&
                       messages.get(0).getMessageType() == AlarmMessageType.RACING_START &&
                       messages.get(0).getAlarmMessageInfo().equals(simpleAlarmInfo);
            }));
        }
    }
}
