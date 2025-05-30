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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static common.domain.Status.ALIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamResultAnalysisServiceTest {

    @Mock
    TeamRepository teamRepository;

    @InjectMocks
    TeamResultAnalysisService teamResultAnalysisService;

    Team mockTeam;
    TeamResult mockTeamResult;

    @BeforeEach
    void setUp() {
        mockTeam = mock(Team.class);
        mockTeamResult = mock(TeamResult.class);
    }

    @Test
    void analyzeLateTimeResult() {
        //given
        Long teamId = 1L;
        String expected = "결과";

        when(teamRepository.findTeamWithResult(teamId)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.getTeamLateTimeResult(teamId)).thenReturn(List.of(mock(TeamLateTimeResult.class)));

        try (MockedStatic<ObjectMapperUtil> mockedUtil = Mockito.mockStatic(ObjectMapperUtil.class)) {
            mockedUtil.when(() -> ObjectMapperUtil.toJson(any())).thenReturn(expected);

            // when
            teamResultAnalysisService.analyzeLateTimeResult(teamId);

            // then
            mockedUtil.verify(() -> ObjectMapperUtil.toJson(any(TeamLateTimeResultDto.class)));
            verify(mockTeam).setTeamLateResult(expected);
        }
    }

    @Test
    void analyzeBettingResult() {
        //given
        Long teamId = 1L;
        String expected = "결과";

        when(teamRepository.findTeamWithResult(teamId)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.getBettingWinOddsResult(teamId)).thenReturn(List.of(mock(TeamBettingResult.class)));

        try (MockedStatic<ObjectMapperUtil> mockedUtil = Mockito.mockStatic(ObjectMapperUtil.class)) {
            mockedUtil.when(() -> ObjectMapperUtil.toJson(any())).thenReturn(expected);

            // when
            teamResultAnalysisService.analyzeBettingResult(teamId);

            // then
            mockedUtil.verify(() -> ObjectMapperUtil.toJson(any(TeamBettingResultDto.class)));
            verify(mockTeam).setTeamBettingResult(expected);
        }
    }

    @Test
    void analyzeRacingResult() {
        //given
        Long teamId = 1L;
        String expected = "결과";

        when(teamRepository.findTeamWithResult(teamId)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.getRacingWinOddsResult(teamId)).thenReturn(List.of(mock(TeamRacingResult.class)));

        try (MockedStatic<ObjectMapperUtil> mockedUtil = Mockito.mockStatic(ObjectMapperUtil.class)) {
            mockedUtil.when(() -> ObjectMapperUtil.toJson(any())).thenReturn(expected);

            // when
            teamResultAnalysisService.analyzeRacingResult(teamId);

            // then
            mockedUtil.verify(() -> ObjectMapperUtil.toJson(any(TeamRacingResultDto.class)));
            verify(mockTeam).setTeamRacingResult(expected);
        }
    }

    @Test
    void updateTeamResultOfExitMember_지각시간만_업데이트() {
        //given
        Long memberId1 = 3L;
        Long memberId2 = 4L;
        Long teamId = 10L;
        String originalResult = "originalResult";
        String expected = "expected";


        TeamResult mockTeamResult = mock(TeamResult.class);
        when(mockTeamResult.hasNoLateTimeResult()).thenReturn(false); //지각 시간만 업데이트
        when(mockTeamResult.hasNoTeamBettingResult()).thenReturn(true);
        when(mockTeamResult.hasNoTeamRacingResult()).thenReturn(true);
        when(mockTeamResult.getLateTimeResult()).thenReturn(originalResult);

        when(mockTeam.getTeamResult()).thenReturn(mockTeamResult);
        when(teamRepository.findTeamWithResult(teamId)).thenReturn(Optional.of(mockTeam));

        List<TeamLateTimeResult> lateTimeResultList = List.of(
                new TeamLateTimeResult(memberId1, "name1", null, 0, ALIVE),
                new TeamLateTimeResult(memberId2, "name2", null, 0, ALIVE)
        );
        TeamLateTimeResultDto teamLateTimeResultDto = new TeamLateTimeResultDto(teamId, lateTimeResultList);

        try (MockedStatic<ObjectMapperUtil> mockedUtil = Mockito.mockStatic(ObjectMapperUtil.class)) {
            mockedUtil.when(() -> ObjectMapperUtil.parseJson(originalResult, TeamLateTimeResultDto.class)).thenReturn(teamLateTimeResultDto);
            mockedUtil.when(() -> ObjectMapperUtil.toJson(any())).thenReturn(expected);

            //when
            teamResultAnalysisService.updateTeamResultOfExitMember(memberId1, teamId);

            // then
            ArgumentCaptor<TeamLateTimeResultDto> updatedResultCaptor = ArgumentCaptor.forClass(TeamLateTimeResultDto.class);
            mockedUtil.verify(() -> ObjectMapperUtil.toJson(updatedResultCaptor.capture()));

            TeamLateTimeResultDto result = updatedResultCaptor.getValue();
            assertThat(result.getMembers())
                    .extracting(TeamLateTimeResult::getMemberId, TeamLateTimeResult::isTeamMember)
                    .containsExactlyInAnyOrder(
                            tuple(memberId1, false), //요청한 멤버만 팀 퇴장
                            tuple(memberId2, true)
                    );

            verify(mockTeam).setTeamLateResult(expected);
        }
    }

    @Test
    void updateTeamResultOfExitMember_베팅결과만_업데이트() {
        //given
        Long memberId1 = 3L;
        Long memberId2 = 4L;
        Long teamId = 10L;
        String originalResult = "originalResult";
        String expected = "expected";


        TeamResult mockTeamResult = mock(TeamResult.class);
        when(mockTeamResult.hasNoTeamBettingResult()).thenReturn(false); //베팅 승률만 업데이트
        when(mockTeamResult.hasNoLateTimeResult()).thenReturn(true);
        when(mockTeamResult.hasNoTeamRacingResult()).thenReturn(true);
        when(mockTeamResult.getTeamBettingResult()).thenReturn(originalResult);

        when(mockTeam.getTeamResult()).thenReturn(mockTeamResult);
        when(teamRepository.findTeamWithResult(teamId)).thenReturn(Optional.of(mockTeam));

        List<TeamBettingResult> bettingResultList = List.of(
                new TeamBettingResult(memberId1, "name1", null, 0, ALIVE),
                new TeamBettingResult(memberId2, "name2", null, 0, ALIVE)
        );
        TeamBettingResultDto teamBettingResultDto = new TeamBettingResultDto(teamId, bettingResultList);

        try (MockedStatic<ObjectMapperUtil> mockedUtil = Mockito.mockStatic(ObjectMapperUtil.class)) {
            mockedUtil.when(() -> ObjectMapperUtil.parseJson(originalResult, TeamBettingResultDto.class)).thenReturn(teamBettingResultDto);
            mockedUtil.when(() -> ObjectMapperUtil.toJson(any())).thenReturn(expected);

            //when
            teamResultAnalysisService.updateTeamResultOfExitMember(memberId1, teamId);

            // then
            ArgumentCaptor<TeamBettingResultDto> updatedResultCaptor = ArgumentCaptor.forClass(TeamBettingResultDto.class);
            mockedUtil.verify(() -> ObjectMapperUtil.toJson(updatedResultCaptor.capture()));

            TeamBettingResultDto result = updatedResultCaptor.getValue();
            assertThat(result.getMembers())
                    .extracting(TeamBettingResult::getMemberId, TeamBettingResult::isTeamMember)
                    .containsExactlyInAnyOrder(
                            tuple(memberId1, false), //요청한 멤버만 팀 퇴장
                            tuple(memberId2, true)
                    );

            verify(mockTeam).setTeamBettingResult(expected);
        }
    }

    @Test
    void updateTeamResultOfExitMember_레이싱결과만_업데이트() {
        //given
        Long memberId1 = 3L;
        Long memberId2 = 4L;
        Long teamId = 10L;
        String originalResult = "originalResult";
        String expected = "expected";


        TeamResult mockTeamResult = mock(TeamResult.class);
        when(mockTeamResult.hasNoTeamRacingResult()).thenReturn(false); //베팅 승률만 업데이트
        when(mockTeamResult.hasNoTeamBettingResult()).thenReturn(true);
        when(mockTeamResult.hasNoLateTimeResult()).thenReturn(true);
        when(mockTeamResult.getTeamRacingResult()).thenReturn(originalResult);

        when(mockTeam.getTeamResult()).thenReturn(mockTeamResult);
        when(teamRepository.findTeamWithResult(teamId)).thenReturn(Optional.of(mockTeam));

        List<TeamRacingResult> teamRacingResultList = List.of(
                new TeamRacingResult(memberId1, "name1", null, 0, ALIVE),
                new TeamRacingResult(memberId2, "name2", null, 0, ALIVE)
        );
        TeamRacingResultDto teamRacingResultDto = new TeamRacingResultDto(teamId, teamRacingResultList);

        try (MockedStatic<ObjectMapperUtil> mockedUtil = Mockito.mockStatic(ObjectMapperUtil.class)) {
            mockedUtil.when(() -> ObjectMapperUtil.parseJson(originalResult, TeamRacingResultDto.class)).thenReturn(teamRacingResultDto);
            mockedUtil.when(() -> ObjectMapperUtil.toJson(any())).thenReturn(expected);

            //when
            teamResultAnalysisService.updateTeamResultOfExitMember(memberId1, teamId);

            // then
            ArgumentCaptor<TeamRacingResultDto> updatedResultCaptor = ArgumentCaptor.forClass(TeamRacingResultDto.class);
            mockedUtil.verify(() -> ObjectMapperUtil.toJson(updatedResultCaptor.capture()));

            TeamRacingResultDto result = updatedResultCaptor.getValue();
            assertThat(result.getMembers())
                    .extracting(TeamRacingResult::getMemberId, TeamRacingResult::isTeamMember)
                    .containsExactlyInAnyOrder(
                            tuple(memberId1, false), //요청한 멤버만 팀 퇴장
                            tuple(memberId2, true)
                    );

            verify(mockTeam).setTeamRacingResult(expected);
        }
    }
}