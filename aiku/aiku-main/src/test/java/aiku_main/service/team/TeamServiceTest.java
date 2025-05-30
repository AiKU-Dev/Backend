package aiku_main.service.team;

import aiku_main.application_event.event.TeamExitEvent;
import aiku_main.dto.DataResDto;
import aiku_main.dto.MemberProfileResDto;
import aiku_main.dto.team.TeamAddDto;
import aiku_main.dto.team.TeamDetailResDto;
import aiku_main.dto.team.TeamMemberResDto;
import aiku_main.dto.team.TeamResDto;
import aiku_main.exception.TeamException;
import aiku_main.repository.member.MemberRepository;
import aiku_main.repository.schedule.ScheduleRepository;
import aiku_main.repository.team.TeamRepository;
import common.domain.member.Member;
import common.domain.team.Team;
import common.domain.team.TeamMember;
import common.domain.team.TeamResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static common.domain.Status.ALIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    TeamRepository teamRepository;
    @Mock
    ScheduleRepository scheduleRepository;
    @Mock
    MemberRepository memberRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    TeamService teamService;

    Member mockMember1, mockMember2;
    Team mockTeam;

    @BeforeEach
    void setUp() {
        mockMember1 = mock(Member.class);
        mockMember2 = mock(Member.class);
        mockTeam = mock(Team.class);
    }

    @Test
    void addTeam() {
        // given
        Long memberId = 1L;
        TeamAddDto teamDto = new TeamAddDto("우리팀");

        when(memberRepository.findByIdAndStatus(memberId, ALIVE)).thenReturn(Optional.of(mockMember1));

        Long teamId = 2L;
        when(mockTeam.getId()).thenReturn(teamId);

        try (MockedStatic<Team> mockedTeam = mockStatic(Team.class)) {
            mockedTeam.when(() -> Team.create(mockMember1, teamDto.getGroupName())).thenReturn(mockTeam);

            // when
            Long resultId = teamService.addTeam(memberId, teamDto);

            // then
            assertThat(resultId).isEqualTo(teamId);
            verify(teamRepository).save(mockTeam);
        }
    }

    @Test
    void enterTeam() {
        //given
        Long memberId = 1L;
        when(memberRepository.findByIdAndStatus(memberId, ALIVE)).thenReturn(Optional.of(mockMember1));

        Long teamId = 2L;
        when(mockTeam.getId()).thenReturn(teamId);

        when(teamRepository.findByIdAndStatus(teamId, ALIVE)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.existTeamMember(memberId, teamId)).thenReturn(false);

        //when
        Long resultId = teamService.enterTeam(memberId, teamId);

        //then
        assertThat(resultId).isEqualTo(teamId);
        verify(mockTeam).addTeamMember(mockMember1);
    }

    @Test
    void enterTeam_중복된_팀멤버() {
        //given
        Long memberId = 1L;
        when(memberRepository.findByIdAndStatus(memberId, ALIVE)).thenReturn(Optional.of(mockMember1));

        Long teamId = 2L;

        when(teamRepository.findByIdAndStatus(teamId, ALIVE)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.existTeamMember(memberId, teamId)).thenReturn(true);

        //when
        assertThatThrownBy(() -> teamService.enterTeam(memberId, teamId))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void exitTeam() {
        // given
        Long memberId = 1L;
        Long teamId = 2L;
        Long teamMemberSize = 2L;
        TeamMember mockTeamMember = mock(TeamMember.class);

        when(mockTeam.getId()).thenReturn(teamId);
        when(teamRepository.findByIdAndStatus(teamId, ALIVE)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.existTeamMember(memberId, teamId)).thenReturn(true);
        when(teamRepository.countOfTeamMember(teamId)).thenReturn(teamMemberSize);
        when(teamRepository.findTeamMember(teamId, memberId)).thenReturn(Optional.of(mockTeamMember));
        when(scheduleRepository.existRunScheduleOfMemberInTeam(memberId, teamId)).thenReturn(false);

        // when
        Long result = teamService.exitTeam(memberId, teamId);

        // then
        assertThat(result).isEqualTo(teamId);

        ArgumentCaptor<TeamExitEvent> eventCaptor = ArgumentCaptor.forClass(TeamExitEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TeamExitEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getTeamId()).isEqualTo(teamId);
        assertThat(publishedEvent.getMemberId()).isEqualTo(memberId);

        verify(mockTeam).removeTeamMember(mockTeamMember);
    }

    @Test
    void exitTeam_마지막_팀멤버() {
        // given
        Long memberId = 1L;
        Long teamId = 2L;
        Long teamMemberSize = 1L;
        TeamMember mockTeamMember = mock(TeamMember.class);

        when(mockTeam.getId()).thenReturn(teamId);
        when(teamRepository.findByIdAndStatus(teamId, ALIVE)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.existTeamMember(memberId, teamId)).thenReturn(true);
        when(teamRepository.countOfTeamMember(teamId)).thenReturn(teamMemberSize);
        when(teamRepository.findTeamMember(teamId, memberId)).thenReturn(Optional.of(mockTeamMember));
        when(scheduleRepository.existRunScheduleOfMemberInTeam(memberId, teamId)).thenReturn(false);

        // when
        Long result = teamService.exitTeam(memberId, teamId);

        // then
        assertThat(result).isEqualTo(teamId);

        ArgumentCaptor<TeamExitEvent> eventCaptor = ArgumentCaptor.forClass(TeamExitEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        TeamExitEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getTeamId()).isEqualTo(teamId);
        assertThat(publishedEvent.getMemberId()).isEqualTo(memberId);

        verify(mockTeam).removeTeamMember(mockTeamMember);
        verify(mockTeam).delete();
    }

    @Test
    void getTeamDetail() {
        // given
        Long memberId1 = 1L;
        Long memberId2 = 2L;
        Long teamId = 3L;
        String teamName = "그룹 이름";

        when(mockTeam.getTeamName()).thenReturn(teamName);
        when(teamRepository.findByIdAndStatus(teamId, ALIVE)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.existTeamMember(memberId1, teamId)).thenReturn(true);

        List<TeamMemberResDto> memberList = List.of(
                new TeamMemberResDto(memberId1, "user1", new MemberProfileResDto()),
                new TeamMemberResDto(memberId2, "user2", new MemberProfileResDto())
        );
        when(teamRepository.getTeamMemberList(teamId)).thenReturn(memberList);

        // when
        TeamDetailResDto result = teamService.getTeamDetail(memberId1, teamId);

        // then
        assertThat(result.getGroupId()).isEqualTo(teamId);
        assertThat(result.getGroupName()).isEqualTo(teamName);
        assertThat(result.getMembers()).hasSize(2);
        assertThat(result.getMembers())
                .extracting(TeamMemberResDto::getMemberId)
                .containsExactlyInAnyOrder(memberId1, memberId2);
    }

    @Test
    void getTeamDetail_팀소속x() {
        // given
        Long memberId1 = 1L;
        Long teamId = 2L;

        when(teamRepository.findByIdAndStatus(teamId, ALIVE)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.existTeamMember(memberId1, teamId)).thenReturn(false);

        // when
        assertThatThrownBy(() -> teamService.getTeamDetail(memberId1, teamId))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void getTeamList() {
        // given
        Long memberId = 1L;
        int page = 1;
        Long teamId1 = 2L;
        Long teamId2 = 3L;

        List<TeamResDto> expectedList = List.of(
                new TeamResDto(teamId1, "group1", 3, null),
                new TeamResDto(teamId2, "group2", 1, null)
        );
        when(teamRepository.getTeamList(memberId, page)).thenReturn(expectedList);

        // when
        DataResDto<List<TeamResDto>> result = teamService.getTeamList(memberId, page);

        // then
        assertThat(result.getPage()).isEqualTo(page);

        List<TeamResDto> resultData = result.getData();
        assertThat(resultData).hasSize(2);

        assertThat(resultData)
                .extracting(TeamResDto::getGroupId, TeamResDto::getMemberSize)
                .containsExactlyInAnyOrder(
                        tuple(teamId1, 3),
                        tuple(teamId2, 1)
                );
    }


    @Test
    void getTeamLateTimeResult() {
        // given
        Long memberId = 1L;
        Long teamId = 2L;
        String expected = "팀 지각 결과";

        TeamResult teamResult = mock(TeamResult.class);
        when(teamResult.getLateTimeResult()).thenReturn(expected);

        when(mockTeam.getTeamResult()).thenReturn(teamResult);
        when(teamRepository.findTeamWithResult(teamId)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.existTeamMember(memberId, teamId)).thenReturn(true);

        // when
        String result = teamService.getTeamLateTimeResult(memberId, teamId);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getTeamLateTimeResult_팀소속x() {
        // given
        Long memberId = 1L;
        Long teamId = 2L;

        when(teamRepository.existTeamMember(memberId, teamId)).thenReturn(false);

        // when
        assertThatThrownBy(() -> teamService.getTeamLateTimeResult(memberId, teamId))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void getTeamBettingResult() {
        // given
        Long memberId = 1L;
        Long teamId = 2L;
        String expected = "팀 베팅 결과";

        TeamResult teamResult = mock(TeamResult.class);
        when(teamResult.getTeamBettingResult()).thenReturn(expected);

        when(mockTeam.getTeamResult()).thenReturn(teamResult);
        when(teamRepository.findTeamWithResult(teamId)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.existTeamMember(memberId, teamId)).thenReturn(true);

        // when
        String result = teamService.getTeamBettingResult(memberId, teamId);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getTeamBettingResult_팀소속x() {
        // given
        Long memberId = 1L;
        Long teamId = 2L;

        when(teamRepository.existTeamMember(memberId, teamId)).thenReturn(false);

        // when
        assertThatThrownBy(() -> teamService.getTeamBettingResult(memberId, teamId))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void getTeamRacingResult() {
        // given
        Long memberId = 1L;
        Long teamId = 2L;
        String expected = "팀 레이싱 결과";

        TeamResult teamResult = mock(TeamResult.class);
        when(teamResult.getTeamRacingResult()).thenReturn(expected);

        when(mockTeam.getTeamResult()).thenReturn(teamResult);
        when(teamRepository.findTeamWithResult(teamId)).thenReturn(Optional.of(mockTeam));
        when(teamRepository.existTeamMember(memberId, teamId)).thenReturn(true);

        // when
        String result = teamService.getTeamRacingResult(memberId, teamId);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getTeamRacingResult_팀소속x() {
        // given
        Long memberId = 1L;
        Long teamId = 2L;

        when(teamRepository.existTeamMember(memberId, teamId)).thenReturn(false);

        // when
        assertThatThrownBy(() -> teamService.getTeamRacingResult(memberId, teamId))
                .isInstanceOf(TeamException.class);
    }
}