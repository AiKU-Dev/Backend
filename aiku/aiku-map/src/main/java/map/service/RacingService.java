package map.service;

import common.domain.Racing;
import common.domain.Status;
import common.domain.schedule.Schedule;
import common.exception.PaidMemberLimitException;
import common.kafka_message.KafkaTopic;
import common.kafka_message.alarm.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import map.application_event.domain.RacingInfo;
import map.application_event.publisher.RacingEventPublisher;
import map.dto.DataResDto;
import map.dto.RacingAddDto;
import map.dto.RacingResDto;
import map.exception.MemberNotFoundException;
import map.exception.MemberNotInRacingException;
import map.exception.NoSuchRacingException;
import map.exception.ScheduleException;
import map.kafka.KafkaProducerService;
import map.repository.MemberRepository;
import map.repository.RacingRepository;
import map.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static common.domain.ExecStatus.RUN;
import static common.domain.ExecStatus.WAIT;
import static common.response.status.BaseErrorCode.*;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class RacingService {

    private final KafkaProducerService kafkaService;
    private final RacingEventPublisher racingEventPublisher;
    private final RacingRepository racingRepository;
    private final ScheduleRepository scheduleRepository;
    private final MemberRepository memberRepository;

    public DataResDto<List<RacingResDto>> getRacings(Long memberId, Long scheduleId) {
        // 해당 멤버 스케줄에 존재 / 스케줄이 진행 중인지 검증,
        checkMemberInSchedule(memberId, scheduleId);
        checkScheduleInRun(scheduleId);

        //  해당 스케줄에 속해 있으며 현재 진행 중인 레이싱 종합하여 전달
        List<RacingResDto> racings = racingRepository.getAllRunningRacingsInSchedule(scheduleId);

        return new DataResDto<>(1, racings);
    }

    @Transactional
    public Long makeRacing(Long memberId, Long scheduleId, RacingAddDto racingAddDto) {
        // 해당 멤버 스케줄에 존재 / 스케줄이 진행 중인지 검증
        checkMemberInSchedule(memberId, scheduleId);
        checkScheduleInRun(scheduleId);

        //  두 유저 모두 깍두기 멤버가 아닌지, 이미 중복된 레이싱이 존재하는지 검증,
        checkPaidScheduleMember(memberId, scheduleId);
        checkPaidScheduleMember(racingAddDto.getTargetMemberId(), scheduleId);

        //  두 유저 모두 충분한 포인트를 가졌는지 확인
        checkEnoughRacingPoint(memberId, racingAddDto.getPoint());
        checkEnoughRacingPoint(racingAddDto.getTargetMemberId(), racingAddDto.getPoint());

        //  대기 중 레이싱 DB 저장
        Racing racing = Racing.create(memberId, racingAddDto.getTargetMemberId(), racingAddDto.getPoint());
        racingRepository.save(racing);

        //  카프카로 레이싱 신청 대상자에게 알림 전달
        AlarmMemberInfo secondRacerInfo = getMemberInfo(racingAddDto.getTargetMemberId());
        Schedule schedule = findSchedule(scheduleId);

        kafkaService.sendMessage(KafkaTopic.alarm,
                new AskRacingMessage(List.of(secondRacerInfo), AlarmMessageType.ASK_RACING,
                        scheduleId,
                        schedule.getScheduleName(),
                        racing.getId()
                )
        );

        //  30초 후 레이싱 상태 확인, 대기 중이면 삭제하고 두 사용자에게 알림 발송하는 로직 실행
        racingEventPublisher.publishAskRacingEvent(
                new RacingInfo(scheduleId,
                        schedule.getScheduleName(),
                        racing.getId(),
                        memberId,
                        racingAddDto.getTargetMemberId(),
                        racingAddDto.getPoint()
                )
        );

        return racing.getId();
    }

    @Transactional
    public void autoDeleteRacingById(RacingInfo racingInfo) {
        racingRepository.deleteById(racingInfo.getRacingId());
        AlarmMemberInfo firstRacerInfo = getMemberInfo(racingInfo.getFirstRacerId());
        AlarmMemberInfo secondRacerInfo = getMemberInfo(racingInfo.getSecondRacerId());

        kafkaService.sendMessage(KafkaTopic.alarm,
                new RacingAutoDeletedMessage(List.of(firstRacerInfo, secondRacerInfo),
                        AlarmMessageType.RACING_AUTO_DELETED,
                        racingInfo.getScheduleId(),
                        racingInfo.getScheduleName(),
                        racingInfo.getRacingId(),
                        racingInfo.getPointAmount()
                )
        );
    }

    @Transactional
    public Long acceptRacing(Long memberId, Long scheduleId, Long racingId) {
        // 레이싱이 대기 중인지 검증,
        checkRacingInWait(racingId);

        //  두 유저 모두 충분한 포인트를 가졌는지 확인
        checkBothMemberHaveEnoughRacingPoint(racingId);

        //  카프카로 레이싱 신청자에게 알림 전달, 레이싱 상태 진행 중으로 변경
        Racing racing = findRacing(racingId);
        racing.startRacing();

        List<AlarmMemberInfo> racingMemberInfos = getMemberInfosInRacing(racingId);

        // 두 대상자에게 포인트 차감 전달
        racingMemberInfos
                .stream()
                        .forEach(r -> kafkaService.sendMessage(KafkaTopic.alarm,
                                new PointChangedMessage(List.of(r), AlarmMessageType.POINT_CHANGED,
                                        r.getMemberId(), racing.getPointAmount())
                        ));

        return racingId;
    }


    @Transactional
    public Long denyRacing(Long memberId, Long scheduleId, Long racingId) {
        // 레이싱이 대기 중인지 검증, 거절을 누른 멤버가 레이싱의 대상자인지 검증
        checkRacingInWait(racingId);
        checkMemberIsSecondRacerInRacing(memberId, racingId);

        //  대기 중 레이싱 DB 삭제,
        racingRepository.deleteById(racingId);

        //  카프카로 레이싱 신청 참가자에게 알림 전송
        Schedule schedule = findSchedule(scheduleId);

        List<AlarmMemberInfo> memberInfosInRacing = getMemberInfosInRacing(racingId);
        kafkaService.sendMessage(KafkaTopic.alarm,
                new RacingDeniedMessage(memberInfosInRacing, AlarmMessageType.RACING_DENIED,
                        scheduleId, schedule.getScheduleName(), racingId)
        );

        return racingId;
    }

    private Schedule findSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleException(NO_SUCH_SCHEDULE));
    }

    private AlarmMemberInfo getMemberInfo(Long memberId) {
        return memberRepository.findMemberInfo(memberId)
                .orElseThrow(() -> new MemberNotFoundException());
    }

    private Racing findRacing(Long racingId) {
        return racingRepository.findById(racingId)
                .orElseThrow(() -> new NoSuchRacingException());
    }

    private List<AlarmMemberInfo> getMemberInfosInRacing(Long racingId) {
        return racingRepository.findMemberInfoByScheduleMemberId(racingId);
    }

    private void checkMemberInSchedule(Long memberId, Long scheduleId) {
        if(!scheduleRepository.existMemberInSchedule(memberId, scheduleId)) {
            throw new ScheduleException(NOT_IN_SCHEDULE);
        }
    }

    private void checkScheduleInRun(Long scheduleId) {
        if (!scheduleRepository.existsByIdAndScheduleStatusAndStatus(scheduleId, RUN, Status.ALIVE)) {
            throw new ScheduleException(NO_SUCH_SCHEDULE);
        }
    }

    private void checkRacingInWait(Long racingId) {
        if (!racingRepository.existsByIdAndRaceStatusAndStatus(racingId, WAIT, Status.ALIVE)) {
            throw new ScheduleException(NO_SUCH_SCHEDULE);
        }
    }

    private void checkPaidScheduleMember(Long memberId, Long scheduleId){
        if(!scheduleRepository.existPaidScheduleMember(memberId, scheduleId)){
            throw new PaidMemberLimitException();
        }
    }

    private void checkEnoughRacingPoint(Long memberId, Integer point){
        if(!memberRepository.checkEnoughRacingPoint(memberId, point)){
            throw new PaidMemberLimitException();
        }
    }

    private void checkBothMemberHaveEnoughRacingPoint(Long racingId){
        if(!racingRepository.checkBothMemberHaveEnoughRacingPoint(racingId)){
            throw new PaidMemberLimitException();
        }
    }

    private void checkMemberIsSecondRacerInRacing(Long memberId, Long racingId) {
        if (!racingRepository.checkMemberIsSecondRacerInRacing(memberId, racingId)) {
            throw new MemberNotInRacingException(NOT_IN_RACING);
        }
    }
}