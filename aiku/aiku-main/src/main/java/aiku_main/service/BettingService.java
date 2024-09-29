package aiku_main.service;

import aiku_main.application_event.domain.ScheduleBetting;
import aiku_main.application_event.domain.ScheduleBettingMember;
import aiku_main.application_event.domain.ScheduleBettingResult;
import aiku_main.application_event.publisher.PointChangeEventPublisher;
import aiku_main.dto.BettingAddDto;
import aiku_main.exception.CanNotBettingException;
import aiku_main.repository.BettingRepository;
import aiku_main.repository.MemberRepository;
import aiku_main.repository.ScheduleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.domain.Betting;
import common.domain.ExecStatus;
import common.domain.schedule.Schedule;
import common.domain.schedule.ScheduleMember;
import common.domain.Status;
import common.domain.member.Member;
import common.domain.value_reference.ScheduleMemberValue;
import common.exception.BaseExceptionImpl;
import common.exception.NoAuthorityException;
import common.exception.NotEnoughPoint;
import common.exception.PaidMemberLimitException;
import common.response.status.BaseErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static aiku_main.application_event.event.PointChangeReason.BETTING;
import static aiku_main.application_event.event.PointChangeReason.BETTING_CANCLE;
import static aiku_main.application_event.event.PointChangeType.MINUS;
import static aiku_main.application_event.event.PointChangeType.PLUS;
import static common.domain.ExecStatus.TERM;
import static common.domain.ExecStatus.WAIT;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class BettingService {

    private final MemberRepository memberRepository;
    private final BettingRepository bettingRepository;
    private final ScheduleRepository scheduleRepository;
    private final PointChangeEventPublisher pointChangeEventPublisher;
    private final ObjectMapper objectMapper;

    //TODO 전체적으로 알림 전송을 위한 카프카 연결 해야됨
    @Transactional
    public Long addBetting(Member member, Long scheduleId, BettingAddDto bettingDto){
        //검증 로직
        checkPaidScheduleMember(member.getId(), scheduleId);
        checkPaidScheduleMember(bettingDto.getBeteeMemberId(), scheduleId);
        checkScheduleWait(scheduleId);

        ScheduleMemberValue bettor = new ScheduleMemberValue(findScheduleMember(member.getId(), scheduleId));
        ScheduleMemberValue bettee = new ScheduleMemberValue(findScheduleMember(bettingDto.getBeteeMemberId(), scheduleId));

        checkAlreadyHasBetting(bettor, scheduleId);
        checkEnoughPoint(member, bettingDto.getPointAmount());

        //서비스 로직
        Betting betting = Betting.create(bettor, bettee, bettingDto.getPointAmount());
        bettingRepository.save(betting);

        pointChangeEventPublisher.publish(member, MINUS, bettingDto.getPointAmount(), BETTING, betting.getId());

        return betting.getId();
    }

    @Transactional
    public Long cancelBetting(Member member, Long scheduleId, Long bettingId){
        //검증 로직
        ScheduleMember bettor = findScheduleMember(member.getId(), scheduleId);
        Betting betting = bettingRepository.findById(bettingId).orElseThrow();
        checkBettingMember(betting, bettor);
        checkBettingAlive(betting);

        //서비스 로직
        betting.setStatus(Status.DELETE);

        pointChangeEventPublisher.publish(member, PLUS, betting.getPointAmount(), BETTING, bettingId);

        return betting.getId();
    }

    //==이벤트 핸들러==
    @Transactional
    public void exitSchedule_deleteBettingForBettor(Long memberId, Long scheduleMemberId, Long scheduleId){
        Betting bettingForBettor = bettingRepository.findByBettorIdAndStatus(scheduleMemberId, Status.ALIVE).orElse(null);
        bettingForBettor.setStatus(Status.DELETE);

        int pointAmount = bettingForBettor.getPointAmount();
        Member member = memberRepository.findById(memberId).orElseThrow();
        pointChangeEventPublisher.publish(member, PLUS, pointAmount, BETTING_CANCLE, bettingForBettor.getId());
    }

    @Transactional
    public void exitSchedule_deleteBettingForBetee(Long memberId, Long scheduleMemberId, Long scheduleId){
        Betting bettingForBetee = bettingRepository.findByBeteeIdAndStatus(scheduleMemberId, Status.ALIVE).orElse(null);
        bettingForBetee.setStatus(Status.DELETE);

        int pointAmount = bettingForBetee.getPointAmount();
        Member bettor = scheduleRepository.findScheduleMemberWithMemberById(bettingForBetee.getBettor().getId()).orElseThrow()
                .getMember();
        pointChangeEventPublisher.publish(bettor, PLUS, pointAmount, BETTING_CANCLE, bettingForBetee.getId());

        //TODO 베터한테 푸쉬 알림 줘야함. 강제 베팅 취소기 때문에..
    }

    @Transactional
    public void processBettingResult(Long scheduleId) {
        List<Betting> bettings = bettingRepository.findBettingsInSchedule(scheduleId, WAIT);
        if(bettings.size() == 0){
            return;
        }

        Map<Long, ScheduleMember> scheduleMembers =
                scheduleRepository.findScheduleMembersWithMember(scheduleId).stream()
                .collect(Collectors.toMap(
                        sm -> sm.getId(),
                        sm -> sm
                ));

        LocalDateTime latestTime = getLatestTimeOfLateMember(scheduleMembers.values());
        long winBettingCount = countWinBetting(scheduleMembers, bettings, latestTime);

        if(winBettingCount == 0) {
            for (Betting betting : bettings) {
                betting.setDraw();
                pointChangeEventPublisher.publish(scheduleMembers.get(betting.getBettor().getId()).getMember(),
                        PLUS, betting.getPointAmount(), BETTING, betting.getId());
            }
            return;
        }

        int bettingPointAmount = getBettingPointAmount(bettings);
        int winnerPointAmount = getWinnersPointAmount(scheduleMembers, bettings, latestTime);

        for (Betting betting : bettings) {
            if(scheduleMembers.get(betting.getBetee().getId()).getArrivalTime().equals(latestTime)){
                int rewardPoint = getBettingRewardPoint(betting.getPointAmount(), winnerPointAmount, bettingPointAmount);
                betting.setWin(rewardPoint);
                pointChangeEventPublisher.publish(scheduleMembers.get(betting.getBettor().getId()).getMember(),
                        PLUS, rewardPoint, BETTING, betting.getId());
            }else {
                betting.setLose();
            }
        }
    }

    private LocalDateTime getLatestTimeOfLateMember(Collection<ScheduleMember> scheduleMembers){
        return scheduleMembers.stream()
                .filter(scheduleMember -> scheduleMember.getArrivalTimeDiff() < 0)
                .max(Comparator.comparing(ScheduleMember::getArrivalTime))
                .map(ScheduleMember::getArrivalTime)
                .orElse(null);
    }

    private long countWinBetting(Map<Long, ScheduleMember> scheduleMembers, List<Betting> bettings, LocalDateTime latestTime){
        if (latestTime == null) return 0L;
        return bettings.stream()
                .filter(betting -> scheduleMembers.get(betting.getBetee().getId()).getArrivalTime().equals(latestTime))
                .count();
    }

    private int getBettingPointAmount(List<Betting> bettings){
        return bettings.stream().mapToInt(Betting::getPointAmount).sum();
    }

    private int getWinnersPointAmount(Map<Long, ScheduleMember> scheduleMembers, List<Betting> bettings, LocalDateTime latestTime){
        return bettings.stream()
                .filter(betting -> scheduleMembers.get(betting.getBetee().getId()).getArrivalTime().equals(latestTime))
                .mapToInt(Betting::getPointAmount)
                .sum();
    }

    private int getBettingRewardPoint(int bettingPoint, int winnerPointAmount, int bettingPointAmount) {
        return (int) ((double) bettingPoint / winnerPointAmount * bettingPointAmount);
    }

    @Transactional
    public void analyzeScheduleBettingResult(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        if (isAlreadyAnalyzeScheduleBettingResult(schedule)) {
            return;
        }

        List<Betting> bettings = bettingRepository.findBettingsInSchedule(scheduleId, TERM);
        if(bettings.size() == 0){
            return;
        }

        Map<Long, ScheduleMember> scheduleMembers =
                scheduleRepository.findScheduleMembersWithMember(scheduleId).stream()
                        .collect(Collectors.toMap(
                                sm -> sm.getId(),
                                sm -> sm
                        ));

        List<ScheduleBetting> bettingDtoList = bettings.stream()
                .map(betting -> {
                    ScheduleBettingMember bettor = new ScheduleBettingMember(scheduleMembers.get(betting.getBettor().getId()).getMember());
                    ScheduleBettingMember betee = new ScheduleBettingMember(scheduleMembers.get(betting.getBetee().getId()).getMember());
                    return new ScheduleBetting(bettor, betee, betting.getPointAmount());
                }).toList();

        ScheduleBettingResult result = new ScheduleBettingResult(scheduleId, bettingDtoList);

        try {
            schedule.setScheduleBettingResult(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Can't Parse ScheduleBettingResult");
        }
    }

    private boolean isAlreadyAnalyzeScheduleBettingResult(Schedule schedule){
        return schedule.getScheduleResult() != null && schedule.getScheduleResult().getScheduleBettingResult() != null;
    }

    //==엔티티 조회 메서드==
    private ScheduleMember findScheduleMember(Long memberId, Long scheduleId) {
        return scheduleRepository.findAliveScheduleMember(memberId, scheduleId).orElseThrow();
    }

    //==편의 메서드==
    private void checkScheduleWait(Long scheduleId) {
        if(!scheduleRepository.existsByIdAndScheduleStatusAndStatus(scheduleId, WAIT, Status.ALIVE)){
            throw new NoAuthorityException("유효하지 않은 스케줄입니다.");
        }
    }

    private void checkBettingAlive(Betting betting) {
        if (betting.getStatus() == Status.DELETE){
            throw new BaseExceptionImpl(BaseErrorCode.FORBIDDEN, "취소된 베팅입니다.");
        }
    }

    private void checkAlreadyHasBetting(ScheduleMemberValue bettor, Long scheduleId) {
        if(bettingRepository.existBettorInSchedule(bettor, scheduleId)){
            throw new CanNotBettingException();
        }
    }

    private void checkPaidScheduleMember(Long memberId, Long scheduleId){
        if(!scheduleRepository.existPaidScheduleMember(memberId, scheduleId)){
            throw new PaidMemberLimitException();
        }
    }

    private void checkBettingMember(Betting betting, ScheduleMember bettor){
        if(!betting.getBettor().getId().equals(bettor.getId())){
            throw new NoAuthorityException();
        }
    }

    private void checkEnoughPoint(Member member, int point){
        if(member.getPoint() < point){
            throw new NotEnoughPoint();
        }
    }
}
