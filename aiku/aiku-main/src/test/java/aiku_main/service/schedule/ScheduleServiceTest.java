package aiku_main.service.schedule;

import aiku_main.application_event.event.PointChangeEvent;
import aiku_main.application_event.event.ScheduleCloseEvent;
import aiku_main.application_event.event.ScheduleExitEvent;
import aiku_main.dto.LocationDto;
import aiku_main.dto.MonthDto;
import aiku_main.dto.SearchDateCond;
import aiku_main.dto.SimpleResDto;
import aiku_main.dto.schedule.*;
import aiku_main.exception.ScheduleException;
import aiku_main.exception.TeamException;
import aiku_main.kafka.KafkaProducerService;
import aiku_main.repository.arrival.ArrivalRepository;
import aiku_main.repository.member.MemberRepository;
import aiku_main.repository.schedule.ScheduleRepository;
import aiku_main.repository.team.TeamRepository;
import aiku_main.scheduler.ScheduleScheduler;
import common.domain.Location;
import common.domain.member.Member;
import common.domain.schedule.Schedule;
import common.domain.schedule.ScheduleMember;
import common.domain.schedule.ScheduleResult;
import common.domain.value_reference.TeamValue;
import common.exception.NotEnoughPointException;
import common.kafka_message.alarm.AlarmMessageType;
import common.kafka_message.alarm.ScheduleAlarmMessage;
import common.kafka_message.alarm.ScheduleMemberAlarmMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static aiku_main.application_event.event.PointChangeReason.*;
import static aiku_main.application_event.event.PointChangeType.*;
import static common.domain.ExecStatus.RUN;
import static common.domain.ExecStatus.WAIT;
import static common.domain.Status.ALIVE;
import static common.kafka_message.KafkaTopic.ALARM;
import static common.kafka_message.alarm.AlarmMessageType.SCHEDULE_OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    ScheduleRepository scheduleRepository;
    @Mock
    ArrivalRepository arrivalRepository;
    @Mock
    TeamRepository teamRepository;
    @Mock
    ScheduleScheduler scheduleScheduler;
    @Mock
    KafkaProducerService kafkaProducerService;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ScheduleService scheduleService;

    @Captor
    ArgumentCaptor<PointChangeEvent> pointEventCaptor;
    @Captor
    ArgumentCaptor<ScheduleAlarmMessage> scheduleAlarmMessageCaptor;
    @Captor
    ArgumentCaptor<ScheduleMemberAlarmMessage> scheduleMemberAlarmMessageCaptor;
    @Captor
    ArgumentCaptor<ScheduleExitEvent> exitEventCaptor;

    private static final Long MEMBER_ID = 1L;
    private static final Long TEAM_ID = 10L;
    private static final Long SCHEDULE_ID = 100L;
    private static final int SCHEDULE_ENTER_POINT = 100;
    private Member member;
    private Schedule schedule;
    private ScheduleMember ownerScheduleMember;
    private ScheduleMember participantScheduleMember;

    @BeforeEach
    void setUp() {
        member = mock(Member.class);
        schedule = mock(Schedule.class);
        ownerScheduleMember = mock(ScheduleMember.class);
        participantScheduleMember = mock(ScheduleMember.class);

        ReflectionTestUtils.setField(scheduleService, "scheduleEnterPoint", SCHEDULE_ENTER_POINT);
    }

    @Test
    void addSchedule() {
        // given
        ScheduleAddDto scheduleAddDto = createScheduleAddDto();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(true);
        given(member.getPoint()).willReturn(SCHEDULE_ENTER_POINT + 100); // 충분한 포인트 반환

        try (MockedStatic<Schedule> mockedSchedule = mockStatic(Schedule.class)) {
            mockedSchedule.when(() -> Schedule.create(
                    eq(member),
                    any(TeamValue.class),
                    eq(scheduleAddDto.getScheduleName()),
                    eq(scheduleAddDto.getScheduleTime()),
                    any(Location.class),
                    eq(SCHEDULE_ENTER_POINT)
            )).thenReturn(schedule);
            given(schedule.getId()).willReturn(SCHEDULE_ID);

            // when
            Long resultId = scheduleService.addSchedule(MEMBER_ID, TEAM_ID, scheduleAddDto);

            // then
            assertThat(resultId).isEqualTo(SCHEDULE_ID);
            then(scheduleRepository).should().save(schedule);
            then(scheduleScheduler).should().reserveSchedule(schedule);

            then(eventPublisher).should().publishEvent(pointEventCaptor.capture());
            PointChangeEvent capturedPointEvent = pointEventCaptor.getValue();
            assertThat(capturedPointEvent.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(capturedPointEvent.getSign()).isEqualTo(MINUS);
            assertThat(capturedPointEvent.getReason()).isEqualTo(SCHEDULE_ENTER);
            assertThat(capturedPointEvent.getReasonId()).isEqualTo(SCHEDULE_ID);
            assertThat(capturedPointEvent.getPointAmount()).isEqualTo(SCHEDULE_ENTER_POINT);
            then(teamRepository).should().findAlarmTokenListOfTeamMembers(TEAM_ID, MEMBER_ID);
            then(kafkaProducerService).should().sendMessage(eq(ALARM), scheduleAlarmMessageCaptor.capture());

            assertThat(scheduleAlarmMessageCaptor.getValue().getScheduleId()).isEqualTo(SCHEDULE_ID);
        }
    }

    @Test
    void addSchedule_포인트_부족() {
        // given
        ScheduleAddDto scheduleAddDto = createScheduleAddDto();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(true);
        given(member.getPoint()).willReturn(SCHEDULE_ENTER_POINT - 10); // 포인트 부족

        // when
        assertThatThrownBy(() -> scheduleService.addSchedule(MEMBER_ID, TEAM_ID, scheduleAddDto))
                .isInstanceOf(NotEnoughPointException.class);
    }

    @Test
    void addSchedule_그룹_멤버x() {
        // given
        ScheduleAddDto scheduleAddDto = createScheduleAddDto();
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(false);

        // when
        assertThatThrownBy(() -> scheduleService.addSchedule(MEMBER_ID, TEAM_ID, scheduleAddDto))
                .isInstanceOf(TeamException.class);
    }

    ScheduleAddDto createScheduleAddDto(){
        return new ScheduleAddDto(
                "모임1",
                new LocationDto("서울", 1.1, 1.1),
                LocalDateTime.now().plusHours(2));
    }

    @Test
    void updateSchedule() {
        // given
        ScheduleUpdateDto scheduleUpdateDto = createScheduleUpdateDto();
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(schedule.isWait()).willReturn(true);
        given(schedule.getScheduleTime()).willReturn(LocalDateTime.now().plusHours(2)); // 60분 이상 남음
        given(scheduleRepository.isScheduleOwner(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(schedule.getId()).willReturn(SCHEDULE_ID);

        // when
        Long resultId = scheduleService.updateSchedule(MEMBER_ID, SCHEDULE_ID, scheduleUpdateDto);

        // then
        assertThat(resultId).isEqualTo(SCHEDULE_ID);
        then(schedule).should().update(
                eq(scheduleUpdateDto.getScheduleName()),
                eq(scheduleUpdateDto.getScheduleTime()),
                any(Location.class)
        );
        then(scheduleScheduler).should().changeSchedule(schedule);
        then(scheduleRepository).should().findAlarmTokenListOfScheduleMembers(SCHEDULE_ID, MEMBER_ID);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), scheduleAlarmMessageCaptor.capture());

        assertThat(scheduleAlarmMessageCaptor.getValue().getScheduleId()).isEqualTo(SCHEDULE_ID);
    }

    @Test
    void updateSchedule_스케줄_상태가_wait_아님() {
        // given
        ScheduleUpdateDto scheduleUpdateDto = createScheduleUpdateDto();
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(schedule.isWait()).willReturn(false); // 대기 상태 아님

        // when
        assertThatThrownBy(() -> scheduleService.updateSchedule(MEMBER_ID, SCHEDULE_ID, scheduleUpdateDto))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void updateSchedule_스케줄_변경가능시간_아님() {
        // given
        ScheduleUpdateDto scheduleUpdateDto = createScheduleUpdateDto();
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(schedule.isWait()).willReturn(true);
        given(schedule.getScheduleTime()).willReturn(LocalDateTime.now().plusMinutes(30)); // 60분 미만

        // when
        assertThatThrownBy(() -> scheduleService.updateSchedule(MEMBER_ID, SCHEDULE_ID, scheduleUpdateDto))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void updateSchedule_스케줄장_아님() {
        // given
        ScheduleUpdateDto scheduleUpdateDto = createScheduleUpdateDto();
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(schedule.isWait()).willReturn(true);
        given(schedule.getScheduleTime()).willReturn(LocalDateTime.now().plusHours(2));
        given(scheduleRepository.isScheduleOwner(MEMBER_ID, SCHEDULE_ID)).willReturn(false); // 스케줄장 아님

        // when
        assertThatThrownBy(() -> scheduleService.updateSchedule(MEMBER_ID, SCHEDULE_ID, scheduleUpdateDto))
                .isInstanceOf(ScheduleException.class);
    }

    ScheduleUpdateDto createScheduleUpdateDto(){
        return new ScheduleUpdateDto(
                "수정된 모임",
                new LocationDto("수정된 장소", 2.2, 2.2),
                LocalDateTime.now().plusHours(10));
    }

    @Test
    void enterSchedule() {
        // given
        Long participantMemberId = 5L;
        Member participantMember = mock(Member.class);

        given(teamRepository.existTeamMember(participantMemberId, TEAM_ID)).willReturn(true);
        given(memberRepository.findById(participantMemberId)).willReturn(Optional.of(participantMember));
        given(participantMember.getPoint()).willReturn(SCHEDULE_ENTER_POINT + 100);
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(scheduleRepository.existScheduleMember(participantMemberId, SCHEDULE_ID)).willReturn(false); // 아직 참여 안함
        given(schedule.isWait()).willReturn(true);
        given(schedule.getId()).willReturn(SCHEDULE_ID);

        // when
        Long resultId = scheduleService.enterSchedule(participantMemberId, TEAM_ID, SCHEDULE_ID);

        // then
        assertThat(resultId).isEqualTo(SCHEDULE_ID);
        then(schedule).should().addScheduleMember(participantMember, false, SCHEDULE_ENTER_POINT);
        then(eventPublisher).should().publishEvent(pointEventCaptor.capture());

        PointChangeEvent capturedPointEvent = pointEventCaptor.getValue();
        assertThat(capturedPointEvent.getMemberId()).isEqualTo(participantMemberId);
        assertThat(capturedPointEvent.getSign()).isEqualTo(MINUS);
        assertThat(capturedPointEvent.getPointAmount()).isEqualTo(SCHEDULE_ENTER_POINT);
        assertThat(capturedPointEvent.getReason()).isEqualTo(SCHEDULE_ENTER);
        assertThat(capturedPointEvent.getReasonId()).isEqualTo(SCHEDULE_ID);

        then(scheduleRepository).should().findAlarmTokenListOfScheduleMembers(SCHEDULE_ID, participantMemberId);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), scheduleMemberAlarmMessageCaptor.capture());

        ScheduleMemberAlarmMessage capturedAlarmMessage = scheduleMemberAlarmMessageCaptor.getValue();
        assertThat(capturedAlarmMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedAlarmMessage.getSourceMember().getMemberId()).isEqualTo(participantMember.getId());
    }

    @Test
    void enterSchedule_이미_참가중인_멤버() {
        // given
        Long participantMemberId = 5L;
        Member participantMember = mock(Member.class);

        given(teamRepository.existTeamMember(participantMemberId, TEAM_ID)).willReturn(true);
        given(memberRepository.findById(participantMemberId)).willReturn(Optional.of(participantMember));
        given(participantMember.getPoint()).willReturn(SCHEDULE_ENTER_POINT + 100);
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(scheduleRepository.existScheduleMember(participantMemberId, SCHEDULE_ID)).willReturn(true); // 이미 참여

        // when
        assertThatThrownBy(() -> scheduleService.enterSchedule(participantMemberId, TEAM_ID, SCHEDULE_ID))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void enterSchedule_포인트_부족() {
        // given
        Long participantMemberId = 2L;
        Member participantMember = mock(Member.class);
        given(teamRepository.existTeamMember(participantMemberId, TEAM_ID)).willReturn(true);
        given(memberRepository.findById(participantMemberId)).willReturn(Optional.of(participantMember));
        given(participantMember.getPoint()).willReturn(SCHEDULE_ENTER_POINT - 10);

        // when
        assertThatThrownBy(() -> scheduleService.enterSchedule(participantMemberId, TEAM_ID, SCHEDULE_ID))
                .isInstanceOf(NotEnoughPointException.class);
    }

    @Test
    void enterSchedule_스케줄_상태가_wait_아님() {
        // given
        Long participantMemberId = 2L;
        Member participantMember = mock(Member.class);

        given(teamRepository.existTeamMember(participantMemberId, TEAM_ID)).willReturn(true);
        given(memberRepository.findById(participantMemberId)).willReturn(Optional.of(participantMember));
        given(participantMember.getPoint()).willReturn(SCHEDULE_ENTER_POINT + 100);
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(scheduleRepository.existScheduleMember(participantMemberId, SCHEDULE_ID)).willReturn(false);
        given(schedule.isWait()).willReturn(false);

        // when
        assertThatThrownBy(() -> scheduleService.enterSchedule(participantMemberId, TEAM_ID, SCHEDULE_ID))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void exitSchedule_퇴장멤버가_방장일때_남은_참가자_있음() {
        // given
        Long ownerMemberId = MEMBER_ID;
        Long ownerScheduleMemberId = 3L;
        Member nextOwner = mock(Member.class);
        ScheduleMember nextOwnerScheduleMember = mock(ScheduleMember.class);

        given(memberRepository.findById(ownerMemberId)).willReturn(Optional.of(member));
        given(scheduleRepository.findScheduleMember(ownerMemberId, SCHEDULE_ID)).willReturn(Optional.of(ownerScheduleMember));
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(schedule.getId()).willReturn(SCHEDULE_ID);
        given(schedule.isWait()).willReturn(true);
        given(scheduleRepository.countOfScheduleMembers(SCHEDULE_ID)).willReturn(5L); // 마지막 멤버 아님
        given(ownerScheduleMember.isOwner()).willReturn(true); // 스케줄장
        given(ownerScheduleMember.getId()).willReturn(ownerScheduleMemberId);

        given(scheduleRepository.findNextScheduleOwnerWithMember(SCHEDULE_ID, ownerScheduleMemberId))
                .willReturn(Optional.of(nextOwnerScheduleMember));
        given(nextOwnerScheduleMember.getMember()).willReturn(nextOwner);
        given(nextOwner.getFirebaseToken()).willReturn("token");

        ArgumentCaptor<ScheduleAlarmMessage> ownerAlarmCaptor = ArgumentCaptor.forClass(ScheduleAlarmMessage.class);
        ArgumentCaptor<ScheduleMemberAlarmMessage> exitAlarmCaptor = ArgumentCaptor.forClass(ScheduleMemberAlarmMessage.class);

        // when
        Long resultId = scheduleService.exitSchedule(ownerMemberId, TEAM_ID, SCHEDULE_ID);

        // then
        assertThat(resultId).isEqualTo(SCHEDULE_ID);
        then(schedule).should().changeScheduleOwner(nextOwnerScheduleMember);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), ownerAlarmCaptor.capture());

        ScheduleAlarmMessage capturedOwnerAlarmMessage = ownerAlarmCaptor.getValue();
        assertThat(capturedOwnerAlarmMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedOwnerAlarmMessage.getAlarmMessageType()).isEqualTo(SCHEDULE_OWNER);

        then(schedule).should().removeScheduleMember(ownerScheduleMember);
        then(scheduleRepository).should().findAlarmTokenListOfScheduleMembers(SCHEDULE_ID, ownerMemberId);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), exitAlarmCaptor.capture());

        ScheduleMemberAlarmMessage capturedExitAlarmMessage = exitAlarmCaptor.getValue();
        assertThat(capturedExitAlarmMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedExitAlarmMessage.getAlarmMessageType()).isEqualTo(AlarmMessageType.SCHEDULE_EXIT);

        then(eventPublisher).should().publishEvent(pointEventCaptor.capture());

        PointChangeEvent capturedPointEvent = pointEventCaptor.getValue();
        assertThat(capturedPointEvent.getMemberId()).isEqualTo(ownerMemberId);
        assertThat(capturedPointEvent.getSign()).isEqualTo(PLUS);
        assertThat(capturedPointEvent.getPointAmount()).isEqualTo(SCHEDULE_ENTER_POINT);
        assertThat(capturedPointEvent.getReason()).isEqualTo(SCHEDULE_EXIT);
        assertThat(capturedPointEvent.getReasonId()).isEqualTo(SCHEDULE_ID);

        then(eventPublisher).should().publishEvent(exitEventCaptor.capture());

        ScheduleExitEvent capturedExitEvent = exitEventCaptor.getValue();
        assertThat(capturedExitEvent.getMemberId()).isEqualTo(ownerMemberId);
        assertThat(capturedExitEvent.getScheduleMemberId()).isEqualTo(ownerScheduleMember.getId());
        assertThat(capturedExitEvent.getScheduleId()).isEqualTo(SCHEDULE_ID);
    }

    @Test
    void exitSchedule_마지막_멤버가_퇴장() {
        // given
        Long ownerMemberId = MEMBER_ID;
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(scheduleRepository.findScheduleMember(MEMBER_ID, SCHEDULE_ID)).willReturn(Optional.of(ownerScheduleMember));
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(schedule.getId()).willReturn(SCHEDULE_ID);
        given(schedule.isWait()).willReturn(true);
        given(scheduleRepository.countOfScheduleMembers(SCHEDULE_ID)).willReturn(1L); // 마지막 멤버

        ArgumentCaptor<ScheduleMemberAlarmMessage> exitAlarmCaptor = ArgumentCaptor.forClass(ScheduleMemberAlarmMessage.class);

        // when
        Long resultId = scheduleService.exitSchedule(MEMBER_ID, TEAM_ID, SCHEDULE_ID);

        // then
        assertThat(resultId).isEqualTo(SCHEDULE_ID);
        then(schedule).should().delete();
        then(schedule).should().removeScheduleMember(ownerScheduleMember);

        then(scheduleRepository).should().findAlarmTokenListOfScheduleMembers(SCHEDULE_ID, MEMBER_ID);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), exitAlarmCaptor.capture());

        ScheduleMemberAlarmMessage capturedExitAlarmMessage = exitAlarmCaptor.getValue();
        assertThat(capturedExitAlarmMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedExitAlarmMessage.getAlarmMessageType()).isEqualTo(AlarmMessageType.SCHEDULE_EXIT);

        then(eventPublisher).should().publishEvent(pointEventCaptor.capture());

        PointChangeEvent capturedPointEvent = pointEventCaptor.getValue();
        assertThat(capturedPointEvent.getMemberId()).isEqualTo(ownerMemberId);
        assertThat(capturedPointEvent.getSign()).isEqualTo(PLUS);
        assertThat(capturedPointEvent.getPointAmount()).isEqualTo(SCHEDULE_ENTER_POINT);
        assertThat(capturedPointEvent.getReason()).isEqualTo(SCHEDULE_EXIT);
        assertThat(capturedPointEvent.getReasonId()).isEqualTo(SCHEDULE_ID);

        then(eventPublisher).should().publishEvent(exitEventCaptor.capture());

        ScheduleExitEvent capturedExitEvent = exitEventCaptor.getValue();
        assertThat(capturedExitEvent.getMemberId()).isEqualTo(ownerMemberId);
        assertThat(capturedExitEvent.getScheduleMemberId()).isEqualTo(ownerScheduleMember.getId());
        assertThat(capturedExitEvent.getScheduleId()).isEqualTo(SCHEDULE_ID);
    }

    @Test
    void exitSchedule_스케줄장_아님_마지막멤버_아님() {
        // given
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(scheduleRepository.findScheduleMember(MEMBER_ID, SCHEDULE_ID)).willReturn(Optional.of(participantScheduleMember));
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(schedule.getId()).willReturn(SCHEDULE_ID);
        given(schedule.isWait()).willReturn(true);
        given(scheduleRepository.countOfScheduleMembers(SCHEDULE_ID)).willReturn(5L); // 마지막 멤버 아님
        given(participantScheduleMember.isOwner()).willReturn(false);

        ArgumentCaptor<ScheduleMemberAlarmMessage> exitAlarmCaptor = ArgumentCaptor.forClass(ScheduleMemberAlarmMessage.class);

        // when
        Long resultId = scheduleService.exitSchedule(MEMBER_ID, TEAM_ID, SCHEDULE_ID);

        // then
        assertThat(resultId).isEqualTo(SCHEDULE_ID);
        then(schedule).should().removeScheduleMember(participantScheduleMember);
        then(scheduleRepository).should().findAlarmTokenListOfScheduleMembers(SCHEDULE_ID, MEMBER_ID);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), exitAlarmCaptor.capture());

        ScheduleMemberAlarmMessage capturedExitAlarmMessage = exitAlarmCaptor.getValue();
        assertThat(capturedExitAlarmMessage.getScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(capturedExitAlarmMessage.getAlarmMessageType()).isEqualTo(AlarmMessageType.SCHEDULE_EXIT);

        then(eventPublisher).should().publishEvent(pointEventCaptor.capture());

        PointChangeEvent capturedPointEvent = pointEventCaptor.getValue();
        assertThat(capturedPointEvent.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(capturedPointEvent.getSign()).isEqualTo(PLUS);
        assertThat(capturedPointEvent.getPointAmount()).isEqualTo(SCHEDULE_ENTER_POINT);
        assertThat(capturedPointEvent.getReason()).isEqualTo(SCHEDULE_EXIT);
        assertThat(capturedPointEvent.getReasonId()).isEqualTo(SCHEDULE_ID);

        then(eventPublisher).should().publishEvent(exitEventCaptor.capture());

        ScheduleExitEvent capturedExitEvent = exitEventCaptor.getValue();
        assertThat(capturedExitEvent.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(capturedExitEvent.getScheduleMemberId()).isEqualTo(ownerScheduleMember.getId());
        assertThat(capturedExitEvent.getScheduleId()).isEqualTo(SCHEDULE_ID);
    }

    @Test
    void closeSchedule() {
        // given
        Long teamId = 1L;
        LocalDateTime closeTime = LocalDateTime.of(2025, 6, 1, 10, 0);
        TeamValue mockTeamValue = mock(TeamValue.class);

        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(arrivalRepository.findArrivalsOfSchedule(SCHEDULE_ID)).willReturn(List.of());
        given(schedule.getTeam()).willReturn(mockTeamValue);
        given(mockTeamValue.getId()).willReturn(teamId);

        ArgumentCaptor<ScheduleCloseEvent> eventCaptor = ArgumentCaptor.forClass(ScheduleCloseEvent.class);

        // when
        scheduleService.closeSchedule(SCHEDULE_ID, closeTime);

        // then
        then(schedule).should().close(eq(closeTime), any(Map.class));
        then(eventPublisher).should().publishEvent(eventCaptor.capture());

        ScheduleCloseEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getTeamId()).isEqualTo(teamId);
        assertThat(capturedEvent.getScheduleId()).isEqualTo(SCHEDULE_ID);
    }

    @Test
    void getScheduleDetail() {
        // given
        Location location = mock(Location.class);
        List<ScheduleMemberResDto> scheduleMemberResDtos = List.of(
                mock(ScheduleMemberResDto.class),
                mock(ScheduleMemberResDto.class)
        );

        given(schedule.getId()).willReturn(SCHEDULE_ID);
        given(schedule.getLocation()).willReturn(location);
        given(location.getLocationName()).willReturn("name");

        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(scheduleRepository.existScheduleMember(MEMBER_ID, SCHEDULE_ID)).willReturn(true);
        given(scheduleRepository.getScheduleMembersWithBettingInfo(MEMBER_ID, SCHEDULE_ID))
                .willReturn(scheduleMemberResDtos);

        // when
        ScheduleDetailResDto result = scheduleService.getScheduleDetail(MEMBER_ID, TEAM_ID, SCHEDULE_ID);

        // then
        assertThat(result.getMembers()).hasSize(2);
    }

    @Test
    void getScheduleDetail_스케줄_멤버_아님() {
        // given
        given(scheduleRepository.findByIdAndStatus(SCHEDULE_ID, ALIVE)).willReturn(Optional.of(schedule));
        given(scheduleRepository.existScheduleMember(MEMBER_ID, SCHEDULE_ID)).willReturn(false);

        // when
        assertThatThrownBy(() -> scheduleService.getScheduleDetail(MEMBER_ID, TEAM_ID, SCHEDULE_ID))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void getSchedulePreview() {
        // given
        SchedulePreviewResDto expectedDto = mock(SchedulePreviewResDto.class);
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(true);
        given(scheduleRepository.getSchedulePreview(SCHEDULE_ID)).willReturn(expectedDto);

        // when
        SchedulePreviewResDto result = scheduleService.getSchedulePreview(MEMBER_ID, TEAM_ID, SCHEDULE_ID);

        // then
        assertThat(result).isEqualTo(expectedDto);
    }

    @Test
    void getSchedulePreview_팀멤버_아님() {
        // given
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(false);

        // when
        assertThatThrownBy(() -> scheduleService.getSchedulePreview(MEMBER_ID, TEAM_ID, SCHEDULE_ID))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void getTeamScheduleList() {
        // given
        int page = 0;
        SearchDateCond dateCond = new SearchDateCond();
        List<TeamScheduleListEachResDto> scheduleResDtoList = List.of(
                mock(TeamScheduleListEachResDto.class),
                mock(TeamScheduleListEachResDto.class)
        );
        int waitScheduleCount = 2;
        int runScheduleCount = 3;

        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(true);
        given(scheduleRepository.getTeamSchedules(TEAM_ID, MEMBER_ID, dateCond, page)).willReturn(scheduleResDtoList);
        given(scheduleRepository.countTeamScheduleByScheduleStatus(TEAM_ID, RUN, dateCond)).willReturn(runScheduleCount);
        given(scheduleRepository.countTeamScheduleByScheduleStatus(TEAM_ID, WAIT, dateCond)).willReturn(waitScheduleCount);

        // when
        TeamScheduleListResDto result = scheduleService.getTeamScheduleList(MEMBER_ID, TEAM_ID, dateCond, page);

        // then
        assertThat(result.getPage()).isEqualTo(page);
        assertThat(result.getGroupId()).isEqualTo(TEAM_ID);
        assertThat(result.getRunSchedule()).isEqualTo(runScheduleCount);
        assertThat(result.getWaitSchedule()).isEqualTo(waitScheduleCount);
        assertThat(result.getData()).hasSize(2);
        then(teamRepository).should().existTeamMember(MEMBER_ID, TEAM_ID);
        then(scheduleRepository).should().getTeamSchedules(TEAM_ID, MEMBER_ID, dateCond, page);
        then(scheduleRepository).should().countTeamScheduleByScheduleStatus(TEAM_ID, RUN, dateCond);
        then(scheduleRepository).should().countTeamScheduleByScheduleStatus(TEAM_ID, WAIT, dateCond);
    }

    @Test
    void getTeamScheduleList_팀멤버_아님() {
        // given
        int page = 0;
        SearchDateCond dateCond = new SearchDateCond();
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(false);

        // when
        assertThatThrownBy(() -> scheduleService.getTeamScheduleList(MEMBER_ID, TEAM_ID, dateCond, page))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void getMemberScheduleList() {
        // given
        int page = 0;
        SearchDateCond dateCond = new SearchDateCond();

        given(scheduleRepository.getMemberSchedules(MEMBER_ID, dateCond, page)).willReturn(List.of());
        given(scheduleRepository.countMemberScheduleByScheduleStatus(MEMBER_ID, RUN, dateCond)).willReturn(1);
        given(scheduleRepository.countMemberScheduleByScheduleStatus(MEMBER_ID, WAIT, dateCond)).willReturn(1);

        // when
        MemberScheduleListResDto result = scheduleService.getMemberScheduleList(MEMBER_ID, dateCond, page);

        // then
        assertThat(result.getPage()).isEqualTo(page);
        assertThat(result.getRunSchedule()).isEqualTo(1);
        assertThat(result.getWaitSchedule()).isEqualTo(1);
        then(scheduleRepository).should().getMemberSchedules(MEMBER_ID, dateCond, page);
        then(scheduleRepository).should().countMemberScheduleByScheduleStatus(MEMBER_ID, RUN, dateCond);
        then(scheduleRepository).should().countMemberScheduleByScheduleStatus(MEMBER_ID, WAIT, dateCond);
    }

    @Test
    void getScheduleArrivalResult() {
        // given
        String expectedResult = "arrival result";
        ScheduleResult mockScheduleResult = mock(ScheduleResult.class);
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(true);
        given(scheduleRepository.findScheduleResult(SCHEDULE_ID)).willReturn(Optional.of(mockScheduleResult));
        given(mockScheduleResult.getScheduleArrivalResult()).willReturn(expectedResult);

        // when
        String result = scheduleService.getScheduleArrivalResult(MEMBER_ID, TEAM_ID, SCHEDULE_ID);

        // then
        assertThat(result).isEqualTo(expectedResult);
        then(mockScheduleResult).should().getScheduleArrivalResult();
    }

    @Test
    void getScheduleArrivalResult_팀멤버_아님() {
        // given
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(false);

        // when
        assertThatThrownBy(() -> scheduleService.getScheduleArrivalResult(MEMBER_ID, TEAM_ID, SCHEDULE_ID))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void getScheduleBettingResult() {
        // given
        String expectedResult = "betting result";
        ScheduleResult mockScheduleResult = mock(ScheduleResult.class);
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(true);
        given(scheduleRepository.findScheduleResult(SCHEDULE_ID)).willReturn(Optional.of(mockScheduleResult));
        given(mockScheduleResult.getScheduleBettingResult()).willReturn(expectedResult);

        // when
        String result = scheduleService.getScheduleBettingResult(MEMBER_ID, TEAM_ID, SCHEDULE_ID);

        // then
        assertThat(result).isEqualTo(expectedResult);
        then(mockScheduleResult).should().getScheduleBettingResult();
    }

    @Test
    void getScheduleBettingResult_팀멤버_아님() {
        // given
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(false);

        // when
        assertThatThrownBy(() -> scheduleService.getScheduleBettingResult(MEMBER_ID, TEAM_ID, SCHEDULE_ID))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void getScheduleRacingResult() {
        // given
        String expectedResult = "racing result";
        ScheduleResult mockScheduleResult = mock(ScheduleResult.class);
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(true);
        given(scheduleRepository.findScheduleResult(SCHEDULE_ID)).willReturn(Optional.of(mockScheduleResult));
        given(mockScheduleResult.getScheduleRacingResult()).willReturn(expectedResult);

        // when
        String result = scheduleService.getScheduleRacingResult(MEMBER_ID, TEAM_ID, SCHEDULE_ID);

        // then
        assertThat(result).isEqualTo(expectedResult);
        then(mockScheduleResult).should().getScheduleRacingResult();
    }

    @Test
    void getScheduleRacingResult_팀멤버_아님() {
        // given
        given(teamRepository.existTeamMember(MEMBER_ID, TEAM_ID)).willReturn(false);

        // when
        assertThatThrownBy(() -> scheduleService.getScheduleRacingResult(MEMBER_ID, TEAM_ID, SCHEDULE_ID))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void getScheduleDatesInMonth() {
        // given
        MonthDto monthDto = new MonthDto(2025, 6);
        List<LocalDateTime> rawDates = List.of(
                LocalDateTime.of(2025, 6, 1, 10, 0),
                LocalDateTime.of(2025, 6, 15, 12, 0),
                LocalDateTime.of(2025, 6, 1, 15, 0)
        );
        List<LocalDate> expectedDates = List.of(
                LocalDate.of(2025, 6, 1),
                LocalDate.of(2025, 6, 15)
        );
        given(scheduleRepository.findScheduleDatesInMonth(MEMBER_ID, monthDto.getYear(), monthDto.getMonth())).willReturn(rawDates);

        // when
        SimpleResDto<List<LocalDate>> result = scheduleService.getScheduleDatesInMonth(MEMBER_ID, monthDto);

        // then
        assertThat(result.getData()).containsExactlyInAnyOrderElementsOf(expectedDates);
    }

    @Test
    void exitAllScheduleInTeam() {
        // given
        Long scheduleId1 = SCHEDULE_ID;
        Long scheduleId2 = SCHEDULE_ID + 2;
        Long scheduleMemberId1 = 200L;
        Long scheduleMemberId2 = 201L;

        Schedule mockSchedule1 = mock(Schedule.class);
        Schedule mockSchedule2 = mock(Schedule.class);
        Member mockMember1 = mock(Member.class);

        ScheduleMember mockScheduleMember1 = mock(ScheduleMember.class);
        ScheduleMember mockScheduleMember2 = mock(ScheduleMember.class);

        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(mockMember1));
        given(scheduleRepository.findWaitScheduleMemberWithScheduleInTeam(MEMBER_ID, TEAM_ID))
                .willReturn(List.of(mockScheduleMember1, mockScheduleMember2));

        given(mockScheduleMember1.getSchedule()).willReturn(mockSchedule1);
        given(mockScheduleMember1.getId()).willReturn(scheduleMemberId1);
        given(mockSchedule1.getId()).willReturn(scheduleId1);

        given(mockScheduleMember2.getSchedule()).willReturn(mockSchedule2);
        given(mockScheduleMember2.getId()).willReturn(scheduleMemberId2);
        given(mockSchedule2.getId()).willReturn(scheduleId2);

        // when
        scheduleService.exitAllScheduleInTeam(MEMBER_ID, TEAM_ID);

        // then
        then(scheduleRepository).should().findWaitScheduleMemberWithScheduleInTeam(MEMBER_ID, TEAM_ID);

        then(mockSchedule1).should().removeScheduleMember(mockScheduleMember1);
        then(mockSchedule2).should().removeScheduleMember(mockScheduleMember2);

        then(eventPublisher).should(times(2)).publishEvent(pointEventCaptor.capture());
        List<PointChangeEvent> capturedPointEvents = pointEventCaptor.getAllValues();

        assertThat(capturedPointEvents).hasSize(2);

        PointChangeEvent pointEvent1 = capturedPointEvents.get(0);
        assertThat(pointEvent1.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(pointEvent1.getSign()).isEqualTo(PLUS);
        assertThat(pointEvent1.getPointAmount()).isEqualTo(SCHEDULE_ENTER_POINT);
        assertThat(pointEvent1.getReason()).isEqualTo(SCHEDULE_EXIT);
        assertThat(pointEvent1.getReasonId()).isEqualTo(scheduleId1);

        PointChangeEvent pointEvent2 = capturedPointEvents.get(1);
        assertThat(pointEvent2.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(pointEvent2.getSign()).isEqualTo(PLUS);
        assertThat(pointEvent2.getPointAmount()).isEqualTo(SCHEDULE_ENTER_POINT);
        assertThat(pointEvent2.getReason()).isEqualTo(SCHEDULE_EXIT);
        assertThat(pointEvent2.getReasonId()).isEqualTo(scheduleId2);


        then(eventPublisher).should(times(2)).publishEvent(exitEventCaptor.capture());
        List<ScheduleExitEvent> capturedExitEvents = exitEventCaptor.getAllValues();

        assertThat(capturedExitEvents).hasSize(2);

        ScheduleExitEvent exitEvent1 = capturedExitEvents.get(0);
        assertThat(exitEvent1.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(exitEvent1.getScheduleMemberId()).isEqualTo(scheduleMemberId1);
        assertThat(exitEvent1.getScheduleId()).isEqualTo(scheduleId1);

        ScheduleExitEvent exitEvent2 = capturedExitEvents.get(1);
        assertThat(exitEvent2.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(exitEvent2.getScheduleMemberId()).isEqualTo(scheduleMemberId2);
        assertThat(exitEvent2.getScheduleId()).isEqualTo(scheduleId2);


        then(kafkaProducerService).should(times(2)).sendMessage(eq(ALARM), scheduleMemberAlarmMessageCaptor.capture());
        List<ScheduleMemberAlarmMessage> capturedAlarmMessages = scheduleMemberAlarmMessageCaptor.getAllValues();

        assertThat(capturedAlarmMessages).hasSize(2);

        ScheduleMemberAlarmMessage alarmMessage1 = capturedAlarmMessages.get(0);
        assertThat(alarmMessage1.getAlarmMessageType()).isEqualTo(AlarmMessageType.SCHEDULE_EXIT);

        ScheduleMemberAlarmMessage alarmMessage2 = capturedAlarmMessages.get(1);
        assertThat(alarmMessage2.getAlarmMessageType()).isEqualTo(AlarmMessageType.SCHEDULE_EXIT);
    }

    @Test
    void openSchedule() {
        // given
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        // when
        scheduleService.openSchedule(SCHEDULE_ID);

        // then
        then(schedule).should().setRun();
        then(kafkaProducerService).should().sendMessage(eq(ALARM), any(ScheduleAlarmMessage.class));
    }

    @Test
    void processScheduleResultPoint_약속시간에_도착한_멤버_존재() {
        // given
        Long earlyMemberId1 = 3L;
        Long earlyMemberId2 = 4L;

        ScheduleMember earlyScheduleMember1 = mock(ScheduleMember.class);
        ScheduleMember earlyScheduleMember2 = mock(ScheduleMember.class);
        Member earlyMember1 = mock(Member.class);
        Member earlyMember2 = mock(Member.class);

        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleRepository.findEarlyScheduleMemberWithMember(SCHEDULE_ID))
                .willReturn(List.of(earlyScheduleMember1, earlyScheduleMember2));
        given(scheduleRepository.findLateScheduleMemberCount(SCHEDULE_ID)).willReturn(1); // 1명 지각

        given(earlyScheduleMember1.getMember()).willReturn(earlyMember1);
        given(earlyMember1.getId()).willReturn(earlyMemberId1);
        given(earlyScheduleMember2.getMember()).willReturn(earlyMember2);
        given(earlyMember2.getId()).willReturn(earlyMemberId2);

        int expectedRewardPointAmount = SCHEDULE_ENTER_POINT + (1 * SCHEDULE_ENTER_POINT / 2);

        // when
        scheduleService.processScheduleResultPoint(SCHEDULE_ID);

        // then
        then(schedule).should().rewardMember(earlyScheduleMember1, expectedRewardPointAmount);
        then(schedule).should().rewardMember(earlyScheduleMember2, expectedRewardPointAmount);

        then(eventPublisher).should(times(2)).publishEvent(pointEventCaptor.capture());
        List<PointChangeEvent> capturedPointChangeEventList = pointEventCaptor.getAllValues();

        PointChangeEvent pointEventCaptor1 = capturedPointChangeEventList.get(0);
        assertThat(pointEventCaptor1.getMemberId()).isEqualTo(earlyMemberId1);
        assertThat(pointEventCaptor1.getSign()).isEqualTo(PLUS);
        assertThat(pointEventCaptor1.getPointAmount()).isEqualTo(expectedRewardPointAmount);
        assertThat(pointEventCaptor1.getReason()).isEqualTo(SCHEDULE_REWARD);
        assertThat(pointEventCaptor1.getReasonId()).isEqualTo(SCHEDULE_ID);

        PointChangeEvent pointEventCaptor2 = capturedPointChangeEventList.get(1);
        assertThat(pointEventCaptor2.getMemberId()).isEqualTo(earlyMemberId2);
        assertThat(pointEventCaptor2.getSign()).isEqualTo(PLUS);
        assertThat(pointEventCaptor2.getPointAmount()).isEqualTo(expectedRewardPointAmount);
        assertThat(pointEventCaptor2.getReason()).isEqualTo(SCHEDULE_REWARD);
        assertThat(pointEventCaptor2.getReasonId()).isEqualTo(SCHEDULE_ID);
    }

    @Test
    void processScheduleResultPoint_모두_지각() {
        // given
        Long memberId1 = 5L;
        Long memberId2 = 6L;
        ScheduleMember lateScheduleMember1 = mock(ScheduleMember.class);
        ScheduleMember lateScheduleMember2 = mock(ScheduleMember.class);
        Member member1 = mock(Member.class);
        Member member2 = mock(Member.class);

        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleRepository.findEarlyScheduleMemberWithMember(SCHEDULE_ID)).willReturn(List.of());
        given(scheduleRepository.findScheduleMembersWithMember(SCHEDULE_ID))
                .willReturn(List.of(lateScheduleMember1, lateScheduleMember2));

        given(schedule.getId()).willReturn(SCHEDULE_ID);
        given(lateScheduleMember1.getMember()).willReturn(member1);
        given(member1.getId()).willReturn(memberId1);
        given(lateScheduleMember2.getMember()).willReturn(member2);
        given(member2.getId()).willReturn(memberId2);

        // when
        scheduleService.processScheduleResultPoint(SCHEDULE_ID);

        // then
        then(schedule).should().rewardMember(lateScheduleMember1, SCHEDULE_ENTER_POINT);
        then(schedule).should().rewardMember(lateScheduleMember2, SCHEDULE_ENTER_POINT);

        then(eventPublisher).should(times(2)).publishEvent(pointEventCaptor.capture());
        List<PointChangeEvent> capturedPointChangeEventList = pointEventCaptor.getAllValues();

        PointChangeEvent pointChangeEventCaptor1 = capturedPointChangeEventList.get(0);
        assertThat(pointChangeEventCaptor1.getMemberId()).isEqualTo(memberId1);
        assertThat(pointChangeEventCaptor1.getSign()).isEqualTo(PLUS);
        assertThat(pointChangeEventCaptor1.getPointAmount()).isEqualTo(SCHEDULE_ENTER_POINT);
        assertThat(pointChangeEventCaptor1.getReason()).isEqualTo(SCHEDULE_REWARD);
        assertThat(pointChangeEventCaptor1.getReasonId()).isEqualTo(SCHEDULE_ID);

        PointChangeEvent pointChangeEventCaptor2 = capturedPointChangeEventList.get(1);
        assertThat(pointChangeEventCaptor2.getMemberId()).isEqualTo(memberId2);
        assertThat(pointChangeEventCaptor2.getSign()).isEqualTo(PLUS);
        assertThat(pointChangeEventCaptor2.getPointAmount()).isEqualTo(SCHEDULE_ENTER_POINT);
        assertThat(pointChangeEventCaptor2.getReason()).isEqualTo(SCHEDULE_REWARD);
        assertThat(pointChangeEventCaptor2.getReasonId()).isEqualTo(SCHEDULE_ID);
    }
}