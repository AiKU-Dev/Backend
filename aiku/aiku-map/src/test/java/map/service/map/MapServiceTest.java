package map.service.map;

import common.domain.Arrival;
import common.domain.Location;
import common.domain.Status;
import common.domain.schedule.Schedule;
import common.domain.schedule.ScheduleMember;
import common.kafka_message.KafkaTopic;
import common.kafka_message.ScheduleCloseMessage;
import common.kafka_message.alarm.*;
import map.application_event.event.MemberArrivalEvent;
import map.application_event.event.ScheduleCloseEvent;
import map.dto.*;
import map.exception.ScheduleException;
import map.kafka.KafkaProducerService;
import map.repository.arrival.ArrivalRepository;
import map.repository.member.MemberRepository;
import map.repository.schedule.ScheduleRepository;
import map.repository.schedule_location.ScheduleLocationRepository;
import map.service.MapService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.*;

import static common.domain.ExecStatus.RUN;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import static org.mockito.BDDMockito.given;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MapServiceTest {

    @Mock KafkaProducerService kafkaService;
    @Mock MemberRepository memberRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock ScheduleLocationRepository scheduleLocationRepository;
    @Mock ArrivalRepository arrivalRepository;
    @Mock ApplicationEventPublisher publisher;
    @InjectMocks MapService mapService;

    @Captor ArgumentCaptor<EmojiMessage> emojiMessageArgumentCaptor;
    @Captor ArgumentCaptor<MemberArrivalEvent> memberArrivalEventCaptor;
    @Captor ArgumentCaptor<ScheduleCloseEvent> scheduleCloseEventCaptor;
    @Captor ArgumentCaptor<ArrivalAlarmMessage> alarmMessageArgumentCaptor;

    private final Long MEMBER_ID = 1L;
    private final Long SCHEDULE_ID = 10L;
    private final String SCHEDULE_NAME = "testSchedule";
    private final LocalDateTime NOW = LocalDateTime.now();

    @Test
    void getScheduleDetail_정상() {
        // Given
        Location location = new Location("Test Location", 1.0, 2.0);
        Schedule schedule = mock(Schedule.class);
        given(schedule.getLocation()).willReturn(location);

        List<ScheduleMemberResDto> members = List.of(mock(ScheduleMemberResDto.class));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.getScheduleMembersInfo(SCHEDULE_ID)).willReturn(members);

        // When
        ScheduleDetailResDto res = mapService.getScheduleDetail(MEMBER_ID, SCHEDULE_ID);

        // Then
        assertThat(res).isNotNull();
        assertThat(res.getScheduleId()).isEqualTo(schedule.getId());
        assertThat(res.getMembers()).isEqualTo(members);
    }

    @Test
    void getScheduleDetail_스케줄없음_예외() {
        // Given
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.getScheduleDetail(MEMBER_ID, SCHEDULE_ID));
    }

    @Test
    void getScheduleDetail_멤버없음_예외() {
        // Given
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(mock(Schedule.class)));
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(false);

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.getScheduleDetail(MEMBER_ID, SCHEDULE_ID));
    }

    @Test
    void saveAndSendAllLocation_정상() {
        // Given
        RealTimeLocationDto dto = new RealTimeLocationDto(1.0, 2.0);
        List<RealTimeLocationResDto> locs = List.of(mock(RealTimeLocationResDto.class));
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleLocationRepository.getScheduleLocations(SCHEDULE_ID)).willReturn(locs);

        // When
        LocationsResponseDto res = mapService.saveAndSendAllLocation(MEMBER_ID, SCHEDULE_ID, dto);

        // Then
        assertThat(res.getCount()).isEqualTo(locs.size());
        assertThat(res.getLocations()).isEqualTo(locs);
        then(scheduleLocationRepository).should().saveLocation(SCHEDULE_ID, MEMBER_ID, 1.0, 2.0);
    }

    @Test
    void saveAndSendAllLocation_멤버없음_예외() {
        // Given
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(false);

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.saveAndSendAllLocation(MEMBER_ID, SCHEDULE_ID, new RealTimeLocationDto(1.0,2.0)));
    }

    @Test
    void getAllLocation_정상() {
        // Given
        List<RealTimeLocationResDto> locs = List.of(mock(RealTimeLocationResDto.class));
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleLocationRepository.getScheduleLocations(SCHEDULE_ID)).willReturn(locs);

        // When
        LocationsResponseDto res = mapService.getAllLocation(MEMBER_ID, SCHEDULE_ID);

        // Then
        assertThat(res.getCount()).isEqualTo(locs.size());
        assertThat(res.getLocations()).isEqualTo(locs);
    }

    @Test
    void getAllLocation_멤버없음_예외() {
        // Given
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(false);

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.getAllLocation(MEMBER_ID, SCHEDULE_ID));
    }

    @Test
    void makeMemberArrive_마지막멤버_정상() {
        // Given
        Schedule schedule = mock(Schedule.class);
        ScheduleMember scheduleMember = mock(ScheduleMember.class);
        Location location = new Location("Location1",1.0, 2.0);
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(true);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleRepository.findScheduleMember(MEMBER_ID, SCHEDULE_ID)).willReturn(Optional.of(scheduleMember));
        given(schedule.getLocation()).willReturn(location);
        given(schedule.getScheduleName()).willReturn(SCHEDULE_NAME);
        given(arrivalRepository.isAllMembersInScheduleArrived(SCHEDULE_ID)).willReturn(true);

        // When
        MemberArrivalDto dto = new MemberArrivalDto(NOW);
        Long res = mapService.makeMemberArrive(MEMBER_ID, SCHEDULE_ID, dto);

        // Then
        assertThat(res).isEqualTo(SCHEDULE_ID);
        then(arrivalRepository).should().save(any(Arrival.class));
        then(scheduleLocationRepository).should().saveLocation(SCHEDULE_ID, MEMBER_ID, 1.0, 2.0);
        then(scheduleLocationRepository).should().updateArrivalStatus(SCHEDULE_ID, MEMBER_ID, true);

        then(publisher).should().publishEvent(memberArrivalEventCaptor.capture());
        then(publisher).should().publishEvent(scheduleCloseEventCaptor.capture());

        // MemberArrivalEvent 검증
        MemberArrivalEvent capturedMemberArrivalEvent = memberArrivalEventCaptor.getValue();
        assertThat(capturedMemberArrivalEvent.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(capturedMemberArrivalEvent.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedMemberArrivalEvent.getScheduleName()).isEqualTo(SCHEDULE_NAME);
        assertThat(capturedMemberArrivalEvent.getArrivalTime()).isEqualTo(NOW);

        // ScheduleCloseEvent 검증
        ScheduleCloseEvent capturedScheduleCloseEvent = scheduleCloseEventCaptor.getValue();
        assertThat(capturedScheduleCloseEvent.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedScheduleCloseEvent.getCloseTime()).isEqualTo(NOW);
    }

    @Test
    void makeMemberArrive_마지막아닌멤버_정상() {
        // Given
        Schedule schedule = mock(Schedule.class);
        ScheduleMember scheduleMember = mock(ScheduleMember.class);
        Location location = new Location("Location1", 1.0, 2.0);
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(true);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleRepository.findScheduleMember(MEMBER_ID, SCHEDULE_ID)).willReturn(Optional.of(scheduleMember));
        given(schedule.getLocation()).willReturn(location);
        given(schedule.getScheduleName()).willReturn(SCHEDULE_NAME);
        given(arrivalRepository.isAllMembersInScheduleArrived(SCHEDULE_ID)).willReturn(false);

        // When
        MemberArrivalDto dto = new MemberArrivalDto(NOW);
        Long res = mapService.makeMemberArrive(MEMBER_ID, SCHEDULE_ID, dto);

        // Then
        assertThat(res).isEqualTo(SCHEDULE_ID);
        then(arrivalRepository).should().save(any(Arrival.class));
        then(scheduleLocationRepository).should().saveLocation(SCHEDULE_ID, MEMBER_ID, 1.0, 2.0);
        then(scheduleLocationRepository).should().updateArrivalStatus(SCHEDULE_ID, MEMBER_ID, true);

        then(publisher).should().publishEvent(memberArrivalEventCaptor.capture());
        then(publisher).should(never()).publishEvent(any(ScheduleCloseEvent.class));

        // MemberArrivalEvent 검증
        MemberArrivalEvent capturedMemberArrivalEvent = memberArrivalEventCaptor.getValue();
        assertThat(capturedMemberArrivalEvent.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(capturedMemberArrivalEvent.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedMemberArrivalEvent.getScheduleName()).isEqualTo(SCHEDULE_NAME);
        assertThat(capturedMemberArrivalEvent.getArrivalTime()).isEqualTo(NOW);
    }

    @Test
    void makeMemberArrive_멤버없음_예외() {
        // Given
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(false);

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.makeMemberArrive(MEMBER_ID, SCHEDULE_ID, new MemberArrivalDto(NOW)));
    }

    @Test
    void makeMemberArrive_스케줄상태_예외() {
        // Given
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(false);

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.makeMemberArrive(MEMBER_ID, SCHEDULE_ID, new MemberArrivalDto(NOW)));
    }

    @Test
    void sendEmoji_정상() {
        // Given
        EmojiDto emojiDto = new EmojiDto(2L, EmojiType.HEART);
        AlarmMemberInfo sender = mock(AlarmMemberInfo.class);
        AlarmMemberInfo receiver = mock(AlarmMemberInfo.class);
        Schedule schedule = mock(Schedule.class);
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(true);
        given(memberRepository.findMemberInfo(MEMBER_ID)).willReturn(Optional.of(sender));
        given(memberRepository.findMemberInfo(2L)).willReturn(Optional.of(receiver));
        given(memberRepository.findMemberFirebaseTokenById(2L)).willReturn(Optional.of("token"));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(schedule.getScheduleName()).willReturn(SCHEDULE_NAME);

        // When
        Long res = mapService.sendEmoji(MEMBER_ID, SCHEDULE_ID, emojiDto);

        // Then
        assertThat(res).isEqualTo(SCHEDULE_ID);

        then(kafkaService).should().sendMessage(eq(KafkaTopic.ALARM), emojiMessageArgumentCaptor.capture());

        EmojiMessage capturedEmojiMessage = emojiMessageArgumentCaptor.getValue();
        assertThat(capturedEmojiMessage.getSenderInfo()).isEqualTo(sender);
        assertThat(capturedEmojiMessage.getReceiverInfo()).isEqualTo(receiver);
        assertThat(capturedEmojiMessage.getAlarmMessageType()).isEqualTo(AlarmMessageType.EMOJI);
        assertThat(capturedEmojiMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedEmojiMessage.getScheduleName()).isEqualTo(SCHEDULE_NAME);
    }

    @Test
    void sendEmoji_멤버없음_예외() {
        // Given
        EmojiDto emojiDto = new EmojiDto(2L, EmojiType.HEART);
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(false);

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.sendEmoji(MEMBER_ID, SCHEDULE_ID, emojiDto));
    }

    @Test
    void sendEmoji_스케줄상태_예외() {
        // Given
        EmojiDto emojiDto = new EmojiDto(2L, EmojiType.HEART);
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(false);

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.sendEmoji(MEMBER_ID, SCHEDULE_ID, emojiDto));
    }

    @Test
    void deleteAllLocationsInSchedule_정상() {
        // Given
        mapService.deleteAllLocationsInSchedule(SCHEDULE_ID);

        // When & Then
        then(scheduleLocationRepository).should().deleteScheduleLocations(SCHEDULE_ID);
    }

    @Test
    void makeNotArrivedMemberArrive_정상() {
        // Given
        ScheduleMember sm1 = mock(ScheduleMember.class);
        ScheduleMember sm2 = mock(ScheduleMember.class);
        List<ScheduleMember> notArrived = List.of(sm1, sm2);
        given(scheduleRepository.findScheduleMembersNotInArrivalByScheduleId(SCHEDULE_ID)).willReturn(notArrived);

        // When
        mapService.makeNotArrivedMemberArrive(SCHEDULE_ID, NOW);

        // Then
        then(arrivalRepository).should().saveAll(anyList());
    }

    @Test
    void sendKafkaAlarmIfMemberArrived_정상() {
        // Given
        List<String> tokens = List.of("token1", "token2");
        AlarmMemberInfo info = mock(AlarmMemberInfo.class);
        given(scheduleRepository.findAllFcmTokensInSchedule(SCHEDULE_ID)).willReturn(tokens);
        given(memberRepository.findMemberInfo(MEMBER_ID)).willReturn(Optional.of(info));

        // When
        mapService.sendKafkaAlarmIfMemberArrived(MEMBER_ID, SCHEDULE_ID, SCHEDULE_NAME, NOW);

        // Then
        then(kafkaService).should().sendMessage(eq(KafkaTopic.ALARM), alarmMessageArgumentCaptor.capture());

        ArrivalAlarmMessage capturedMessage = alarmMessageArgumentCaptor.getValue();
        assertThat(capturedMessage.getAlarmReceiverTokens()).isEqualTo(tokens);
        assertThat(capturedMessage.getAlarmMessageType()).isEqualTo(AlarmMessageType.MEMBER_ARRIVAL);
        assertThat(capturedMessage.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(capturedMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedMessage.getScheduleName()).isEqualTo(SCHEDULE_NAME);
        assertThat(capturedMessage.getArrivalTime()).isEqualTo(NOW);
        assertThat(capturedMessage.getArriveMemberInfo()).isEqualTo(info);

    }

    @Test
    void sendKafkaEventIfScheduleClosed_정상() {
        // Given
        mapService.sendKafkaEventIfScheduleClosed(SCHEDULE_ID, NOW);

        // When & Then
        then(kafkaService).should().sendMessage(eq(KafkaTopic.SCHEDULE_CLOSE), any(ScheduleCloseMessage.class));
    }

    @Test
    void findSchedule_없으면_예외() {
        // Given
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(ScheduleException.class, () -> {
            // private 메서드 테스트를 위해 public 메서드를 호출
            mapService.getScheduleDetail(MEMBER_ID, SCHEDULE_ID);
        });
    }

    @Test
    void findScheduleMember_없으면_예외() {
        // Given
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(true);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(mock(Schedule.class)));
        given(scheduleRepository.findScheduleMember(MEMBER_ID, SCHEDULE_ID)).willReturn(Optional.empty());

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.makeMemberArrive(MEMBER_ID, SCHEDULE_ID, new MemberArrivalDto(NOW)));
    }

    @Test
    void checkMemberInSchedule_없으면_예외() {
        // Given
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(false);

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.getAllLocation(MEMBER_ID, SCHEDULE_ID));
    }

    @Test
    void checkScheduleInRun_없으면_예외() {
        // Given
        given(scheduleRepository.existMemberInSchedule(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.existsByIdAndScheduleStatusAndStatus(SCHEDULE_ID, RUN, Status.ALIVE)).willReturn(false);

        // When & Then
        assertThrows(ScheduleException.class, () -> mapService.makeMemberArrive(MEMBER_ID, SCHEDULE_ID, new MemberArrivalDto(NOW)));
    }
}
