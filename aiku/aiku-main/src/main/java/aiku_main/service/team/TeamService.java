package aiku_main.service.team;

import aiku_main.application_event.event.TeamExitEvent;
import aiku_main.dto.team.*;
import aiku_main.dto.*;
import aiku_main.exception.MemberNotFoundException;
import aiku_main.exception.TeamException;
import aiku_main.repository.member.MemberRepository;
import aiku_main.repository.schedule.ScheduleRepository;
import aiku_main.repository.team.TeamRepository;
import common.domain.member.Member;
import common.domain.team.Team;
import common.domain.team.TeamMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static common.domain.Status.ALIVE;
import static common.response.status.BaseErrorCode.*;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final ScheduleRepository scheduleRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long addTeam(Long memberId, TeamAddDto teamDto){
        Member member = findMember(memberId);

        Team team = Team.create(member, teamDto.getGroupName());
        teamRepository.save(team);

        return team.getId();
    }

    @Transactional
    public Long enterTeam(Long memberId, Long teamId) {
        Member member = findMember(memberId);
        Team team = findTeam(teamId);
        checkTeamMember(memberId, teamId, false);

        team.addTeamMember(member);

        return team.getId();
    }

    @Transactional
    public Long exitTeam(Long memberId, Long teamId) {
        Team team = findTeam(teamId);
        checkTeamMember(memberId, teamId, true);
        checkHasRunSchedule(memberId, teamId);

        deleteTeamIfLastMember(team);

        TeamMember teamMember = findTeamMember(memberId, teamId);
        team.removeTeamMember(teamMember);

        publishTeamExitEvent(memberId, teamId);

        return team.getId();
    }

    private void deleteTeamIfLastMember(Team team){
        Long teamMemberCount = teamRepository.countOfTeamMember(team.getId());
        if (isLastMember(teamMemberCount)){
            team.delete();
        }
    }

    private boolean isLastMember(long teamMemberCount){
        return teamMemberCount <= 1;
    }

    private void publishTeamExitEvent(Long memberId, Long teamId){
        TeamExitEvent event = new TeamExitEvent(memberId, teamId);
        eventPublisher.publishEvent(event);
    }

    public TeamDetailResDto getTeamDetail(Long memberId, Long teamId) {
        Team team = findTeam(teamId);
        checkTeamMember(memberId, teamId, true);

        List<TeamMemberResDto> teamMemberList = teamRepository.getTeamMemberList(teamId);

        return new TeamDetailResDto(teamId, team.getTeamName(), teamMemberList);
    }

    public DataResDto<List<TeamResDto>> getTeamList(Long memberId, int page) {
        List<TeamResDto> data = teamRepository.getTeamList(memberId, page);

        return new DataResDto<>(page, data);
    }

    public String getTeamLateTimeResult(Long memberId, Long teamId){
        checkTeamMember(memberId, teamId, true);
        Team team = findTeamWithResult(teamId);

        return team.getTeamResult() == null
                ? null
                : team.getTeamResult().getLateTimeResult();
    }

    public String getTeamBettingResult(Long memberId, Long teamId){
        checkTeamMember(memberId, teamId, true);
        Team team = findTeamWithResult(teamId);

        return team.getTeamResult() == null
                ? null
                : team.getTeamResult().getTeamBettingResult();
    }

    public String getTeamRacingResult(Long memberId, Long teamId) {
        checkTeamMember(memberId, teamId, true);
        Team team = findTeamWithResult(teamId);

        return team.getTeamResult() == null
                ? null
                : team.getTeamResult().getTeamRacingResult();
    }

    private Member findMember(Long memberId){
        return memberRepository.findByIdAndStatus(memberId, ALIVE)
                .orElseThrow(() -> new MemberNotFoundException());
    }

    private Team findTeam(Long teamId){
        return teamRepository.findByIdAndStatus(teamId, ALIVE)
                .orElseThrow(() -> new TeamException(NO_SUCH_TEAM));
    }

    private Team findTeamWithResult(Long teamId){
        return teamRepository.findTeamWithResult(teamId)
                .orElseThrow(() -> new TeamException(NO_SUCH_TEAM));
    }

    private TeamMember findTeamMember(Long memberId, Long teamId){
        return teamRepository.findTeamMember(teamId, memberId)
                .orElseThrow(() -> new TeamException(INTERNAL_SERVER_ERROR));
    }

    private void checkTeamMember(Long memberId, Long teamId, boolean isMember){
        if(teamRepository.existTeamMember(memberId, teamId) != isMember){
            if(isMember){
                throw new TeamException(NOT_IN_TEAM);
            }else {
                throw new TeamException(ALREADY_IN_TEAM);
            }
        }
    }

    private void checkHasRunSchedule(Long memberId, Long teamId) {
        if(scheduleRepository.existRunScheduleOfMemberInTeam(memberId, teamId)){
            throw new TeamException(CAN_NOT_EXIT);
        }
    }
}
