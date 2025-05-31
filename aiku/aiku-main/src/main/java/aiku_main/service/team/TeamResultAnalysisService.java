package aiku_main.service.team;

import aiku_main.dto.team.result.betting_odds.TeamBettingResult;
import aiku_main.dto.team.result.betting_odds.TeamBettingResultDto;
import aiku_main.dto.team.result.late_time.TeamLateTimeResult;
import aiku_main.dto.team.result.late_time.TeamLateTimeResultDto;
import aiku_main.dto.team.result.racing_odds.TeamRacingResult;
import aiku_main.dto.team.result.racing_odds.TeamRacingResultDto;
import aiku_main.repository.team.TeamRepository;
import common.domain.team.Team;
import common.domain.team.TeamResult;
import common.util.ObjectMapperUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class TeamResultAnalysisService {

    private final TeamRepository teamRepository;

    @Transactional
    public void analyzeLateTimeResult(Long teamId) {
        Team team = teamRepository.findTeamWithResult(teamId).orElseThrow();

        List<TeamLateTimeResult> result = teamRepository.getTeamLateTimeResult(teamId); //1.지각 총 시간 내림차순, 2.스케줄 총 개수 내림차순
        TeamLateTimeResultDto resultDto = new TeamLateTimeResultDto(teamId, result);

        team.setTeamLateResult(ObjectMapperUtil.toJson(resultDto));
    }

    @Transactional
    public void analyzeBettingResult(Long teamId) {
        Team team = teamRepository.findTeamWithResult(teamId).orElseThrow();

        List<TeamBettingResult> result = teamRepository.getBettingWinOddsResult(teamId); //1.확률 내림차순, 2.베팅 총 개수 내림차순
        TeamBettingResultDto resultDto = new TeamBettingResultDto(team.getId(), result);

        team.setTeamBettingResult(ObjectMapperUtil.toJson(resultDto));
    }

    @Transactional
    public void analyzeRacingResult(Long teamId){
        Team team = teamRepository.findTeamWithResult(teamId).orElseThrow();

        List<TeamRacingResult> result = teamRepository.getRacingWinOddsResult(teamId);//1.확률 내림차순, 2.레이싱 총 개수 내림차순
        TeamRacingResultDto resultDto = new TeamRacingResultDto(team.getId(), result);

        team.setTeamRacingResult(ObjectMapperUtil.toJson(resultDto));
    }

    @Transactional
    public void updateTeamResultOfExitMember(Long memberId, Long teamId) {
        Team team = teamRepository.findTeamWithResult(teamId).orElseThrow();

        updateLateTimeResultOfExitMember(memberId, team);
        updateBettingTimeResultOfExitMember(memberId, team);
        updateRacingTimeResultOfExitMember(memberId, team);
    }

    private void updateLateTimeResultOfExitMember(Long memberId, Team team){
        TeamResult teamResult = team.getTeamResult();
        if (teamResult.hasNoLateTimeResult()) {
            return;
        }

        TeamLateTimeResultDto result = ObjectMapperUtil.parseJson(teamResult.getLateTimeResult(), TeamLateTimeResultDto.class);
        result.getMembers().forEach(resultMember -> {
            if (resultMember.getMemberId().equals(memberId)) {
                resultMember.setTeamMember(false);
            }
        });

        team.setTeamLateResult(ObjectMapperUtil.toJson(result));
    }

    private void updateBettingTimeResultOfExitMember(Long memberId, Team team){
        TeamResult teamResult = team.getTeamResult();
        if (teamResult.hasNoTeamBettingResult()) {
            return;
        }

        TeamBettingResultDto result = ObjectMapperUtil.parseJson(teamResult.getTeamBettingResult(), TeamBettingResultDto.class);
        result.getMembers().forEach(resultMember -> {
            if (resultMember.getMemberId().equals(memberId)) {
                resultMember.setTeamMember(false);
            }
        });

        team.setTeamBettingResult(ObjectMapperUtil.toJson(result));
    }

    private void updateRacingTimeResultOfExitMember(Long memberId, Team team){
        TeamResult teamResult = team.getTeamResult();
        if (teamResult.hasNoTeamRacingResult()) {
            return;
        }

        TeamRacingResultDto result = ObjectMapperUtil.parseJson(teamResult.getTeamRacingResult(), TeamRacingResultDto.class);
        result.getMembers().forEach(resultMember -> {
            if (resultMember.getMemberId().equals(memberId)) {
                resultMember.setTeamMember(false);
            }
        });

        team.setTeamRacingResult(ObjectMapperUtil.toJson(result));
    }
}
