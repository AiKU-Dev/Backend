package aiku_main.service.schedule;

import aiku_main.dto.*;
import aiku_main.dto.schedule.*;
import aiku_main.exception.ScheduleException;
import aiku_main.exception.TeamException;
import aiku_main.repository.member.MemberRepository;
import aiku_main.repository.schedule.ScheduleRepository;
import aiku_main.repository.team.TeamRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.domain.*;
import common.domain.betting.Betting;
import common.domain.member.Member;
import common.domain.schedule.Schedule;
import common.domain.schedule.ScheduleMember;
import common.domain.team.Team;
import common.domain.value_reference.ScheduleMemberValue;
import common.domain.value_reference.TeamValue;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static common.domain.Status.ALIVE;
import static common.domain.Status.DELETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@SpringBootTest
public class ScheduleServiceIntegrationTest {

    @Autowired
    ScheduleService scheduleService;
    @Autowired
    EntityManager em;
    @Autowired
    ScheduleRepository scheduleRepository;
    @Autowired
    TeamRepository teamRepository;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    ObjectMapper objectMapper;

    Member teamOwner;
    Member member1;
    Member member2;
    Member member3;
    Member member4;
    Team team;

    Random random = new Random();

    @Value("${schedule.fee.participation}")
    private int scheduleEnterPoint;

    @BeforeEach
    void beforeEach(){
        teamOwner = createMember();
        member1 = createMember();
        member2 = createMember();
        member3 = createMember();
        member4 = createMember();
        em.persist(teamOwner);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);

