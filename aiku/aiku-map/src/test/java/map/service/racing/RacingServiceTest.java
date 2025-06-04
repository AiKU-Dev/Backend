package map.service.racing;

import common.domain.Status;
import common.domain.member.MemberProfileBackground;
import common.domain.member.MemberProfileCharacter;
import common.domain.member.MemberProfileType;
import common.domain.racing.Racing;
import common.domain.schedule.Schedule;
import common.kafka_message.KafkaTopic;
import common.kafka_message.PointChangeReason;
import common.kafka_message.PointChangedMessage;
import common.kafka_message.PointChangedType;
import common.kafka_message.alarm.*;
import map.application_event.domain.RacingInfo;
import map.application_event.event.AskRacingEvent;
import map.dto.*;
import map.exception.NotEnoughPointException;
import map.exception.RacingException;
import map.exception.ScheduleException;
import map.kafka.KafkaProducerService;
import map.repository.member.MemberRepository;
import map.repository.racing.RacingRepository;
import map.repository.schedule.ScheduleRepository;
import map.service.RacingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static common.domain.ExecStatus.RUN;
import static common.domain.ExecStatus.WAIT;
import static common.response.status.BaseErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
public class RacingServiceTest {

    @Mock private KafkaProducerService kafkaService;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private RacingRepository racingRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private MemberRepository memberRepository;
    @InjectMocks private RacingService racingService;

    @Captor private ArgumentCaptor<AskRacingEvent> askRacingEventCaptor;
    @Captor private ArgumentCaptor<AskRacingMessage> askRacingMessageCaptor;
    @Captor private ArgumentCaptor<RacingStartMessage> racingStartMessageCaptor;
    @Captor private ArgumentCaptor<RacingDeniedMessage> racingDeniedMessageCaptor;
    @Captor private ArgumentCaptor<RacingTermMessage> racingTermMessageCaptor;
    @Captor private ArgumentCaptor<PointChangedMessage> pointChangedMessageCaptor;
    @Captor private ArgumentCaptor<RacingAutoDeletedMessage> racingAutoDeletedMessageCaptor;

    private final Long MEMBER_ID = 1L;
    private final Long TARGET_MEMBER_ID = 2L;
    private final Long SCHEDULE_ID = 10L;
    private final Long RACING_ID = 100L;
    private final Long SCHEDULE_MEMBER_ID = 20L;
    private final Long TARGET_SCHEDULE_MEMBER_ID = 21L;
    private final Integer POINT_AMOUNT = 100;
    private final String SCHEDULE_NAME = "테스트 스케줄";
    private final String MEMBER_FCM_TOKEN = "member_fcm_token";
    private final String TARGET_MEMBER_FCM_TOKEN = "target_member_fcm_token";

