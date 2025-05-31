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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;


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

        given(memberRepository.findByIdAndStatus(memberId, ALIVE)).willReturn(Optional.of(mockMember1));
        Long teamId = 2L;
        given(mockTeam.getId()).willReturn(teamId);

        try (MockedStatic<Team> mockedTeam = mockStatic(Team.class)) {
            mockedTeam.when(() -> Team.create(mockMember1, teamDto.getGroupName())).thenReturn(mockTeam);

            // when
            Long resultId = teamService.addTeam(memberId, teamDto);

            // then
            assertThat(resultId).isEqualTo(teamId);
            then(teamRepository).should().save(mockTeam);
        }
    }

    @Test
    void enterTeam() {
        //given
        Long memberId = 1L;
        given(memberRepository.findByIdAndStatus(memberId, ALIVE)).willReturn(Optional.of(mockMember1));

        Long teamId = 2L;
        given(mockTeam.getId()).willReturn(teamId);

        given(teamRepository.findByIdAndStatus(teamId, ALIVE)).willReturn(Optional.of(mockTeam));
        given(teamRepository.existTeamMember(memberId, teamId)).willReturn(false);

        //when
        Long resultId = teamService.enterTeam(memberId, teamId);

        //then
        assertThat(resultId).isEqualTo(teamId);
        then(mockTeam).should().addTeamMember(mockMember1);
    }

    @Test
    void enterTeam_중복된_팀멤버() {
        //given
        Long memberId = 1L;
        given(memberRepository.findByIdAndStatus(memberId, ALIVE)).willReturn(Optional.of(mockMember1));

        Long teamId = 2L;

        given(teamRepository.findByIdAndStatus(teamId, ALIVE)).willReturn(Optional.of(mockTeam));
        given(teamRepository.existTeamMember(memberId, teamId)).willReturn(true);

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

        given(mockTeam.getId()).willReturn(teamId);
        given(teamRepository.findByIdAndStatus(teamId, ALIVE)).willReturn(Optional.of(mockTeam));
        given(teamRepository.existTeamMember(memberId, teamId)).willReturn(true);
        given(teamRepository.countOfTeamMember(teamId)).willReturn(teamMemberSize);
        given(teamRepository.findTeamMember(teamId, memberId)).willReturn(Optional.of(mockTeamMember));
        given(scheduleRepository.existRunScheduleOfMemberInTeam(memberId, teamId)).willReturn(false);

        // when
        Long result = teamService.exitTeam(memberId, teamId);

        // then
        assertThat(result).isEqualTo(teamId);

        ArgumentCaptor<TeamExitEvent> eventCaptor = ArgumentCaptor.forClass(TeamExitEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());

        TeamExitEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getTeamId()).isEqualTo(teamId);
        assertThat(publishedEvent.getMemberId()).isEqualTo(memberId);

        then(mockTeam).should().removeTeamMember(mockTeamMember);
    }

    @Test
    void exitTeam_마지막_팀멤버() {
        // given
        Long memberId = 1L;
        Long teamId = 2L;
        Long teamMemberSize = 1L;
        TeamMember mockTeamMember = mock(TeamMember.class);

        given(mockTeam.getId()).willReturn(teamId);
        given(teamRepository.findByIdAndStatus(teamId, ALIVE)).willReturn(Optional.of(mockTeam));
        given(teamRepository.existTeamMember(memberId, teamId)).willReturn(true);
        given(teamRepository.countOfTeamMember(teamId)).willReturn(teamMemberSize);
        given(teamRepository.findTeamMember(teamId, memberId)).willReturn(Optional.of(mockTeamMember));
        given(scheduleRepository.existRunScheduleOfMemberInTeam(memberId, teamId)).willReturn(false);

        // when
        Long result = teamService.exitTeam(memberId, teamId);

        // then
        assertThat(result).isEqualTo(teamId);

        ArgumentCaptor<TeamExitEvent> eventCaptor = ArgumentCaptor.forClass(TeamExitEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());

        TeamExitEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.getTeamId()).isEqualTo(teamId);
        assertThat(publishedEvent.getMemberId()).isEqualTo(memberId);

        then(mockTeam).should().removeTeamMember(mockTeamMember);
        then(mockTeam).should().delete();
    }

    @Test
    void getTeamDetail() {
        // given
        Long memberId1 = 1L;
        Long memberId2 = 2L;
        Long teamId = 3L;
        String teamName = "그룹 이름";

        given(mockTeam.getTeamName()).willReturn(teamName);
        given(teamRepository.findByIdAndStatus(teamId, ALIVE)).willReturn(Optional.of(mockTeam));
        given(teamRepository.existTeamMember(memberId1, teamId)).willReturn(true);

        List<TeamMemberResDto> memberList = List.of(
                new TeamMemberResDto(memberId1, "user1", new MemberProfileResDto()),
                new TeamMemberResDto(memberId2, "user2", new MemberProfileResDto())
        );
        given(teamRepository.getTeamMemberList(teamId)).willReturn(memberList);

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

        given(teamRepository.findByIdAndStatus(teamId, ALIVE)).willReturn(Optional.of(mockTeam));
        given(teamRepository.existTeamMember(memberId1, teamId)).willReturn(false);

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
        given(teamRepository.getTeamList(memberId, page)).willReturn(expectedList);

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
        given(teamResult.getLateTimeResult()).willReturn(expected);

        given(mockTeam.getTeamResult()).willReturn(teamResult);
        given(teamRepository.findTeamWithResult(teamId)).willReturn(Optional.of(mockTeam));
        given(teamRepository.existTeamMember(memberId, teamId)).willReturn(true);

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

        given(teamRepository.existTeamMember(memberId, teamId)).willReturn(false);

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
        given(teamResult.getTeamBettingResult()).willReturn(expected);

        given(mockTeam.getTeamResult()).willReturn(teamResult);
        given(teamRepository.findTeamWithResult(teamId)).willReturn(Optional.of(mockTeam));
        given(teamRepository.existTeamMember(memberId, teamId)).willReturn(true);

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

        given(teamRepository.existTeamMember(memberId, teamId)).willReturn(false);

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
        given(teamResult.getTeamRacingResult()).willReturn(expected);

        given(mockTeam.getTeamResult()).willReturn(teamResult);
        given(teamRepository.findTeamWithResult(teamId)).willReturn(Optional.of(mockTeam));
        given(teamRepository.existTeamMember(memberId, teamId)).willReturn(true);

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

        given(teamRepository.existTeamMember(memberId, teamId)).willReturn(false);

        // when
        assertThatThrownBy(() -> teamService.getTeamRacingResult(memberId, teamId))
                .isInstanceOf(TeamException.class);
    }
}