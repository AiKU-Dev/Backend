package aiku_main.service.team;

import aiku_main.dto.team.*;
import aiku_main.dto.team.TeamResDto;
import aiku_main.exception.TeamException;
import aiku_main.repository.member.MemberRepository;
import aiku_main.repository.team.TeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.domain.*;
import common.domain.member.Member;
import common.domain.schedule.Schedule;
import common.domain.team.Team;
import common.domain.team.TeamMember;
import common.domain.value_reference.TeamValue;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@SpringBootTest
public class TeamServiceIntegrationTest {

    @Autowired
    EntityManager em;
    @Autowired
    TeamService teamService;
    @Autowired
    TeamRepository teamRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    ObjectMapper objectMapper;

    Member member1;
    Member member2;
    Member member3;

    @BeforeEach
    void beforeEach(){
        member1 = Member.create("member1");
        member2 = Member.create("member2");
        member3 = Member.create("member3");
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
    }

    @AfterEach
    void afterEach(){
        memberRepository.deleteAll();
    }

    @Test
    void 그룹_등록() {
        //when
        TeamAddDto teamDto = new TeamAddDto("group1");
        Long teamId = teamService.addTeam(member1.getId(), teamDto);

        //then
        Team team = teamRepository.findById(teamId).orElse(null);
        assertThat(team).isNotNull();
        assertThat(team.getTeamName()).isEqualTo(teamDto.getGroupName());
        assertThat(team.getTeamMembers()).hasSize(1);

        TeamMember teamMember = team.getTeamMembers().get(0);
        assertThat(teamMember.getMember().getId()).isEqualTo(member1.getId()); //그룹을 등록한 멤버는 그룹 자동 참가됨
    }

    @Test
    void 그룹_입장() {
        //given
        Team team = Team.create(member1, "team1");
        em.persist(team);

        //when
        Long teamId = teamService.enterTeam(member2.getId(), team.getId());

        //then
        Team resultTeam = teamRepository.findById(teamId).orElse(null);
        assertThat(resultTeam).isNotNull();
        assertThat(resultTeam.getTeamMembers()).hasSize(2);
        assertThat(resultTeam.getTeamMembers())
                .extracting(teamMember -> teamMember.getMember().getId())
                .containsExactlyInAnyOrder(member1.getId(), member2.getId());
    }

    @Test
    void 그룹_입장_중복() {
        //given
        Team team = Team.create(member1, "team1");
        team.addTeamMember(member2);
        em.persist(team);

        //when
        assertThatThrownBy(() -> teamService.enterTeam(member2.getId(), team.getId()))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void 그룹_퇴장() {
        //given
        Team team = Team.create(member1, "team1");
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        em.persist(team);

        //when
        teamService.exitTeam(member2.getId(), team.getId());

        //then
        Team resultTeam = teamRepository.findById(team.getId()).orElse(null);
        assertThat(resultTeam).isNotNull();
        assertThat(resultTeam.getStatus()).isEqualTo(Status.ALIVE);

        TeamMember teamMember = teamRepository.findDeletedTeamMember(team.getId(), member2.getId()).orElse(null);
        assertThat(teamMember).isNotNull();
    }

    @Test
    void 그룹_퇴장_그룹멤버x() {
        //given
        Team team = Team.create(member1, "team1");
        em.persist(team);

        //when
        assertThatThrownBy(() -> teamService.exitTeam(member2.getId(), team.getId()))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void 그룹_퇴장_실행중인스케줄O() {
        //given
        Team team = Team.create(member1, "team1");
        em.persist(team);

        Schedule schedule = Schedule.create(member1, new TeamValue(team.getId()), "sche1",
                LocalDateTime.now().plusDays(1), new Location("loc", 1.0, 1.0), 0);
        schedule.setRun();
        em.persist(schedule);

        //when
        assertThatThrownBy(() -> teamService.exitTeam(member1.getId(), team.getId()))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void 그룹_퇴장_중복() {
        //given
        Team team = Team.create(member1, "team1");
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        em.persist(team);

        teamService.exitTeam(member2.getId(), team.getId());

        //when
        assertThatThrownBy(() -> teamService.exitTeam(member2.getId(), team.getId()))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void 그룹_퇴장_남은멤버x_그룹삭제() {
        //given
        Team team = Team.create(member1, "team1");
        em.persist(team);

        //when
        teamService.exitTeam(member1.getId(), team.getId());

        //then
        Team resultTeam = teamRepository.findById(team.getId()).orElse(null);
        assertThat(resultTeam).isNotNull();
        assertThat(resultTeam.getStatus()).isEqualTo(Status.DELETE);
    }

    @Test
    void 그룹_상세_조회() {
        //given
        Team team = Team.create(member1, "team1");
        team.addTeamMember(member2);
        em.persist(team);

        //when
        TeamDetailResDto result = teamService.getTeamDetail(member1.getId(), team.getId());

        //then
        assertThat(result.getGroupId()).isEqualTo(team.getId());
        assertThat(result.getGroupName()).isEqualTo(team.getTeamName());

        List<TeamMemberResDto> teamMemberDtos = result.getMembers();
        assertThat(teamMemberDtos).hasSize(2);
        assertThat(teamMemberDtos)
                .extracting(TeamMemberResDto::getMemberId)
                .containsExactly(member1.getId(), member2.getId());
    }

    @Test
    void 그룹_상세_조회_그룹멤버x() {
        //given
        Team team = Team.create(member1, "team1");
        em.persist(team);

        //when
        assertThatThrownBy(() -> teamService.getTeamDetail(member2.getId(), team.getId()))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void 그룹_목록_조회() {
        //given
        Team teamA = Team.create(member1, "teamA");
        em.persist(teamA);

        Team teamB = Team.create(member1, "teamB");
        teamB.addTeamMember(member2);
        em.persist(teamB);

        Team teamC = Team.create(member1, "teamC");
        teamC.addTeamMember(member2);
        teamC.addTeamMember(member3);
        em.persist(teamC);

        Schedule scheduleA1 = createSchedule(member1, teamA, LocalDateTime.now().minusDays(3));
        scheduleA1.setTerm(LocalDateTime.now().minusDays(3));
        em.persist(scheduleA1);

        Schedule scheduleB1 = createSchedule(member1, teamB, LocalDateTime.now().plusDays(1));
        em.persist(scheduleB1);

        Schedule scheduleC1 = createSchedule(member1, teamC, LocalDateTime.now().minusDays(5));
        scheduleC1.setTerm(LocalDateTime.now().minusDays(5));
        em.persist(scheduleC1);

        Schedule scheduleC2 = createSchedule(member1, teamC, LocalDateTime.now().minusDays(1));
        scheduleC2.setTerm(LocalDateTime.now().minusDays(1));
        em.persist(scheduleC2);

        //when
        List<TeamResDto> data = teamService.getTeamList(member1.getId(), 1).getData();

        //then
        assertThat(data).hasSize(3);
        assertThat(data)
                .extracting(TeamResDto::getGroupId)
                .containsExactly(teamC.getId(), teamA.getId(), teamB.getId());
        assertThat(data)
                .extracting(TeamResDto::getMemberSize)
                .containsExactly(3, 1, 2);
    }

    Schedule createSchedule(Member member, Team team, LocalDateTime startTime) {
        return Schedule.create(member, new TeamValue(team.getId()), "schedule", startTime,
                new Location("loc1", 1.1, 1.1), 0);
    }
}
