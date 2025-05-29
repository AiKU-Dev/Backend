package aiku_main.service.betting;

import aiku_main.application_event.event.PointChangeEvent;
import aiku_main.application_event.event.PointChangeReason;
import aiku_main.application_event.event.PointChangeType;
import aiku_main.dto.betting.BettingAddDto;
import aiku_main.exception.BettingException;
import aiku_main.exception.ScheduleException;
import aiku_main.repository.betting.BettingRepository;
import aiku_main.repository.member.MemberRepository;
import aiku_main.repository.schedule.ScheduleRepository;
import common.domain.betting.Betting;
import common.domain.schedule.ScheduleMember;
import common.domain.member.Member;
import common.domain.value_reference.ScheduleMemberValue;
import common.exception.NotEnoughPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static aiku_main.application_event.event.PointChangeReason.*;
import static aiku_main.application_event.event.PointChangeType.MINUS;
import static aiku_main.application_event.event.PointChangeType.PLUS;
import static common.domain.ExecStatus.WAIT;
import static common.domain.Status.ALIVE;
import static common.domain.Status.DELETE;
import static common.response.status.BaseErrorCode.*;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class BettingService {

    private final MemberRepository memberRepository;
    private final BettingRepository bettingRepository;
    private final ScheduleRepository scheduleRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long addBetting(Long memberId, Long scheduleId, BettingAddDto bettingDto){
        Member member = findMember(memberId);
        checkScheduleWait(scheduleId);

        ScheduleMemberValue bettor = new ScheduleMemberValue(findScheduleMemberId(member.getId(), scheduleId));
        ScheduleMemberValue bettee = new ScheduleMemberValue(findScheduleMemberId(bettingDto.getBeteeMemberId(), scheduleId));

        checkAlreadyHasBetting(bettor.getId(), scheduleId);
        checkEnoughPoint(member, bettingDto.getPointAmount());

        Betting betting = Betting.create(bettor, bettee, bettingDto.getPointAmount());
        bettingRepository.save(betting);

        publishPointChangeEvent(
                memberId,
                MINUS,
                bettingDto.getPointAmount(),
                BETTING,
                betting.getId()
        );

        return betting.getId();
    }

    @Transactional
    public Long cancelBetting(Long memberId, Long scheduleId, Long bettingId){
        ScheduleMember bettor = findScheduleMember(memberId, scheduleId);
        Betting betting = findBettingById(bettingId);
        checkBettingMember(betting, bettor);

        betting.delete();

        publishPointChangeEvent(
                memberId,
                PLUS,
                betting.getPointAmount(),
                BETTING_CANCLE,
                bettingId
        );

        return betting.getId();
    }

    @Transactional
    public void deleteBettingForBettor(Long memberId, Long scheduleMemberId, Long scheduleId){
        bettingRepository.findByBettorIdAndStatus(scheduleMemberId, ALIVE)
                .ifPresent((betting) -> {
                    betting.delete();

                    publishPointChangeEvent(
                            memberId,
                            PLUS,
                            betting.getPointAmount(),
                            BETTING_CANCLE,
                            betting.getId()
                    );
                });
    }

    @Transactional
    public void deleteBettingForBetee(Long memberId, Long scheduleMemberId, Long scheduleId){
        bettingRepository.findByBeteeIdAndStatus(scheduleMemberId, ALIVE)
                .ifPresent((betting) -> {
                    betting.delete();

                    Long memberIdOfBettor = scheduleRepository.findMemberIdOfScheduleMember(betting.getBettor().getId()).orElseThrow();
                    publishPointChangeEvent(
                            memberIdOfBettor,
                            PLUS,
                            betting.getPointAmount(),
                            BETTING_CANCLE,
                            betting.getId()
                    );
                });
    }

    @Transactional
    public void termBettingAndProcessResultPoint(Long scheduleId) {
        List<Betting> bettings = bettingRepository.findBettingsInSchedule(scheduleId, WAIT);
        if(bettings.isEmpty()){
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
                publishPointChangeEvent(
                        scheduleMembers.get(betting.getBettor().getId()).getMember().getId(),
                        PLUS,
                        betting.getPointAmount(),
                        BETTING_REWARD,
                        betting.getId()
                );
            }
            return;
        }

        int bettingPointAmount = getBettingPointAmount(bettings);
        int winnerPointAmount = getWinnersPointAmount(scheduleMembers, bettings, latestTime);

        for (Betting betting : bettings) {
            if(scheduleMembers.get(betting.getBetee().getId()).getArrivalTime().equals(latestTime)){
                int rewardPoint = getBettingRewardPoint(betting.getPointAmount(), winnerPointAmount, bettingPointAmount);
                betting.setWin(rewardPoint);
                publishPointChangeEvent(
                        scheduleMembers.get(betting.getBettor().getId()).getMember().getId(),
                        PLUS,
                        rewardPoint,
                        BETTING_REWARD,
                        betting.getId()
                );
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

    private Member findMember(Long memberId){
        return memberRepository.findById(memberId).orElseThrow();
    }

    private Betting findBettingById(Long bettingId){
        return bettingRepository.findByIdAndStatus(bettingId, ALIVE)
                .orElseThrow(() -> new BettingException(NO_SUCH_BETTING));
    }

    private ScheduleMember findScheduleMember(Long memberId, Long scheduleId) {
        return scheduleRepository.findScheduleMember(memberId, scheduleId)
                .orElseThrow(() -> new ScheduleException(NOT_IN_SCHEDULE));
    }

    private Long findScheduleMemberId(Long memberId, Long scheduleId) {
        return scheduleRepository.findScheduleMemberId(memberId, scheduleId)
                .orElseThrow(() -> new ScheduleException(NOT_IN_SCHEDULE));
    }

    private void checkScheduleWait(Long scheduleId) {
        if(!scheduleRepository.existsByIdAndScheduleStatusAndStatus(scheduleId, WAIT, ALIVE)){
            throw new ScheduleException(NO_WAIT_SCHEDULE);
        }
    }

    private void checkAlreadyHasBetting(Long scheduleMemberIdOfBettor, Long scheduleId) {
        if(bettingRepository.existBettorInSchedule(scheduleMemberIdOfBettor, scheduleId)){
            throw new BettingException(ALREADY_IN_BETTING);
        }
    }

    private void checkBettingMember(Betting betting, ScheduleMember bettor){
        if(!betting.getBettor().getId().equals(bettor.getId())){
            throw new BettingException(NOT_IN_BETTING);
        }
    }

    private void checkEnoughPoint(Member member, int point){
        if(member.getPoint() < point){
            throw new NotEnoughPoint();
        }
    }
}