    @Test
    void getRacings_정상() {
        // Given
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(true);
        
        LocalDateTime now = LocalDateTime.now();
        MemberProfileDto profile = new MemberProfileDto(MemberProfileType.IMG, "img.png", MemberProfileCharacter.C01, MemberProfileBackground.GREEN);
        
        List<RacingResDto> expectedRacings = List.of(
                new RacingResDto(
                        new RacerResDto(MEMBER_ID, "사용자1", profile),
                        new RacerResDto(TARGET_MEMBER_ID, "사용자2", profile),
                        now
                )
        );
        given(racingRepository.getAllRunningRacingsInSchedule(SCHEDULE_ID)).willReturn(expectedRacings);

        // When
        DataResDto<List<RacingResDto>> result = racingService.getRacings(MEMBER_ID, SCHEDULE_ID);

        // Then
        assertThat(result.getData().size()).isEqualTo(1);
        assertThat(result.getData()).isEqualTo(expectedRacings);
        then(scheduleRepository).should().existMemberInSchedule(MEMBER_ID, SCHEDULE_ID);
        then(scheduleRepository).should().existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE);
        then(racingRepository).should().getAllRunningRacingsInSchedule(SCHEDULE_ID);
    }

    @Test
    void getRacings_멤버가스케줄에없음_예외() {
        // Given
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(false);

        // When & Then
        ScheduleException exception = assertThrows(ScheduleException.class, 
            () -> racingService.getRacings(MEMBER_ID, SCHEDULE_ID));
        assertThat(exception.getStatus()).isEqualTo(NOT_IN_SCHEDULE);
    }

    @Test
    void getRacings_스케줄이진행중이아님_예외() {
        // Given
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(false);

        // When & Then
        ScheduleException exception = assertThrows(ScheduleException.class, 
            () -> racingService.getRacings(MEMBER_ID, SCHEDULE_ID));
        assertThat(exception.getStatus()).isEqualTo(NO_SUCH_SCHEDULE);
    }

    @Test
    void makeRacing_정상() {
        // Given
        RacingAddDto racingAddDto = new RacingAddDto(TARGET_MEMBER_ID, POINT_AMOUNT);
        Schedule schedule = mock(Schedule.class);
        AlarmMemberInfo memberInfo = mock(AlarmMemberInfo.class);
        
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(true);
        given(racingRepository.existsByFirstMemberIdAndSecondMemberId(SCHEDULE_ID, MEMBER_ID, TARGET_MEMBER_ID)).willReturn(false);
        given(memberRepository.checkEnoughRacingPoint(MEMBER_ID, POINT_AMOUNT)).willReturn(true);
        given(memberRepository.checkEnoughRacingPoint(TARGET_MEMBER_ID, POINT_AMOUNT)).willReturn(true);
        
        given(scheduleRepository.findScheduleMemberIdByMemberAndScheduleId(MEMBER_ID, SCHEDULE_ID)).willReturn(Optional.of(SCHEDULE_MEMBER_ID));
        given(scheduleRepository.findScheduleMemberIdByMemberAndScheduleId(TARGET_MEMBER_ID, SCHEDULE_ID)).willReturn(Optional.of(TARGET_SCHEDULE_MEMBER_ID));

        Racing newRacing = Racing.create(SCHEDULE_MEMBER_ID, TARGET_SCHEDULE_MEMBER_ID, POINT_AMOUNT);
        given(racingRepository.save(any(Racing.class))).willReturn(newRacing);
        
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(schedule.getScheduleName()).willReturn(SCHEDULE_NAME);
        given(memberRepository.findMemberInfo(MEMBER_ID)).willReturn(Optional.of(memberInfo));
        given(memberRepository.findMemberFirebaseTokenById(TARGET_MEMBER_ID)).willReturn(Optional.of(TARGET_MEMBER_FCM_TOKEN));

        // When
        racingService.makeRacing(MEMBER_ID, SCHEDULE_ID, racingAddDto);

        // Then
        then(racingRepository).should().save(any(Racing.class));
        
        // AskRacingMessage 검증
        then(kafkaService).should().sendMessage(eq(KafkaTopic.ALARM), askRacingMessageCaptor.capture());
        AskRacingMessage capturedAskRacingMessage = askRacingMessageCaptor.getValue();
        assertThat(capturedAskRacingMessage.getAlarmReceiverTokens()).isEqualTo(List.of(TARGET_MEMBER_FCM_TOKEN));
        assertThat(capturedAskRacingMessage.getAlarmMessageType()).isEqualTo(AlarmMessageType.ASK_RACING);
        assertThat(capturedAskRacingMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedAskRacingMessage.getScheduleName()).isEqualTo(SCHEDULE_NAME);
        assertThat(capturedAskRacingMessage.getPoint()).isEqualTo(POINT_AMOUNT);
        assertThat(capturedAskRacingMessage.getFirstRacerInfo()).isEqualTo(memberInfo);
        
        // AskRacingEvent 검증
        then(publisher).should().publishEvent(askRacingEventCaptor.capture());
        AskRacingEvent capturedAskRacingEvent = askRacingEventCaptor.getValue();
        assertThat(capturedAskRacingEvent.getRacingInfo().getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedAskRacingEvent.getRacingInfo().getScheduleName()).isEqualTo(SCHEDULE_NAME);
        assertThat(capturedAskRacingEvent.getRacingInfo().getFirstRacerId()).isEqualTo(MEMBER_ID);
        assertThat(capturedAskRacingEvent.getRacingInfo().getSecondRacerId()).isEqualTo(TARGET_MEMBER_ID);
        assertThat(capturedAskRacingEvent.getRacingInfo().getPointAmount()).isEqualTo(POINT_AMOUNT);
    }

    @Test
    void makeRacing_스케줄이진행중이아님_예외() {
        // Given
        RacingAddDto racingAddDto = new RacingAddDto(TARGET_MEMBER_ID, POINT_AMOUNT);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(false);

        // When & Then
        ScheduleException exception = assertThrows(ScheduleException.class, 
            () -> racingService.makeRacing(MEMBER_ID, SCHEDULE_ID, racingAddDto));
        assertThat(exception.getStatus()).isEqualTo(NO_SUCH_SCHEDULE);
    }

    @Test
    void makeRacing_중복레이싱존재_예외() {
        // Given
        RacingAddDto racingAddDto = new RacingAddDto(TARGET_MEMBER_ID, POINT_AMOUNT);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(true);
        given(racingRepository.existsByFirstMemberIdAndSecondMemberId(SCHEDULE_ID, MEMBER_ID, TARGET_MEMBER_ID)).willReturn(true);

        // When & Then
        RacingException exception = assertThrows(RacingException.class, 
            () -> racingService.makeRacing(MEMBER_ID, SCHEDULE_ID, racingAddDto));
        assertThat(exception.getStatus()).isEqualTo(DUPLICATE_RACING);
    }

    @Test
    void makeRacing_사용자포인트부족_예외() {
        // Given
        RacingAddDto racingAddDto = new RacingAddDto(TARGET_MEMBER_ID, POINT_AMOUNT);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(true);
        given(racingRepository.existsByFirstMemberIdAndSecondMemberId(SCHEDULE_ID, MEMBER_ID, TARGET_MEMBER_ID)).willReturn(false);
        given(memberRepository.checkEnoughRacingPoint(MEMBER_ID, POINT_AMOUNT)).willReturn(false);

        // When & Then
        assertThrows(NotEnoughPointException.class, 
            () -> racingService.makeRacing(MEMBER_ID, SCHEDULE_ID, racingAddDto));
    }

    @Test
    void makeRacing_대상사용자포인트부족_예외() {
        // Given
        RacingAddDto racingAddDto = new RacingAddDto(TARGET_MEMBER_ID, POINT_AMOUNT);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(true);
        given(racingRepository.existsByFirstMemberIdAndSecondMemberId(SCHEDULE_ID, MEMBER_ID, TARGET_MEMBER_ID)).willReturn(false);
        given(memberRepository.checkEnoughRacingPoint(MEMBER_ID, POINT_AMOUNT)).willReturn(true);
        given(memberRepository.checkEnoughRacingPoint(TARGET_MEMBER_ID, POINT_AMOUNT)).willReturn(false);

        // When & Then
        assertThrows(NotEnoughPointException.class, 
            () -> racingService.makeRacing(MEMBER_ID, SCHEDULE_ID, racingAddDto));
    }

    @Test
    void autoDeleteRacingById_정상() {
        // Given
        RacingInfo racingInfo = new RacingInfo(SCHEDULE_ID, SCHEDULE_NAME, RACING_ID, MEMBER_ID, TARGET_MEMBER_ID, POINT_AMOUNT);
        AlarmMemberInfo targetMemberInfo = mock(AlarmMemberInfo.class);
        
        given(memberRepository.findMemberInfo(TARGET_MEMBER_ID)).willReturn(Optional.of(targetMemberInfo));
        given(memberRepository.findMemberFirebaseTokenById(MEMBER_ID)).willReturn(Optional.of(MEMBER_FCM_TOKEN));
        given(memberRepository.findMemberFirebaseTokenById(TARGET_MEMBER_ID)).willReturn(Optional.of(TARGET_MEMBER_FCM_TOKEN));

        // When
        racingService.autoDeleteRacingById(racingInfo);

        // Then
        then(racingRepository).should().deleteById(RACING_ID);

        // RacingAutoDeletedMessage 검증
        then(kafkaService).should().sendMessage(eq(KafkaTopic.ALARM), racingAutoDeletedMessageCaptor.capture());
        RacingAutoDeletedMessage capturedMessage = racingAutoDeletedMessageCaptor.getValue();
        assertThat(capturedMessage.getAlarmReceiverTokens()).isEqualTo(List.of(MEMBER_FCM_TOKEN, TARGET_MEMBER_FCM_TOKEN));
        assertThat(capturedMessage.getAlarmMessageType()).isEqualTo(AlarmMessageType.RACING_AUTO_DELETED);
        assertThat(capturedMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedMessage.getScheduleName()).isEqualTo(SCHEDULE_NAME);
        assertThat(capturedMessage.getRacingId()).isEqualTo(RACING_ID);
        assertThat(capturedMessage.getPoint()).isEqualTo(POINT_AMOUNT);
        assertThat(capturedMessage.getSecondRacerInfo()).isEqualTo(targetMemberInfo);
    }

    @Test
    void acceptRacing_정상() {
        // Given
        Racing racing = mock(Racing.class);
        Schedule schedule = mock(Schedule.class);
        AlarmMemberInfo memberInfo = mock(AlarmMemberInfo.class);
        AlarmMemberInfo targetMemberInfo = mock(AlarmMemberInfo.class);

        given(racing.getPointAmount()).willReturn(POINT_AMOUNT);
        given(schedule.getScheduleName()).willReturn(SCHEDULE_NAME);

        given(racingRepository.existsByIdAndRaceStatusAndStatus(RACING_ID, WAIT, Status.ALIVE)).willReturn(true);
        given(racingRepository.checkBothMemberHaveEnoughRacingPoint(RACING_ID)).willReturn(true);
        given(racingRepository.findById(RACING_ID)).willReturn(Optional.of(racing));

        List<AlarmMemberInfo> memberInfos = Arrays.asList(memberInfo, targetMemberInfo);
        given(racingRepository.findMemberInfoByScheduleMemberId(RACING_ID)).willReturn(memberInfos);
        given(racingRepository.findRacersFcmTokensInRacing(RACING_ID)).willReturn(Arrays.asList(MEMBER_FCM_TOKEN, TARGET_MEMBER_FCM_TOKEN));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        // When
        Long result = racingService.acceptRacing(MEMBER_ID, SCHEDULE_ID, RACING_ID);

        // Then
        assertThat(result).isEqualTo(RACING_ID);
        then(racing).should().startRacing();

        // RacingStartMessage 검증
        then(kafkaService).should().sendMessage(eq(KafkaTopic.ALARM), racingStartMessageCaptor.capture());
        RacingStartMessage capturedStartMessage = racingStartMessageCaptor.getValue();
        assertThat(capturedStartMessage.getAlarmReceiverTokens()).isEqualTo(List.of(MEMBER_FCM_TOKEN, TARGET_MEMBER_FCM_TOKEN));
        assertThat(capturedStartMessage.getAlarmMessageType()).isEqualTo(AlarmMessageType.RACING_START);
        assertThat(capturedStartMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedStartMessage.getScheduleName()).isEqualTo(SCHEDULE_NAME);
        assertThat(capturedStartMessage.getRacingId()).isEqualTo(RACING_ID);
        assertThat(capturedStartMessage.getPoint()).isEqualTo(POINT_AMOUNT);
        assertThat(capturedStartMessage.getFirstRacerInfo()).isEqualTo(memberInfo);
        assertThat(capturedStartMessage.getSecondRacerInfo()).isEqualTo(targetMemberInfo);

        // PointChangedMessage 검증 (2개 메시지)
        then(kafkaService).should(times(2)).sendMessage(eq(KafkaTopic.ALARM), pointChangedMessageCaptor.capture());
        List<PointChangedMessage> pointChangedMessages = pointChangedMessageCaptor.getAllValues();

        // 첫 번째 메시지 검증
        assertThat(pointChangedMessages.get(0).getMemberId()).isEqualTo(memberInfos.get(0).getMemberId());
        assertThat(pointChangedMessages.get(0).getPointChangedType()).isEqualTo(PointChangedType.MINUS);
        assertThat(pointChangedMessages.get(0).getPointAmount()).isEqualTo(POINT_AMOUNT);
        assertThat(pointChangedMessages.get(0).getReason()).isEqualTo(PointChangeReason.RACING);
        assertThat(pointChangedMessages.get(0).getReasonId()).isEqualTo(RACING_ID);

        // 두 번째 메시지 검증
        assertThat(pointChangedMessages.get(1).getMemberId()).isEqualTo(memberInfos.get(1).getMemberId());
        assertThat(pointChangedMessages.get(1).getPointChangedType()).isEqualTo(PointChangedType.MINUS);
        assertThat(pointChangedMessages.get(1).getPointAmount()).isEqualTo(POINT_AMOUNT);
        assertThat(pointChangedMessages.get(1).getReason()).isEqualTo(PointChangeReason.RACING);
        assertThat(pointChangedMessages.get(1).getReasonId()).isEqualTo(RACING_ID);
    }

    @Test
    void acceptRacing_레이싱이대기중이아님_예외() {
        // Given
        given(racingRepository.existsByIdAndRaceStatusAndStatus(RACING_ID, WAIT, Status.ALIVE)).willReturn(false);

        // When & Then
        ScheduleException exception = assertThrows(ScheduleException.class,
            () -> racingService.acceptRacing(MEMBER_ID, SCHEDULE_ID, RACING_ID));
        assertThat(exception.getStatus()).isEqualTo(NO_SUCH_SCHEDULE);
    }

    @Test
    void acceptRacing_사용자포인트부족_예외() {
        // Given
        given(racingRepository.existsByIdAndRaceStatusAndStatus(RACING_ID, WAIT, Status.ALIVE)).willReturn(true);
        given(racingRepository.checkBothMemberHaveEnoughRacingPoint(RACING_ID)).willReturn(false);

        // When & Then
        assertThrows(NotEnoughPointException.class,
            () -> racingService.acceptRacing(MEMBER_ID, SCHEDULE_ID, RACING_ID));
    }

    @Test
    void denyRacing_정상() {
        // Given
        Schedule schedule = mock(Schedule.class);
        AlarmMemberInfo memberInfo = mock(AlarmMemberInfo.class);

        given(schedule.getScheduleName()).willReturn(SCHEDULE_NAME);
        given(racingRepository.existsByIdAndRaceStatusAndStatus(RACING_ID, WAIT, Status.ALIVE)).willReturn(true);
        given(racingRepository.checkMemberIsSecondRacerInRacing(MEMBER_ID, RACING_ID)).willReturn(true);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(racingRepository.findRacersFcmTokensInRacing(RACING_ID)).willReturn(Arrays.asList(MEMBER_FCM_TOKEN, TARGET_MEMBER_FCM_TOKEN));
        given(memberRepository.findMemberInfo(MEMBER_ID)).willReturn(Optional.of(memberInfo));

        // When
        Long result = racingService.denyRacing(MEMBER_ID, SCHEDULE_ID, RACING_ID);

        // Then
        assertThat(result).isEqualTo(RACING_ID);
        then(racingRepository).should().cancelRacing(RACING_ID);

        // RacingDeniedMessage 검증
        then(kafkaService).should().sendMessage(eq(KafkaTopic.ALARM), racingDeniedMessageCaptor.capture());
        RacingDeniedMessage capturedDeniedMessage = racingDeniedMessageCaptor.getValue();
        assertThat(capturedDeniedMessage.getAlarmReceiverTokens()).isEqualTo(List.of(MEMBER_FCM_TOKEN, TARGET_MEMBER_FCM_TOKEN));
        assertThat(capturedDeniedMessage.getAlarmMessageType()).isEqualTo(AlarmMessageType.RACING_DENIED);
        assertThat(capturedDeniedMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedDeniedMessage.getScheduleName()).isEqualTo(SCHEDULE_NAME);
        assertThat(capturedDeniedMessage.getRacingId()).isEqualTo(RACING_ID);
        assertThat(capturedDeniedMessage.getSecondRacerInfo()).isEqualTo(memberInfo);
    }

    @Test
    void denyRacing_레이싱이대기중이아님_예외() {
        // Given
        given(racingRepository.existsByIdAndRaceStatusAndStatus(RACING_ID, WAIT, Status.ALIVE)).willReturn(false);

        // When & Then
        ScheduleException exception = assertThrows(ScheduleException.class,
            () -> racingService.denyRacing(MEMBER_ID, SCHEDULE_ID, RACING_ID));
        assertThat(exception.getStatus()).isEqualTo(NO_SUCH_SCHEDULE);
    }

    @Test
    void denyRacing_사용자가두번째레이서가아님_예외() {
        // Given
        given(racingRepository.existsByIdAndRaceStatusAndStatus(RACING_ID, WAIT, Status.ALIVE)).willReturn(true);
        given(racingRepository.checkMemberIsSecondRacerInRacing(MEMBER_ID, RACING_ID)).willReturn(false);

        // When & Then
        RacingException exception = assertThrows(RacingException.class,
            () -> racingService.denyRacing(MEMBER_ID, SCHEDULE_ID, RACING_ID));
        assertThat(exception.getStatus()).isEqualTo(NOT_IN_RACING);
    }

    @Test
    void makeMemberWinnerInRacing_정상() {
        // Given
        AlarmMemberInfo memberInfo = mock(AlarmMemberInfo.class);
        AlarmMemberInfo targetMemberInfo = mock(AlarmMemberInfo.class);

        given(scheduleRepository.findScheduleMemberIdByMemberAndScheduleId(MEMBER_ID, SCHEDULE_ID))
            .willReturn(Optional.of(SCHEDULE_MEMBER_ID));

        RunningRacingDto racingDto = new RunningRacingDto(
            RACING_ID, SCHEDULE_MEMBER_ID, TARGET_SCHEDULE_MEMBER_ID, POINT_AMOUNT
        );
        given(racingRepository.findRunningRacingsByScheduleMemberId(SCHEDULE_MEMBER_ID))
            .willReturn(Collections.singletonList(racingDto));

        given(memberRepository.findMemberInfoByScheduleMemberId(SCHEDULE_MEMBER_ID))
            .willReturn(Optional.of(memberInfo));
        given(memberRepository.findMemberInfoByScheduleMemberId(TARGET_SCHEDULE_MEMBER_ID))
            .willReturn(Optional.of(targetMemberInfo));

        given(racingRepository.findRacersFcmTokensInRacing(RACING_ID))
            .willReturn(Arrays.asList(MEMBER_FCM_TOKEN, TARGET_MEMBER_FCM_TOKEN));

        // When
        racingService.makeMemberWinnerInRacing(MEMBER_ID, SCHEDULE_ID, SCHEDULE_NAME);

        // Then
        then(racingRepository).should().setWinnerAndTermRacingByScheduleMemberId(SCHEDULE_MEMBER_ID);

        // PointChangedMessage 검증
        then(kafkaService).should().sendMessage(eq(KafkaTopic.ALARM), pointChangedMessageCaptor.capture());
        PointChangedMessage capturedPointMessage = pointChangedMessageCaptor.getValue();
        assertThat(capturedPointMessage.getMemberId()).isEqualTo(memberInfo.getMemberId());
        assertThat(capturedPointMessage.getPointChangedType()).isEqualTo(PointChangedType.PLUS);
        assertThat(capturedPointMessage.getPointAmount()).isEqualTo(POINT_AMOUNT * 2);
        assertThat(capturedPointMessage.getReason()).isEqualTo(PointChangeReason.RACING_REWARD);
        assertThat(capturedPointMessage.getReasonId()).isEqualTo(RACING_ID);

        // RacingTermMessage 검증
        then(kafkaService).should().sendMessage(eq(KafkaTopic.ALARM), racingTermMessageCaptor.capture());
        RacingTermMessage capturedTermMessage = racingTermMessageCaptor.getValue();
        assertThat(capturedTermMessage.getAlarmReceiverTokens()).isEqualTo(List.of(MEMBER_FCM_TOKEN, TARGET_MEMBER_FCM_TOKEN));
        assertThat(capturedTermMessage.getAlarmMessageType()).isEqualTo(AlarmMessageType.RACING_TERM);
        assertThat(capturedTermMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedTermMessage.getScheduleName()).isEqualTo(SCHEDULE_NAME);
        assertThat(capturedTermMessage.getRacingId()).isEqualTo(RACING_ID);
        assertThat(capturedTermMessage.getPoint()).isEqualTo(POINT_AMOUNT);
        assertThat(capturedTermMessage.getWinnerRacerInfo()).isEqualTo(memberInfo);
        assertThat(capturedTermMessage.getLoserRacerInfo()).isEqualTo(targetMemberInfo);
    }

    @Test
    void terminateRunningRacing_정상() {
        // Given
        AlarmMemberInfo memberInfo = mock(AlarmMemberInfo.class);
        AlarmMemberInfo targetMemberInfo = mock(AlarmMemberInfo.class);

        TermRacingDto racingDto = new TermRacingDto(
            RACING_ID, SCHEDULE_MEMBER_ID, TARGET_SCHEDULE_MEMBER_ID, POINT_AMOUNT
        );
        given(racingRepository.findTermRacingIdsWithNoWinnerInSchedule(SCHEDULE_ID))
            .willReturn(Collections.singletonList(racingDto));

        given(memberRepository.findMemberInfoByScheduleMemberId(SCHEDULE_MEMBER_ID))
            .willReturn(Optional.of(memberInfo));
        given(memberRepository.findMemberInfoByScheduleMemberId(TARGET_SCHEDULE_MEMBER_ID))
            .willReturn(Optional.of(targetMemberInfo));

        // When
        racingService.terminateRunningRacing(SCHEDULE_ID);

        // Then
        then(racingRepository).should().terminateRunningRacing(SCHEDULE_ID);

        // PointChangedMessage 검증 (2개 메시지)
        then(kafkaService).should(times(2)).sendMessage(eq(KafkaTopic.ALARM), pointChangedMessageCaptor.capture());
        List<PointChangedMessage> pointChangedMessages = pointChangedMessageCaptor.getAllValues();

        // 첫 번째 메시지 검증
        assertThat(pointChangedMessages.get(0).getMemberId()).isEqualTo(memberInfo.getMemberId());
        assertThat(pointChangedMessages.get(0).getPointChangedType()).isEqualTo(PointChangedType.PLUS);
        assertThat(pointChangedMessages.get(0).getPointAmount()).isEqualTo(POINT_AMOUNT);
        assertThat(pointChangedMessages.get(0).getReason()).isEqualTo(PointChangeReason.RACING_DRAW);
        assertThat(pointChangedMessages.get(0).getReasonId()).isEqualTo(RACING_ID);

        // 두 번째 메시지 검증
        assertThat(pointChangedMessages.get(1).getMemberId()).isEqualTo(targetMemberInfo.getMemberId());
        assertThat(pointChangedMessages.get(1).getPointChangedType()).isEqualTo(PointChangedType.PLUS);
        assertThat(pointChangedMessages.get(1).getPointAmount()).isEqualTo(POINT_AMOUNT);
        assertThat(pointChangedMessages.get(1).getReason()).isEqualTo(PointChangeReason.RACING_DRAW);
        assertThat(pointChangedMessages.get(1).getReasonId()).isEqualTo(RACING_ID);
    }
}
