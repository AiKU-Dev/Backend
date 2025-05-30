package aiku_main.service.team;

import aiku_main.dto.MemberProfileResDto;
import aiku_main.dto.team.result.betting_odds.TeamBettingResult;
import aiku_main.dto.team.result.betting_odds.TeamBettingResultDto;
import aiku_main.dto.team.result.late_time.TeamLateTimeResult;
import aiku_main.dto.team.result.late_time.TeamLateTimeResultDto;
import aiku_main.dto.team.result.racing_odds.TeamRacingResult;
import aiku_main.dto.team.result.racing_odds.TeamRacingResultDto;
import aiku_main.repository.team.TeamRepository;
import common.domain.Location;
import common.domain.Status;
import common.domain.betting.Betting;
import common.domain.member.Member;
import common.domain.member.MemberProfileType;
import common.domain.racing.Racing;
import common.domain.schedule.Schedule;
import common.domain.team.Team;
import common.domain.team.TeamResult;
import common.domain.value_reference.ScheduleMemberValue;
import common.domain.value_reference.TeamValue;
import common.util.ObjectMapperUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@SpringBootTest
class TeamResultAnalysisServiceIntegrationTest {

    @Autowired
    EntityManager em;
    @Autowired
    TeamResultAnalysisService teamResultAnalysisService;
    @Autowired
    TeamRepository teamRepository;

    Member teamOwner;
    Member member1;
    Member member2;
    Member member3;
    Team team;
    Schedule schedule1;
    Schedule schedule2;
    Schedule schedule3;

    @BeforeEach
    void beforeEach(){
        teamOwner = createMember();
        member1 = createMember();
        member2 = createMember();
        member3 = createMember();
        em.persist(teamOwner);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);

        team = Team.create(teamOwner, "team");
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        team.addTeamMember(member3);
        em.persist(team);

        schedule1 = createSchedule(member1, team);
        schedule1.addScheduleMember(member2, false, 0);
        schedule1.addScheduleMember(member3, false, 0);
        em.persist(schedule1);

        schedule2 = createSchedule(member1, team);
        schedule2.addScheduleMember(member2, false, 0);
        schedule2.addScheduleMember(member3, false, 0);
        em.persist(schedule2);

