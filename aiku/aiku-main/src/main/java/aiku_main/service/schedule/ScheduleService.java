package aiku_main.service.schedule;

import aiku_main.application_event.event.*;
import aiku_main.dto.*;
import aiku_main.dto.schedule.*;
import aiku_main.exception.ScheduleException;
import aiku_main.exception.TeamException;
import aiku_main.kafka.KafkaProducerService;
import aiku_main.repository.arrival.ArrivalRepository;
import aiku_main.repository.member.MemberRepository;
import aiku_main.repository.schedule.ScheduleRepository;
import aiku_main.repository.team.TeamRepository;
import aiku_main.scheduler.ScheduleScheduler;
import common.domain.schedule.ScheduleMember;
import common.domain.member.Member;
import common.domain.schedule.Schedule;
import common.domain.schedule.ScheduleResult;
import common.domain.value_reference.TeamValue;
import common.exception.NotEnoughPointException;
import common.kafka_message.alarm.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static aiku_main.application_event.event.PointChangeReason.*;
import static aiku_main.application_event.event.PointChangeType.MINUS;
import static aiku_main.application_event.event.PointChangeType.PLUS;
import static common.domain.ExecStatus.RUN;
import static common.domain.ExecStatus.WAIT;
import static common.domain.Status.ALIVE;
import static common.kafka_message.KafkaTopic.ALARM;
import static common.response.status.BaseErrorCode.*;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class ScheduleService {

    private final MemberRepository memberRepository;
    private final ScheduleRepository scheduleRepository;
    private final ArrivalRepository arrivalRepository;
    private final TeamRepository teamRepository;
    private final ScheduleScheduler scheduleScheduler;
    private final KafkaProducerService kafkaProducerService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${schedule.fee.participation}")
    private int scheduleEnterPoint;

    @Transactional
    public Long addSchedule(Long memberId, Long teamId, ScheduleAddDto scheduleDto){
        Member member = findMember(memberId);
        checkTeamMember(memberId, teamId);
        checkEnoughPoint(member, scheduleEnterPoint);

        Schedule schedule = Schedule.create(
                member,
                new TeamValue(teamId),
                scheduleDto.getScheduleName(),
                scheduleDto.getScheduleTime(),
                scheduleDto.getLocation().toDomain(),
                scheduleEnterPoint
        );
        scheduleRepository.save(schedule);

        scheduleScheduler.reserveSchedule(schedule);
        publishPointChangeEvent(
                memberId,
                MINUS,
                scheduleEnterPoint,
                SCHEDULE_ENTER,
                schedule.getId()
        );
        sendMessageToTeamMembers(teamId, schedule, memberId, AlarmMessageType.SCHEDULE_ADD);

        return schedule.getId();
    }

    private void publishPointChangeEvent(Long memberId, PointChangeType changeType, int pointAmount, PointChangeReason reason, Long reasonId){
        PointChangeEvent event = new PointChangeEvent(
                memberId,
                changeType,
                pointAmount,
                reason,
                reasonId
        );
        eventPublisher.publishEvent(event);
    }

    private void sendMessageToTeamMembers(Long teamId, Schedule schedule, Long excludeMemberId, AlarmMessageType messageType){
        List<String> alarmTokens = teamRepository.findAlarmTokenListOfTeamMembers(teamId, excludeMemberId);
        kafkaProducerService.sendMessage(ALARM, new ScheduleAlarmMessage(alarmTokens, messageType, schedule));
    }

    @Transactional
    public Long updateSchedule(Long memberId, Long scheduleId, ScheduleUpdateDto scheduleDto){
        Schedule schedule = findSchedule(scheduleId);
        checkIsWait(schedule);
        checkScheduleUpdateTime(schedule);
        checkIsOwner(memberId, scheduleId);

        schedule.update(scheduleDto.getScheduleName(), scheduleDto.getScheduleTime(), scheduleDto.location.toDomain());

        scheduleScheduler.changeSchedule(schedule);
        sendMessageToScheduleMembers(schedule, memberId, null, AlarmMessageType.SCHEDULE_UPDATE);

        return schedule.getId();
    }

    @Transactional
    public Long enterSchedule(Long memberId, Long teamId, Long scheduleId) {
        checkTeamMember(memberId, teamId);

        Member member = findMember(memberId);
        checkEnoughPoint(member, scheduleEnterPoint);

        Schedule schedule = findSchedule(scheduleId);
        checkScheduleMember(memberId, scheduleId, false);
        checkIsWait(schedule);

        schedule.addScheduleMember(member, false, scheduleEnterPoint);

        sendMessageToScheduleMembers(schedule, memberId, member, AlarmMessageType.SCHEDULE_ENTER);

        publishPointChangeEvent(
                memberId,
                MINUS,
                scheduleEnterPoint,
                SCHEDULE_ENTER,
                scheduleId
        );

        return scheduleId;
    }

    @Transactional
    public Long exitSchedule(Long memberId, Long teamId, Long scheduleId) {
        Member member = findMember(memberId);
        ScheduleMember scheduleMember = findScheduleMember(memberId, scheduleId);
        Schedule schedule = findSchedule(scheduleId);
        checkIsWait(schedule);

        Long scheduleMemberCount = scheduleRepository.countOfScheduleMembers(scheduleId);
        if(isLastMemberOfSchedule(scheduleMemberCount)){
            schedule.delete();
        }else if(scheduleMember.isOwner()){
            ScheduleMember nextOwner = changeScheduleOwner(schedule, scheduleMember.getId());
            sendMessageToScheduleMember(schedule, nextOwner.getMember().getFirebaseToken(), AlarmMessageType.SCHEDULE_OWNER);
        }

        schedule.removeScheduleMember(scheduleMember);

        sendMessageToScheduleMembers(schedule, memberId, member, AlarmMessageType.SCHEDULE_EXIT);
        publishPointChangeEvent(
                memberId,
                PLUS,
                scheduleEnterPoint,
                SCHEDULE_EXIT,
                schedule.getId()
        );

        publishScheduleExitEvent(memberId, scheduleMember.getId(), scheduleId);

        return schedule.getId();
    }

    private boolean isLastMemberOfSchedule(long scheduleMemberCount){
        return scheduleMemberCount <= 1;
    }

    private ScheduleMember changeScheduleOwner(Schedule schedule, Long curOwnerScheduleMemberId){
        ScheduleMember nextOwner = findNextScheduleOwnerWithMember(schedule.getId(), curOwnerScheduleMemberId);
        schedule.changeScheduleOwner(nextOwner);

        return nextOwner;
    }

    private void publishScheduleExitEvent(Long memberId, Long scheduleMemberId, Long scheduleId){
        ScheduleExitEvent event = new ScheduleExitEvent(memberId, scheduleMemberId, scheduleId);
        eventPublisher.publishEvent(event);
    }

    private ScheduleMember findNextScheduleOwnerWithMember(Long scheduleId, Long prevOwnerScheduleId){
        return scheduleRepository.findNextScheduleOwnerWithMember(scheduleId, prevOwnerScheduleId)
                .orElseThrow(() -> new ScheduleException(CAN_NOT_FIND_NEXT_SCHEDULE_OWNER));
    }

    private void sendMessageToScheduleMembers(Schedule schedule, Long excludeMemberId, Member sourceMember, AlarmMessageType messageType) {
        List<String> alarmTokens = scheduleRepository.findAlarmTokenListOfScheduleMembers(schedule.getId(), excludeMemberId);

        if(sourceMember == null) {
            kafkaProducerService.sendMessage(
                    ALARM,
                    new ScheduleAlarmMessage(alarmTokens, messageType, schedule)
            );
        } else {
            kafkaProducerService.sendMessage(
                    ALARM,
                    new ScheduleMemberAlarmMessage(alarmTokens, messageType, new AlarmMemberInfo(sourceMember), schedule)
            );
        }
    }

    private void sendMessageToScheduleMember(Schedule schedule, String alarmToken, AlarmMessageType messageType){
        kafkaProducerService.sendMessage(
                ALARM,
                new ScheduleAlarmMessage(List.of(alarmToken), messageType, schedule)
        );
    }

    @Transactional
    public void closeSchedule(Long scheduleId, LocalDateTime closeTime){
        Schedule schedule = findSchedule(scheduleId);

        Map<Long, LocalDateTime> scheMemArrivalTime = arrivalRepository.findArrivalsOfSchedule(scheduleId)
                .stream()
                .collect(Collectors.toMap(
                        (arrival) -> arrival.getScheduleMember().getId(),
                        (arrival) -> arrival.getArrivalTime()
                ));

        schedule.close(closeTime, scheMemArrivalTime);

        publishScheduleCloseEvent(schedule.getTeam().getId(), scheduleId);
    }

    private void publishScheduleCloseEvent(Long teamId, Long scheduleId){
        ScheduleCloseEvent event = new ScheduleCloseEvent(teamId, scheduleId);
        eventPublisher.publishEvent(event);
    }

    public ScheduleDetailResDto getScheduleDetail(Long memberId, Long teamId, Long scheduleId) {
        Schedule schedule = findSchedule(scheduleId);
        checkScheduleMember(memberId, scheduleId, true);

        List<ScheduleMemberResDto> membersDtoList = scheduleRepository.getScheduleMembersWithBettingInfo(memberId, scheduleId);

        return new ScheduleDetailResDto(schedule, membersDtoList);
    }

    public SchedulePreviewResDto getSchedulePreview(Long memberId, Long groupId, Long scheduleId) {
        checkTeamMember(memberId, groupId);

        SchedulePreviewResDto schedulePreview = scheduleRepository.getSchedulePreview(scheduleId);

        return schedulePreview;
    }

    public TeamScheduleListResDto getTeamScheduleList(Long memberId, Long teamId, SearchDateCond dateCond, int page) {
        checkTeamMember(memberId, teamId);

        List<TeamScheduleListEachResDto> scheduleList = scheduleRepository.getTeamSchedules(teamId, memberId, dateCond, page);
        scheduleList.forEach((scheduleDto) -> scheduleDto.SetAcceptIfEntered(memberId));

        int runSchedule = scheduleRepository.countTeamScheduleByScheduleStatus(teamId, RUN, dateCond);
        int waitSchedule = scheduleRepository.countTeamScheduleByScheduleStatus(teamId, WAIT, dateCond);

        return new TeamScheduleListResDto(page, teamId, runSchedule, waitSchedule, scheduleList);
    }

    public MemberScheduleListResDto getMemberScheduleList(Long memberId, SearchDateCond dateCond, int page) {
        List<MemberScheduleListEachResDto> scheduleList = scheduleRepository.getMemberSchedules(memberId, dateCond, page);
        int runSchedule = scheduleRepository.countMemberScheduleByScheduleStatus(memberId, RUN, dateCond);
        int waitSchedule = scheduleRepository.countMemberScheduleByScheduleStatus(memberId, WAIT, dateCond);

        return new MemberScheduleListResDto(page, runSchedule, waitSchedule, scheduleList);
    }

    public String getScheduleArrivalResult(Long memberId, Long teamId, Long scheduleId) {
        checkTeamMember(memberId, teamId);
        ScheduleResult scheduleResult = findScheduleResult(scheduleId);

        return scheduleResult.getScheduleArrivalResult();
    }

    public String getScheduleBettingResult(Long memberId, Long teamId, Long scheduleId) {
        checkTeamMember(memberId, teamId);
        ScheduleResult scheduleResult = findScheduleResult(scheduleId);

        return scheduleResult.getScheduleBettingResult();
    }

    public String getScheduleRacingResult(Long memberId, Long teamId, Long scheduleId) {
        checkTeamMember(memberId, teamId);
        ScheduleResult scheduleResult = findScheduleResult(scheduleId);

        return scheduleResult.getScheduleRacingResult();
    }

    public SimpleResDto<List<LocalDate>> getScheduleDatesInMonth(Long memberId, MonthDto monthDto) {
        List<LocalDate> scheduleTimeList = scheduleRepository.findScheduleDatesInMonth(memberId, monthDto.getYear(), monthDto.getMonth())
                .stream()
                .map(dateTime -> dateTime.toLocalDate())
                .distinct()
                .toList();

        return new SimpleResDto<>(scheduleTimeList);
    }

    @Transactional
    public void exitAllScheduleInTeam(Long memberId, Long teamId) {
        Member member = memberRepository.findById(memberId).orElseThrow();

        List<ScheduleMember> scheduleMembers = scheduleRepository.findWaitScheduleMemberWithScheduleInTeam(memberId, teamId);
        scheduleMembers.forEach((scheduleMember) ->{
            Schedule schedule = scheduleMember.getSchedule();
            schedule.removeScheduleMember(scheduleMember);

            publishPointChangeEvent(
                    memberId,
                    PLUS,
                    scheduleEnterPoint,
                    SCHEDULE_EXIT,
                    schedule.getId()
            );
            publishScheduleExitEvent(memberId, scheduleMember.getId(), schedule.getId());
            sendMessageToScheduleMembers(schedule, member.getId(), member, AlarmMessageType.SCHEDULE_EXIT);
        });
    }

    @Transactional
    public void openSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        schedule.setRun();

        sendMessageToScheduleMembers(schedule, null, null, AlarmMessageType.SCHEDULE_OPEN);
    }

    @Transactional
    public void processScheduleResultPoint(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();

        List<ScheduleMember> earlyMembers = scheduleRepository.findEarlyScheduleMemberWithMember(scheduleId);

        if(earlyMembers.isEmpty()) {
            refundScheduleEnterPointToMembers(schedule);
            return;
        }

        int lateScheduleMemberCount = scheduleRepository.findLateScheduleMemberCount(scheduleId);
        int rewardOfEarlyMember = lateScheduleMemberCount * scheduleEnterPoint / earlyMembers.size();
        int rewardPointAmount = scheduleEnterPoint + rewardOfEarlyMember;

        earlyMembers.forEach((earlyScheduleMember) -> {
            schedule.rewardMember(earlyScheduleMember, rewardPointAmount);
            publishPointChangeEvent(
                    earlyScheduleMember.getMember().getId(),
                    PLUS,
                    rewardPointAmount,
                    SCHEDULE_REWARD,
                    scheduleId
            );
        });
    }

    private void refundScheduleEnterPointToMembers(Schedule schedule){
        scheduleRepository.findScheduleMembersWithMember(schedule.getId())
                .forEach((scheduleMember) -> {
                    schedule.rewardMember(scheduleMember, scheduleEnterPoint);
                    publishPointChangeEvent(
                            scheduleMember.getMember().getId(),
                            PLUS,
                            scheduleEnterPoint,
                            SCHEDULE_REWARD,
                            schedule.getId()
                    );
                });
    }

    private Member findMember(Long memberId){
        return memberRepository.findById(memberId).orElseThrow();
    }

    private Schedule findSchedule(Long scheduleId){
        return scheduleRepository.findByIdAndStatus(scheduleId, ALIVE)
                .orElseThrow(() -> new ScheduleException(NO_SUCH_SCHEDULE));
    }

    private ScheduleMember findScheduleMember(Long memberId, Long scheduleId){
        return scheduleRepository.findScheduleMember(memberId, scheduleId)
                .orElseThrow(() -> new ScheduleException(NOT_IN_SCHEDULE));
    }

    private ScheduleResult findScheduleResult(Long scheduleId){
        return scheduleRepository.findScheduleResult(scheduleId)
                .orElseThrow(() -> new ScheduleException(NO_SUCH_SCHEDULE_RESULT));
    }

    private void checkIsWait(Schedule schedule){
        if(!schedule.isWait()){
            throw new ScheduleException(NO_WAIT_SCHEDULE);
        }
    }

    private void checkTeamMember(Long memberId, Long teamId){
        if(!teamRepository.existTeamMember(memberId, teamId)){
            throw new TeamException(NOT_IN_TEAM);
        }
    }

    private void checkScheduleMember(Long memberId, Long scheduleId, boolean isMember){
        if(scheduleRepository.existScheduleMember(memberId, scheduleId) != isMember){
            if(isMember){
                throw new ScheduleException(NOT_IN_SCHEDULE);
            }else{
                throw new ScheduleException(ALREADY_IN_SCHEDULE);
            }
        }
    }

    private void checkIsOwner(Long memberId, Long scheduleId){
        if(!scheduleRepository.isScheduleOwner(memberId, scheduleId)){
            throw new ScheduleException(NO_SCHEDULE_OWNER);
        }
    }

    private void checkEnoughPoint(Member member, int point){
        if(member.getPoint() < point){
            throw new NotEnoughPointException();
        }
    }

    private void checkScheduleUpdateTime(Schedule schedule){
        long minutesDiff = Duration.between(LocalDateTime.now(), schedule.getScheduleTime()).toMinutes();
        if(minutesDiff < 60){
            throw new ScheduleException(FORBIDDEN_SCHEDULE_UPDATE_TIME);
        }
    }
}