        team = Team.create(teamOwner, "team");
        em.persist(team);
    }

    @AfterEach
    void afterEach(){
        teamRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void 스케줄_등록() {
        //when
        ScheduleAddDto scheduleDto = new ScheduleAddDto(
                "schedule",
                new LocationDto("lo1", 1.0, 1.0),
                LocalDateTime.now().plusHours(1)
        );
        Long scheduleId = scheduleService.addSchedule(teamOwner.getId(), team.getId(), scheduleDto);

        //then
        Schedule schedule = scheduleRepository.findById(scheduleId).get();
        assertThat(schedule.getScheduleStatus()).isEqualTo(ExecStatus.WAIT);
        assertThat(schedule.getScheduleMembers()).hasSize(1);
        assertThat(schedule.getScheduleMembers())
                .extracting((scheduleMember) -> scheduleMember.getMember().getId())
                .contains(teamOwner.getId());
    }

    @Test
    void 스케줄_등록_팀멤버x() {
        //when
        ScheduleAddDto scheduleDto = new ScheduleAddDto(
                "schedule",
                new LocationDto("lo1", 1.0, 1.0),
                LocalDateTime.now().plusHours(1)
        );
        assertThatThrownBy(() -> scheduleService.addSchedule(member2.getId(), team.getId(), scheduleDto))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void 스케줄_수정() {
        //given
        Schedule schedule = createSchedule(teamOwner, team);
        em.persist(schedule);

        //when
        ScheduleUpdateDto scheduleDto = new ScheduleUpdateDto(
                "new schedule",
                new LocationDto("new location", 2.0, 2.0),
                LocalDateTime.now().plusHours(2)
        );
        scheduleService.updateSchedule(teamOwner.getId(), schedule.getId(), scheduleDto);

        //then
        Schedule findSchedule = scheduleRepository.findById(schedule.getId()).get();
        assertThat(findSchedule.getScheduleName()).isEqualTo(scheduleDto.getScheduleName());
        assertThat(findSchedule.getLocation().getLocationName()).isEqualTo(scheduleDto.getLocation().getLocationName());
    }

    @Test
    void 스케줄_수정_방장x() {
        //given
        team.addTeamMember(member1);
        em.flush();

        Schedule schedule = createSchedule(teamOwner, team);
        schedule.addScheduleMember(member1, false, 0);
        em.persist(schedule);

        //when
        ScheduleUpdateDto scheduleDto = new ScheduleUpdateDto(
                "new schedule",
                new LocationDto("new location", 2.0, 2.0),
                LocalDateTime.now().plusHours(2)
        );
        assertThatThrownBy(() -> scheduleService.updateSchedule(member1.getId(), schedule.getId(), scheduleDto))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void 스케줄_수정_스케줄시간1시간이내() {
        //given
        Schedule schedule = Schedule.create(
                teamOwner,
                new TeamValue(team.getId()),
                "schedule",
                LocalDateTime.now().plusMinutes(30),
                new Location("loc1", 1.0, 1.0),
                scheduleEnterPoint
        );
        em.persist(schedule);

        //when
        ScheduleUpdateDto scheduleDto = new ScheduleUpdateDto(
                "new schedule",
                new LocationDto("new location", 2.0, 2.0),
                LocalDateTime.now().plusHours(2)
        );
        assertThatThrownBy(() -> scheduleService.updateSchedule(teamOwner.getId(), schedule.getId(), scheduleDto))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void 스케줄_입장() {
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        em.flush();

        Schedule schedule = createSchedule(member1, team);
        em.persist(schedule);

        //when
        Long scheduleId = scheduleService.enterSchedule(member2.getId(), team.getId(), schedule.getId());

        //then
        Schedule testSchedule = scheduleRepository.findById(scheduleId).get();

        List<ScheduleMember> scheduleMembers = testSchedule.getScheduleMembers();
        assertThat(scheduleMembers).hasSize(2);
        assertThat(scheduleMembers)
                .extracting((scheduleMember) -> scheduleMember.getMember().getId())
                .contains(member1.getId(), member2.getId());
        assertThat(scheduleMembers)
                .extracting(ScheduleMember::isOwner)
                .contains(true, false);
    }

    @Test
    void 스케줄_입장_중복() {
        //given
        Schedule schedule = createSchedule(teamOwner, team);
        em.persist(schedule);

        //when
        assertThatThrownBy(() -> scheduleService.enterSchedule(teamOwner.getId(), team.getId(), schedule.getId()))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void 스케줄_입장_팀멤버X() {
        //given
        Schedule schedule = createSchedule(teamOwner, team);
        em.persist(schedule);

        //when
        assertThatThrownBy(() -> scheduleService.enterSchedule(member1.getId(), team.getId(), schedule.getId()))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void 스케줄_입장_대기스케줄x() {
        //given
        team.addTeamMember(member1);
        em.flush();

        Schedule schedule = createSchedule(teamOwner, team);
        schedule.setRun();
        em.persist(schedule);

        //when
        assertThatThrownBy(() -> scheduleService.enterSchedule(member1.getId(), team.getId(), schedule.getId()))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void 스케줄_퇴장() {
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        team.addTeamMember(member3);
        em.persist(team);

        Schedule schedule = createSchedule(member1, team);
        schedule.addScheduleMember(member2, false, scheduleEnterPoint);
        schedule.addScheduleMember(member3, false, scheduleEnterPoint);
        em.persist(schedule);

        //when
        scheduleService.exitSchedule(member2.getId(), team.getId(), schedule.getId());

        //then
        Schedule testSchedule = scheduleRepository.findById(schedule.getId()).get();

        List<ScheduleMember> scheduleMembers = testSchedule.getScheduleMembers();
        assertThat(scheduleMembers).hasSize(3);
        assertThat(scheduleMembers)
                .extracting(ScheduleMember::getStatus)
                .contains(ALIVE, ALIVE, DELETE);
    }

    @Test
    void 스케줄_퇴장_스케줄멤버X() {
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        em.flush();

        Schedule schedule = createSchedule(member1, team);
        em.persist(schedule);

        //when
        assertThatThrownBy(() -> scheduleService.exitSchedule(member2.getId(), team.getId(), schedule.getId()))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void 스케줄_퇴장_중복() {
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        em.flush();

        Schedule schedule = createSchedule(member1, team);
        schedule.addScheduleMember(member2, false, scheduleEnterPoint);
        em.persist(schedule);

        scheduleService.exitSchedule(member2.getId(), team.getId(), schedule.getId());

        //when
        assertThatThrownBy(() -> scheduleService.exitSchedule(member2.getId(), team.getId(), schedule.getId()))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void 스케줄_퇴장_남은멤버x_자동삭제() {
        //given
        Schedule schedule = createSchedule(member1, team);
        schedule.addScheduleMember(member2, false, scheduleEnterPoint);
        em.persist(schedule);

        //when
        scheduleService.exitSchedule(member2.getId(), team.getId(), schedule.getId());
        scheduleService.exitSchedule(member1.getId(), team.getId(), schedule.getId());

        //then
        Schedule testSchedule = scheduleRepository.findById(schedule.getId()).get();
        assertThat(testSchedule.getStatus()).isEqualTo(DELETE);
    }

    @Test
    void 스케줄_퇴장_방장퇴장_방장변경() {
        //given
        Schedule schedule = createSchedule(member1, team);
        schedule.addScheduleMember(member2, false, scheduleEnterPoint);
        em.persist(schedule);

        //when
        scheduleService.exitSchedule(member1.getId(), team.getId(), schedule.getId());

        //then
        Schedule testSchedule = scheduleRepository.findById(schedule.getId()).get();
        assertThat(testSchedule.getStatus()).isEqualTo(ALIVE);

        ScheduleMember nextOwner = scheduleRepository.findScheduleMember(member2.getId(), schedule.getId()).get();
        assertThat(nextOwner.isOwner()).isTrue();
    }

    @Test
    void 스케줄_퇴장_대기스케줄x() {
        //given
        Schedule schedule = createSchedule(member1, team);
        schedule.addScheduleMember(member2, false, scheduleEnterPoint);
        schedule.setTerm(LocalDateTime.now());
        em.persist(schedule);

        //when
        assertThatThrownBy(() -> scheduleService.exitSchedule(member2.getId(), team.getId(), schedule.getId()))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void 스케줄_상세_조회() {
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        team.addTeamMember(member3);
        team.addTeamMember(member4);
        em.flush();

        Schedule schedule = createSchedule(member1, team);
        schedule.addScheduleMember(member2, false, scheduleEnterPoint);
        schedule.addScheduleMember(member3, false, scheduleEnterPoint);
        em.persist(schedule);

        //when
        ScheduleDetailResDto resultDto = scheduleService.getScheduleDetail(member1.getId(), team.getId(), schedule.getId());

        //then
        assertThat(resultDto.getScheduleId()).isEqualTo(schedule.getId());

        List<ScheduleMemberResDto> scheduleMembers = resultDto.getMembers();
        assertThat(scheduleMembers).hasSize(3);
        assertThat(scheduleMembers)
                .extracting(ScheduleMemberResDto::getMemberId)
                .containsExactly(member1.getId(), member2.getId(), member3.getId());
        assertThat(scheduleMembers)
                .extracting(ScheduleMemberResDto::isOwner)
                .containsExactly(true, false, false);
    }

    @Test
    void 스케줄_상세_조회_꼴찌_멤버_선택() {
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        team.addTeamMember(member3);
        team.addTeamMember(member4);
        em.flush();

        Schedule schedule = createSchedule(member1, team);
        schedule.addScheduleMember(member2, false, scheduleEnterPoint);
        schedule.addScheduleMember(member3, false, scheduleEnterPoint);
        em.persist(schedule);

        Long scheduleMemberId1 = scheduleRepository.findScheduleMemberId(member1.getId(), schedule.getId()).orElseThrow();
        Long scheduleMemberId2 = scheduleRepository.findScheduleMemberId(member2.getId(), schedule.getId()).orElseThrow();
        Betting betting = Betting.create(new ScheduleMemberValue(scheduleMemberId1), new ScheduleMemberValue(scheduleMemberId2), 100);
        em.persist(betting);

        //when
        ScheduleDetailResDto resultDto = scheduleService.getScheduleDetail(member1.getId(), team.getId(), schedule.getId());

        //then
        assertThat(resultDto.getScheduleId()).isEqualTo(schedule.getId());
        assertThat(resultDto.getMembers())
                .extracting(ScheduleMemberResDto::isBetee)
                .containsExactly(false, true, false);
    }

    @Test
    void 스케줄_상세_조회_스케줄멤버x() {
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        team.addTeamMember(member3);
        team.addTeamMember(member4);
        em.flush();

        Schedule schedule = createSchedule(member1, team);
        schedule.addScheduleMember(member2, false, scheduleEnterPoint);
        schedule.addScheduleMember(member3, false, scheduleEnterPoint);
        em.persist(schedule);

        //when
        assertThatThrownBy(() -> scheduleService.getScheduleDetail(member4.getId(), team.getId(), schedule.getId()))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void 스케줄_프리뷰_조회() {
        //given
        team.addTeamMember(member1);
        em.flush();

        Schedule schedule = createSchedule(member1, team);
        em.persist(schedule);

        //when
        SchedulePreviewResDto result = scheduleService.getSchedulePreview(member1.getId(), team.getId(), schedule.getId());

        //then
        assertThat(result.getScheduleId()).isEqualTo(schedule.getId());
        assertThat(result.getOwner().getMemberId()).isEqualTo(member1.getId());
    }

    @Test
    void 그룹_스케줄_목록_조회() {
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        team.addTeamMember(member3);
        em.flush();

        Schedule schedule1 = createSchedule(member1, team);
        schedule1.addScheduleMember(member2, false, scheduleEnterPoint);
        schedule1.addScheduleMember(member3, false, scheduleEnterPoint);
        schedule1.setRun();
        em.persist(schedule1);

        Schedule schedule2 = createSchedule(member2, team);
        schedule2.addScheduleMember(member3, false, scheduleEnterPoint);
        em.persist(schedule2);

        Schedule schedule3 = createSchedule(member3, team);
        em.persist(schedule3);

        //when
        TeamScheduleListResDto result = scheduleService.getTeamScheduleList(member1.getId(), team.getId(), new SearchDateCond(), 1);

        //then
        assertThat(result.getRunSchedule()).isEqualTo(1);
        assertThat(result.getWaitSchedule()).isEqualTo(2);

        List<TeamScheduleListEachResDto> schedules = result.getData();
        assertThat(schedules)
                .extracting(TeamScheduleListEachResDto::getScheduleId)
                .containsExactly(schedule3.getId(), schedule2.getId(), schedule1.getId());
        assertThat(schedules)
                .extracting(TeamScheduleListEachResDto::getMemberSize)
                .containsExactly(1, 2, 3);
        assertThat(schedules).
                extracting(TeamScheduleListEachResDto::isAccept)
                .containsExactly(false, false, true);
    }

    @Test
    void 그룹_스케줄_목록_조회_그룹멤버x() {
        //when
        assertThatThrownBy(() -> scheduleService.getTeamScheduleList(member1.getId(), team.getId(), new SearchDateCond(), 1))
                .isInstanceOf(TeamException.class);
    }

    @Test
    void 그룹_스케줄_목록_조회_필터링() {
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        team.addTeamMember(member3);
        em.flush();

        Schedule schedule1 = createSchedule(member1, team);
        schedule1.setRun();

        LocalDateTime startDate = LocalDateTime.now().plusHours(3);
        em.persist(schedule1);

        Schedule schedule2 = createSchedule(member2, team);
        em.persist(schedule2);

        LocalDateTime endDate = LocalDateTime.now().plusHours(3);

        Schedule schedule3 = createSchedule(member3, team);
        em.persist(schedule3);

        //when
        TeamScheduleListResDto result = scheduleService.getTeamScheduleList(member1.getId(), team.getId(), new SearchDateCond(startDate, endDate), 1);

        //then
        assertThat(result.getRunSchedule()).isEqualTo(0);
        assertThat(result.getWaitSchedule()).isEqualTo(1);

        List<TeamScheduleListEachResDto> schedules = result.getData();
        assertThat(schedules).hasSize(1);
        assertThat(schedules)
                .extracting(TeamScheduleListEachResDto::getScheduleId)
                .containsExactly(schedule2.getId());
        assertThat(schedules)
                .extracting(TeamScheduleListEachResDto::isAccept)
                .containsExactly(false);
    }

    @Test
    void 멤버_스케줄_목록_조회() {
        //given
        Team teamA = Team.create(member1, "teamA");
        teamA.addTeamMember(member2);
        teamA.addTeamMember(member3);
        em.persist(teamA);

        Team teamB = Team.create(member1, "teamB");
        teamB.addTeamMember(member2);
        em.persist(teamB);

        Schedule scheduleA1 = createSchedule(member1, teamA);
        scheduleA1.setRun();
        scheduleA1.addScheduleMember(member2, false, scheduleEnterPoint);
        em.persist(scheduleA1);

        Schedule scheduleA2 = createSchedule(member2, teamA);
        em.persist(scheduleA2);

        Schedule scheduleB1 = createSchedule(member1, teamB);
        scheduleB1.setRun();
        em.persist(scheduleB1);

        //when
        MemberScheduleListResDto result = scheduleService.getMemberScheduleList(member1.getId(), new SearchDateCond(), 1);

        //then
        assertThat(result.getWaitSchedule()).isEqualTo(0);
        assertThat(result.getRunSchedule()).isEqualTo(2);

        List<MemberScheduleListEachResDto> schedules = result.getData();
        assertThat(schedules)
                .extracting(MemberScheduleListEachResDto::getGroupId)
                .containsExactly(teamB.getId(), teamA.getId());
        assertThat(schedules)
                .extracting(MemberScheduleListEachResDto::getScheduleId)
                .containsExactly(scheduleB1.getId(), scheduleA1.getId());
        assertThat(schedules)
                .extracting(MemberScheduleListEachResDto::getMemberSize)
                .containsExactly(1, 2);
    }

    @Test
    void 멤버_스케줄_목록_조회_필터링() {
        //given
        Team teamA = Team.create(member1, "teamA");
        teamA.addTeamMember(member2);
        teamA.addTeamMember(member3);
        em.persist(teamA);

        Team teamB = Team.create(member1, "teamB");
        teamB.addTeamMember(member2);
        em.persist(teamB);

        Schedule scheduleA1 = createSchedule(member1, teamA);
        scheduleA1.setRun();
        scheduleA1.addScheduleMember(member2, false, 0);
        em.persist(scheduleA1);

        Schedule scheduleA2 = createSchedule(member2, teamA);
        em.persist(scheduleA2);

        LocalDateTime startDate = LocalDateTime.now().plusHours(3);

        Schedule scheduleB1 = createSchedule(member1, teamB);
        scheduleB1.setRun();
        em.persist(scheduleB1);

        Schedule scheduleB2 = createSchedule(member1, teamB);
        em.persist(scheduleB2);

        //when
        MemberScheduleListResDto result = scheduleService.getMemberScheduleList(member1.getId(), new SearchDateCond(startDate, null), 1);

        //then
        assertThat(result.getWaitSchedule()).isEqualTo(1);
        assertThat(result.getRunSchedule()).isEqualTo(1);

        List<MemberScheduleListEachResDto> schedules = result.getData();
        assertThat(schedules)
                .extracting(MemberScheduleListEachResDto::getGroupId)
                .containsExactly(teamB.getId(), teamB.getId());
        assertThat(schedules)
                .extracting(MemberScheduleListEachResDto::getScheduleId)
                .containsExactly(scheduleB2.getId(), scheduleB1.getId());
        assertThat(schedules)
                .extracting(MemberScheduleListEachResDto::getMemberSize)
                .containsExactly(1, 1);
    }
    
    @Test
    void 멤버_지정된달의_스케줄있는_날짜_조회() {
        //given
        Team team1 = Team.create(member1, "team1");
        Team team2 = Team.create(member1, "team2");
        em.persist(team1);
        em.persist(team2);

        LocalDateTime now = LocalDateTime.of(2024, 4, 1, 1, 1);
        Schedule schedule1 = Schedule.create(
                member1,
                new TeamValue(team1.getId()),
                "sche1",
                now.plusDays(3),
                new Location("loc", 1.0, 1.0),
                scheduleEnterPoint
        );
        Schedule schedule2 = Schedule.create(
                member1,
                new TeamValue(team2.getId()),
                "sche1",
                now.plusDays(3),
                new Location("loc", 1.0, 1.0),
                scheduleEnterPoint
        );
        Schedule schedule3 = Schedule.create(
                member1,
                new TeamValue(team2.getId()),
                "sche1",
                now.plusDays(4),
                new Location("loc", 1.0, 1.0),
                scheduleEnterPoint
        );
        em.persist(schedule1);
        em.persist(schedule2);
        em.persist(schedule3);

        //when
        List<LocalDate> result = scheduleService.getScheduleDatesInMonth(member1.getId(), new MonthDto(now.getYear(), now.getMonth().getValue())).getData();

        //then
        assertThat(result).hasSize(2);
        assertThat(result).contains(
                LocalDate.of(now.getYear(), now.getMonth(), now.getDayOfMonth() + 3),
                LocalDate.of(now.getYear(), now.getMonth(), now.getDayOfMonth() + 4)
        );
    }

    @Test
    void 이벤트핸들러_팀퇴장_참여중인_대기스케줄_퇴장() {
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        em.flush();

        Schedule schedule1 = createSchedule(member1, team);
        schedule1.addScheduleMember(member2, false, scheduleEnterPoint);
        em.persist(schedule1);

        Schedule schedule2 = createSchedule(member1, team);
        schedule2.addScheduleMember(member2, false, scheduleEnterPoint);
        em.persist(schedule2);

        Schedule schedule3 = createSchedule(member1, team);
        schedule3.addScheduleMember(member2, false, scheduleEnterPoint);
        schedule3.setTerm(LocalDateTime.now());
        em.persist(schedule3);

        Team teamB = Team.create(member1, "teamB");
        em.persist(teamB);

        Schedule scheduleB1 = createSchedule(member1, teamB);
        em.persist(scheduleB1);

        //when
        scheduleService.exitAllScheduleInTeam(member1.getId(), team.getId());

        //then
        assertThat(scheduleRepository.existScheduleMember(member1.getId(), schedule1.getId())).isFalse();
        assertThat(scheduleRepository.existScheduleMember(member1.getId(), schedule2.getId())).isFalse();
        assertThat(scheduleRepository.existScheduleMember(member1.getId(), schedule3.getId())).isTrue();
        assertThat(scheduleRepository.existScheduleMember(member1.getId(), scheduleB1.getId())).isTrue();
    }

    @Test
    void 이벤트핸들러_스케줄_결과_정산_지각자x(){
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        team.addTeamMember(member3);
        em.flush();

        Schedule schedule1 = createSchedule(member1, team);
        schedule1.addScheduleMember(member2, false, scheduleEnterPoint);
        schedule1.addScheduleMember(member3, false, scheduleEnterPoint);
        em.persist(schedule1);

        LocalDateTime arrivalTime = LocalDateTime.now();
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(0), arrivalTime);
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(1), arrivalTime);
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(2), arrivalTime);
        schedule1.setTerm(LocalDateTime.now());

        //when
        scheduleService.processScheduleResultPoint(schedule1.getId());

        //then
        Schedule findSchedule = scheduleRepository.findById(schedule1.getId()).orElse(null);
        assertThat(findSchedule).isNotNull();

        assertThat(findSchedule.getScheduleMembers())
                .extracting(ScheduleMember::getRewardPointAmount)
                .containsExactly(scheduleEnterPoint, scheduleEnterPoint, scheduleEnterPoint);
    }

    @Test
    void 이벤트핸들러_스케줄_결과_정산_모두지각자(){
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        team.addTeamMember(member3);
        em.flush();

        Schedule schedule1 = createSchedule(member1, team);
        schedule1.addScheduleMember(member2, false, scheduleEnterPoint);
        schedule1.addScheduleMember(member3, false, scheduleEnterPoint);
        em.persist(schedule1);

        LocalDateTime arrivalTime = LocalDateTime.now().plusHours(4);
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(0), arrivalTime);
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(1), arrivalTime);
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(2), arrivalTime);
        schedule1.setTerm(LocalDateTime.now());

        //when
        scheduleService.processScheduleResultPoint(schedule1.getId());

        //then
        Schedule findSchedule = scheduleRepository.findById(schedule1.getId()).orElse(null);
        assertThat(findSchedule).isNotNull();

        assertThat(findSchedule.getScheduleMembers())
                .extracting(ScheduleMember::getRewardPointAmount)
                .containsExactly(scheduleEnterPoint, scheduleEnterPoint, scheduleEnterPoint);
    }

    @Test
    void 이벤트핸들러_스케줄_결과_정산(){
        //given
        team.addTeamMember(member1);
        team.addTeamMember(member2);
        team.addTeamMember(member3);
        em.flush();

        Schedule schedule1 = createSchedule(member1, team);
        schedule1.addScheduleMember(member2, false, scheduleEnterPoint);
        schedule1.addScheduleMember(member3, false, scheduleEnterPoint);
        em.persist(schedule1);

        LocalDateTime arrivalTime = LocalDateTime.now();
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(0), arrivalTime.plusHours(5));
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(1), arrivalTime);
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(2), arrivalTime);
        schedule1.setTerm(LocalDateTime.now());

        //when
        scheduleService.processScheduleResultPoint(schedule1.getId());

        //then
        int rewardPoint = scheduleEnterPoint + scheduleEnterPoint / 2;
        Schedule testSchedule = scheduleRepository.findById(schedule1.getId()).get();
        assertThat(testSchedule.getScheduleMembers())
                .extracting(ScheduleMember::getRewardPointAmount)
                .contains(0, rewardPoint, rewardPoint);
    }

    @Test
    void 스케줄_종료() {
        //given
        Schedule schedule = createSchedule(member1, team);
        schedule.addScheduleMember(member2, false, 0);
        scheduleRepository.save(schedule);

        Arrival arrival1 = createArrival(schedule.getScheduleMembers().get(0).getId(), schedule.getScheduleTime().minusMinutes(10));
        Arrival arrival2 = createArrival(schedule.getScheduleMembers().get(1).getId(), schedule.getScheduleTime().plusMinutes(20));
        em.persist(arrival1);
        em.persist(arrival2);

        //when
        scheduleService.closeSchedule(schedule.getId(), schedule.getScheduleTime().plusMinutes(30));

        //then
        List<ScheduleMember> scheduleMembers = scheduleRepository.findScheduleMembersWithMember(schedule.getId());
        assertThat(scheduleMembers)
                .extracting(ScheduleMember::getArrivalTimeDiff)
                .containsExactlyInAnyOrder(10, -20);
    }

    Member createMember(){
        Member member = Member.builder()
                .nickname(UUID.randomUUID().toString())
                .build();
        member.updatePointAmount(100);

        return member;
    }

    Schedule createSchedule(Member member, Team team){
        return Schedule.create(
                member, 
                new TeamValue(team.getId()),
                UUID.randomUUID().toString(), 
                LocalDateTime.now().plusHours(3),
                new Location(UUID.randomUUID().toString(), random.nextDouble(), random.nextDouble()),
                scheduleEnterPoint);
    }

    Arrival createArrival(Long scheduleMemberId, LocalDateTime arrivalTime){
        return Arrival.builder()
                .scheduleMemberId(scheduleMemberId)
                .arrivalTime(arrivalTime)
                .build();
    }
}