        schedule3 = createSchedule(member1, team);
        schedule3.addScheduleMember(member2, false, 0);
        schedule3.addScheduleMember(member3, false, 0);
        em.persist(schedule3);
    }

    /**
     * //given
     * schedule1
     * member1 -> 0분
     * member2 -> 10분 일찍 도착
     * member3 -> 10분 지각
     *
     * schedule2
     * member1 -> 1시간 지각
     * member2 -> 0분 도착
     * member3 -> 1시간 일찍 도착
     *
     * => member1:1시간 지각 member2: 0분 지각 member3:10분 지각
     */
    @Test
    void 팀_지각_순위_분석() {
        //given
        schedule1.arriveScheduleMember(
                schedule1.getScheduleMembers().get(0),
                schedule1.getScheduleTime()
        );
        schedule1.arriveScheduleMember(
                schedule1.getScheduleMembers().get(1),
                schedule1.getScheduleTime().minusMinutes(10)
        );
        schedule1.arriveScheduleMember(
                schedule1.getScheduleMembers().get(2),
                schedule1.getScheduleTime().plusMinutes(10)
        );
        schedule1.setTerm(schedule1.getScheduleTime().plusHours(1));

        schedule2.arriveScheduleMember(
                schedule2.getScheduleMembers().get(0),
                schedule2.getScheduleTime().plusHours(1)
        );
        schedule2.arriveScheduleMember(
                schedule2.getScheduleMembers().get(1),
                schedule2.getScheduleTime()
        );
        schedule2.arriveScheduleMember(
                schedule2.getScheduleMembers().get(2),
                schedule2.getScheduleTime().minusHours(1)
        );
        schedule2.setTerm(schedule2.getScheduleTime().plusHours(1));

        em.flush();

        //when
        teamResultAnalysisService.analyzeLateTimeResult(team.getId());

        //then
        String result = teamRepository.findById(team.getId()).get()
                .getTeamResult()
                .getLateTimeResult();
        List<TeamLateTimeResult> resultMembers = ObjectMapperUtil.parseJson(result, TeamLateTimeResultDto.class)
                .getMembers();

        assertThat(resultMembers)
                .extracting(TeamLateTimeResult::getMemberId)
                .containsExactly(member1.getId(), member3.getId(), member2.getId());

        assertThat(resultMembers)
                .extracting(TeamLateTimeResult::getLateTime)
                .containsExactly(60, 10, 0);
    }

    /**
     * //given
     * schedule1
     * member1 승 -> member2 패
     * member2 승 ->  member3 패
     * member3 패 ->  member2 패
     * schedule2
     * member1 승 -> member2 패
     * member2 패 -> member1 승
     * => member1:2/2 member2:0/2 member3:0/1
     */
    @Test
    void 팀_베팅_승률_분석() {
        //given
        Betting betting1 = Betting.create(
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(1).getId()),
                20
        );
        betting1.setWin(20);
        em.persist(betting1);

        Betting betting2 = Betting.create(
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(1).getId()),
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(2).getId()),
                10
        );
        betting2.setWin(20);
        em.persist(betting2);

        Betting betting3 = Betting.create(
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(2).getId()),
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(1).getId()),
                20
        );
        betting3.setLose();
        em.persist(betting3);

        Betting betting4 = Betting.create(
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(1).getId()),
                10
        );
        betting4.setWin(20);
        em.persist(betting4);

        Betting betting5 = Betting.create(
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(1).getId()),
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(0).getId()),
                10
        );
        betting5.setLose();
        em.persist(betting5);

        schedule1.setTerm(LocalDateTime.now());
        schedule2.setTerm(LocalDateTime.now());

        em.flush();

        //when
        teamResultAnalysisService.analyzeBettingResult(team.getId());

        //then
        String result = teamRepository.findById(team.getId()).get()
                .getTeamResult()
                .getTeamBettingResult();

        List<TeamBettingResult> resultMembers = ObjectMapperUtil.parseJson(result, TeamBettingResultDto.class)
                .getMembers();

        assertThat(resultMembers)
                .extracting(TeamBettingResult::getMemberId)
                .containsExactly(member1.getId(), member2.getId(), member3.getId());

        assertThat(resultMembers)
                .extracting(TeamBettingResult::getOdds)
                .containsExactly(1L, 0L, 0L);
    }

    /**
     * //given
     * schedule1
     * member1 승 -> member2 패
     * member2 승 ->  member3 패
     * member3 패 ->  member2 패
     * schedule2
     * member1 승 -> member2 패
     * member2 패 -> member1 승
     * => member1:2/2 member2:0/2 member3:0/1
     */
    @Test
    void 팀_레이싱_승률_분석() {
        //given
        Racing racing1 = Racing.create(
                schedule1.getScheduleMembers().get(0).getId(),
                schedule1.getScheduleMembers().get(1).getId(),
                20
        );
        racing1.termRacing(schedule1.getScheduleMembers().get(0).getId());
        em.persist(racing1);

        Racing racing2 = Racing.create(
                schedule1.getScheduleMembers().get(1).getId(),
                schedule1.getScheduleMembers().get(2).getId(),
                10
        );
        racing2.termRacing(schedule1.getScheduleMembers().get(1).getId());
        em.persist(racing2);

        Racing racing3 = Racing.create(
                schedule1.getScheduleMembers().get(2).getId(),
                schedule1.getScheduleMembers().get(1).getId(),
                20
        );
        racing3.termRacing(schedule1.getScheduleMembers().get(1).getId());
        em.persist(racing3);

        Racing racing4 = Racing.create(
                schedule2.getScheduleMembers().get(0).getId(),
                schedule2.getScheduleMembers().get(1).getId(),
                10
        );
        racing4.termRacing(schedule2.getScheduleMembers().get(0).getId());
        em.persist(racing4);

        Racing racing5 = Racing.create(
                schedule2.getScheduleMembers().get(1).getId(),
                schedule2.getScheduleMembers().get(0).getId(),
                10
        );
        racing5.termRacing(schedule2.getScheduleMembers().get(0).getId());
        em.persist(racing5);

        schedule1.setTerm(LocalDateTime.now());
        schedule2.setTerm(LocalDateTime.now());

        em.flush();

        //when
        teamResultAnalysisService.analyzeRacingResult(team.getId());

        //then
        String result = teamRepository.findById(team.getId()).get()
                .getTeamResult()
                .getTeamRacingResult();

        List<TeamRacingResult> resultMembers = ObjectMapperUtil.parseJson(result, TeamRacingResultDto.class)
                .getMembers();

        assertThat(resultMembers)
                .extracting(TeamRacingResult::getMemberId)
                .containsExactly(member1.getId(), member2.getId(), member3.getId());

        assertThat(resultMembers)
                .extracting(TeamRacingResult::getOdds)
                .containsExactly(1L, 0L, 0L);
    }

    @Test
    void 팀_탈퇴시_분석_결과_업데이트() {
        //given
        TeamLateTimeResult teamLateTimeResult = new TeamLateTimeResult(
                member2.getId(),
                member2.getNickname(),
                new MemberProfileResDto(member2.getProfile()),
                0,
                Status.ALIVE
        );
        TeamLateTimeResultDto teamLateTimeResultDto = new TeamLateTimeResultDto(team.getId(), List.of(teamLateTimeResult));
        team.setTeamLateResult(ObjectMapperUtil.toJson(teamLateTimeResultDto));

        TeamBettingResult teamBettingResult = new TeamBettingResult(
                member2.getId(),
                member2.getNickname(),
                new MemberProfileResDto(member2.getProfile()),
                0,
                Status.ALIVE
        );
        TeamBettingResultDto teamBettingResultDto = new TeamBettingResultDto(team.getId(), List.of(teamBettingResult));
        team.setTeamBettingResult(ObjectMapperUtil.toJson(teamBettingResultDto));

        TeamRacingResult teamRacingResult = new TeamRacingResult(
                member2.getId(),
                member2.getNickname(),
                new MemberProfileResDto(member2.getProfile()),
                0,
                Status.ALIVE
        );
        TeamRacingResultDto teamRacingResultDto = new TeamRacingResultDto(team.getId(), List.of(teamRacingResult));
        team.setTeamRacingResult(ObjectMapperUtil.toJson(teamRacingResultDto));

        em.flush();

        //when
        teamResultAnalysisService.updateTeamResultOfExitMember(member2.getId(), team.getId());

        //then
        TeamResult result = teamRepository.findById(team.getId()).get()
                .getTeamResult();

        TeamLateTimeResult teamLateTimeResultMem = ObjectMapperUtil.parseJson(result.getLateTimeResult(), TeamLateTimeResultDto.class)
                .getMembers()
                .get(0);
        assertThat(teamLateTimeResultMem.isTeamMember()).isFalse();

        TeamBettingResult teamBettingResultMem = ObjectMapperUtil.parseJson(result.getTeamBettingResult(), TeamBettingResultDto.class)
                .getMembers()
                .get(0);
        assertThat(teamBettingResultMem.isTeamMember()).isFalse();

        TeamRacingResult teamRacingResultMem = ObjectMapperUtil.parseJson(result.getTeamRacingResult(), TeamRacingResultDto.class)
                .getMembers()
                .get(0);
        assertThat(teamRacingResultMem.isTeamMember()).isFalse();

    }

    Member createMember(){
        Member member = Member.builder()
                .nickname(UUID.randomUUID().toString())
                .build();
        member.updateProfile(MemberProfileType.CHAR, null, null, null);
        member.updatePointAmount(100);

        return member;
    }

    Schedule createSchedule(Member member, Team team){
        return Schedule.create(
                member,
                new TeamValue(team.getId()),
                UUID.randomUUID().toString(),
                LocalDateTime.now().plusHours(3),
                new Location(UUID.randomUUID().toString(), 1.1, 1.1),
                100
        );
    }
}