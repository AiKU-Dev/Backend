package aiku_main.service.title;

import aiku_main.service.schedule.ScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.domain.betting.Betting;
import common.domain.Location;
import common.domain.member.Member;
import common.domain.member.MemberProfileBackground;
import common.domain.member.MemberProfileCharacter;
import common.domain.member.MemberProfileType;
import common.domain.schedule.Schedule;
import common.domain.team.Team;
import common.domain.title.Title;
import common.domain.title.TitleCode;
import common.domain.title.TitleMember;
import common.domain.value_reference.ScheduleMemberValue;
import common.domain.value_reference.TeamValue;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

@Transactional
@SpringBootTest
public class TitleServiceIntegreationTest {
    @Autowired
    EntityManager em;

    @Autowired
    TitleService titleService;

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    ObjectMapper objectMapper;

    Member member1;
    Member member2;
    Member member3;

    Team team1;
    Schedule schedule1;
    Schedule schedule2;
    Schedule schedule3;
    Schedule schedule4;
    Schedule schedule5;
    Schedule schedule6;
    Schedule schedule7;
    Schedule schedule8;
    Schedule schedule9;
    Schedule schedule10;

    @BeforeEach
    void setUp() {
        member1 = Member.builder()
                .oauthId(1L)
                .nickname("member1")
                .email("member1@sample.com")
                .password("1")
                .build();

        member2 = Member.builder()
                .oauthId(2L)
                .nickname("member2")
                .email("member2@sample.com")
                .password("2")
                .build();

        member3 = Member.builder()
                .oauthId(3L)
                .nickname("member3")
                .email("member3@sample.com")
                .password("3")
                .build();

        member1.updateProfile(MemberProfileType.CHAR, null, MemberProfileCharacter.C01, MemberProfileBackground.GRAY);
        member2.updateProfile(MemberProfileType.CHAR, null, MemberProfileCharacter.C01, MemberProfileBackground.GRAY);
        member3.updateProfile(MemberProfileType.CHAR, null, MemberProfileCharacter.C01, MemberProfileBackground.GRAY);

        em.persist(member1);
        em.persist(member2);
        em.persist(member3);

        team1 = Team.create(member1, "team1");

        em.persist(team1);

        schedule1 = Schedule.create(member1, new TeamValue(team1.getId()), "schedule1",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location1", 1.0, 1.0), 30);

        em.persist(schedule1);

        schedule1.addScheduleMember(member2, false, 30);
        schedule1.addScheduleMember(member3, false, 30);

        em.flush();

        schedule2 = Schedule.create(member1, new TeamValue(team1.getId()), "schedule2",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location2", 1.0, 1.0), 30);

        em.persist(schedule2);

        schedule2.addScheduleMember(member2, false, 30);
        schedule2.addScheduleMember(member3, false, 30);

        em.flush();

        schedule3 = Schedule.create(member1, new TeamValue(team1.getId()), "schedule2",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location2", 1.0, 1.0), 30);

        schedule3.addScheduleMember(member2, false, 30);
        schedule3.addScheduleMember(member3, false, 30);

        em.persist(schedule3);

        schedule4 = Schedule.create(member1, new TeamValue(team1.getId()), "schedule2",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location2", 1.0, 1.0), 30);

        schedule4.addScheduleMember(member2, false, 30);
        schedule4.addScheduleMember(member3, false, 30);

        em.persist(schedule4);

        schedule5 = Schedule.create(member1, new TeamValue(team1.getId()), "schedule2",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location2", 1.0, 1.0), 30);

        schedule5.addScheduleMember(member2, false, 30);
        schedule5.addScheduleMember(member3, false, 30);

        em.persist(schedule5);

        schedule6 = Schedule.create(member1, new TeamValue(team1.getId()), "schedule2",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location2", 1.0, 1.0), 30);

        schedule6.addScheduleMember(member2, false, 30);
        schedule6.addScheduleMember(member3, false, 30);

        em.persist(schedule6);

        schedule7 = Schedule.create(member1, new TeamValue(team1.getId()), "schedule2",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location2", 1.0, 1.0), 30);

        schedule7.addScheduleMember(member2, false, 30);
        schedule7.addScheduleMember(member3, false, 30);

        em.persist(schedule7);

        schedule8 = Schedule.create(member1, new TeamValue(team1.getId()), "schedule2",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location2", 1.0, 1.0), 30);

        schedule8.addScheduleMember(member2, false, 30);
        schedule8.addScheduleMember(member3, false, 30);

        em.persist(schedule8);

        schedule9 = Schedule.create(member1, new TeamValue(team1.getId()), "schedule2",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location2", 1.0, 1.0), 30);

        schedule9.addScheduleMember(member2, false, 30);
        schedule9.addScheduleMember(member3, false, 30);

        em.persist(schedule9);

        schedule10 = Schedule.create(member1, new TeamValue(team1.getId()), "schedule2",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location2", 1.0, 1.0), 30);

        schedule10.addScheduleMember(member2, false, 30);
        schedule10.addScheduleMember(member3, false, 30);

        em.persist(schedule10);

        System.out.println("========= init done =========");
    }

    @Test
    void 스케줄_멤버_전원_조회() {
        List<Long> memberIdsInSchedule = titleService.getMemberIdsInSchedule(schedule1.getId());

        Assertions.assertThat(memberIdsInSchedule).contains(member1.getId(), member2.getId(), member3.getId());
    }

    @Test
    void 멤버_1만_포인트_칭호_부여() {
        Title points10kTitle = Title.create("만수르", "1만 포인트 달성", "img1", TitleCode.POINTS_MORE_THAN_10K);
        em.persist(points10kTitle);

        member1.updatePointAmount(10000);

        List<Long> memberIdsInSchedule = titleService.getMemberIdsInSchedule(schedule1.getId());

        titleService.checkAndGivePointsMoreThan10kTitle(memberIdsInSchedule);

        List<TitleMember> titleMembers = points10kTitle.getTitleMembers();

        Assertions.assertThat(titleMembers.size()).isEqualTo(1);
        Assertions.assertThat(titleMembers.get(0).getTitle().getTitleName()).isEqualTo("만수르");
        Assertions.assertThat(titleMembers.get(0).getMember().getNickname()).isEqualTo("member1");
    }

    @Test
    void 멤버_1만_포인트_칭호_중복_부여x() {
        Title points10kTitle = Title.create("만수르", "1만 포인트 달성", "img1", TitleCode.POINTS_MORE_THAN_10K);
        em.persist(points10kTitle);

        member1.updatePointAmount(10000);

        List<Long> memberIdsInSchedule = titleService.getMemberIdsInSchedule(schedule1.getId());

        titleService.checkAndGivePointsMoreThan10kTitle(memberIdsInSchedule);

        titleService.checkAndGivePointsMoreThan10kTitle(memberIdsInSchedule);

        List<TitleMember> titleMembers = points10kTitle.getTitleMembers();

        Assertions.assertThat(titleMembers.size()).isEqualTo(1);
        Assertions.assertThat(titleMembers.get(0).getTitle().getTitleName()).isEqualTo("만수르");
        Assertions.assertThat(titleMembers.get(0).getMember().getNickname()).isEqualTo("member1");
    }

    @Test
    void 멤버_일찍_도착_10회_칭호_부여() {
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 20, 00));
        schedule2.arriveScheduleMember(schedule2.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 20, 00));
        schedule3.arriveScheduleMember(schedule3.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 20, 00));
        schedule4.arriveScheduleMember(schedule4.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 20, 00));
        schedule5.arriveScheduleMember(schedule5.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 20, 00));
        schedule6.arriveScheduleMember(schedule6.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 20, 00));
        schedule7.arriveScheduleMember(schedule7.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 20, 00));
        schedule8.arriveScheduleMember(schedule8.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 20, 00));
        schedule9.arriveScheduleMember(schedule9.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 20, 00));
        schedule10.arriveScheduleMember(schedule10.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 20, 00));

        schedule1.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00));
        schedule2.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00));
        schedule3.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00));
        schedule4.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00));
        schedule5.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00));
        schedule6.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00));
        schedule7.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00));
        schedule8.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00));
        schedule9.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00));
        schedule10.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00));

        Title earlyArrival10TimesTitle = Title.create("기다림의 미학", "일찍 도착 10회 달성", "img2", TitleCode.EARLY_ARRIVAL_10_TIMES);
        em.persist(earlyArrival10TimesTitle);

        List<Long> memberIdsInSchedule = titleService.getMemberIdsInSchedule(schedule10.getId());
        titleService.checkAndGiveEarlyArrival10TimesTitle(memberIdsInSchedule);

        List<TitleMember> titleMembers = earlyArrival10TimesTitle.getTitleMembers();

        Assertions.assertThat(titleMembers.size()).isEqualTo(1);
        Assertions.assertThat(titleMembers.get(0).getTitle().getTitleName()).isEqualTo("기다림의 미학");
        Assertions.assertThat(titleMembers.get(0).getMember().getNickname()).isEqualTo("member1");
    }

    @Test
    void 멤버_지각_도착_5회_칭호_부여() {
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule2.arriveScheduleMember(schedule2.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule3.arriveScheduleMember(schedule3.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule4.arriveScheduleMember(schedule4.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule5.arriveScheduleMember(schedule5.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));

        schedule1.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule2.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule3.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule4.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule5.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));

        Title lateArrival5TimesTitle = Title.create("지각 뉴비", "지각 도착 5회 달성", "img3", TitleCode.LATE_ARRIVAL_5_TIMES);
        em.persist(lateArrival5TimesTitle);

        List<Long> memberIdsInSchedule = titleService.getMemberIdsInSchedule(schedule10.getId());
        titleService.checkAndGiveLateArrival5TimesTitle(memberIdsInSchedule);

        List<TitleMember> titleMembers = lateArrival5TimesTitle.getTitleMembers();

        Assertions.assertThat(titleMembers.size()).isEqualTo(1);
        Assertions.assertThat(titleMembers.get(0).getTitle().getTitleName()).isEqualTo("지각 뉴비");
        Assertions.assertThat(titleMembers.get(0).getMember().getNickname()).isEqualTo("member1");
    }

    @Test
    void 멤버_지각_도착_10회_칭호_부여() {
        schedule1.arriveScheduleMember(schedule1.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule2.arriveScheduleMember(schedule2.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule3.arriveScheduleMember(schedule3.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule4.arriveScheduleMember(schedule4.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule5.arriveScheduleMember(schedule5.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule6.arriveScheduleMember(schedule6.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule7.arriveScheduleMember(schedule7.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule8.arriveScheduleMember(schedule8.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule9.arriveScheduleMember(schedule9.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule10.arriveScheduleMember(schedule10.getScheduleMembers().get(0), LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));

        schedule1.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule2.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule3.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule4.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule5.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule6.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule7.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule8.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule9.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));
        schedule10.setTerm(LocalDateTime.of(2100, Month.JANUARY, 11, 13, 50, 00));

        Title lateArrival10TimesTitle = Title.create("지각 전문가", "지각 도착 10회 달성", "img4", TitleCode.LATE_ARRIVAL_10_TIMES);
        em.persist(lateArrival10TimesTitle);

        List<Long> memberIdsInSchedule = titleService.getMemberIdsInSchedule(schedule10.getId());
        titleService.checkAndGiveLateArrival10TimesTitle(memberIdsInSchedule);

        List<TitleMember> titleMembers = lateArrival10TimesTitle.getTitleMembers();

        Assertions.assertThat(titleMembers.size()).isEqualTo(1);
        Assertions.assertThat(titleMembers.get(0).getTitle().getTitleName()).isEqualTo("지각 전문가");
        Assertions.assertThat(titleMembers.get(0).getMember().getNickname()).isEqualTo("member1");
    }

    @Test
    void 베팅_승리_5회_칭호_부여() {
        Betting betting1 = Betting.create(
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(1).getId()),
                10);

        betting1.setWin(10);

        Betting betting2 = Betting.create(
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(1).getId()),
                10);

        betting2.setWin(10);

        Betting betting3 = Betting.create(
                new ScheduleMemberValue(schedule3.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule3.getScheduleMembers().get(1).getId()),
                10);

        betting3.setWin(10);

        Betting betting4 = Betting.create(
                new ScheduleMemberValue(schedule4.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule4.getScheduleMembers().get(1).getId()),
                10);

        betting4.setWin(10);

        Betting betting5 = Betting.create(
                new ScheduleMemberValue(schedule5.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule5.getScheduleMembers().get(1).getId()),
                10);

        betting5.setWin(10);

        em.persist(betting1);
        em.persist(betting2);
        em.persist(betting3);
        em.persist(betting4);
        em.persist(betting5);

        Title bettingWinning5TimesTitle = Title.create("고니", "베팅 승리 5회 달성", "img5", TitleCode.BETTING_WINNING_5_TIMES);
        em.persist(bettingWinning5TimesTitle);

        List<Long> memberIdsInSchedule = titleService.getMemberIdsInSchedule(schedule10.getId());
        titleService.checkAndGiveBettingWinning5TimesTitle(memberIdsInSchedule);

        List<TitleMember> titleMembers = bettingWinning5TimesTitle.getTitleMembers();

        Assertions.assertThat(titleMembers.size()).isEqualTo(1);
        Assertions.assertThat(titleMembers.get(0).getTitle().getTitleName()).isEqualTo("고니");
        Assertions.assertThat(titleMembers.get(0).getMember().getNickname()).isEqualTo("member1");
    }

    @Test
    void 베팅_승리_5회_칭호_부여_여러명() {
        Betting betting1 = Betting.create(
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(1).getId()),
                10);

        betting1.setWin(10);

        Betting betting2 = Betting.create(
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(1).getId()),
                10);

        betting2.setWin(10);

        Betting betting3 = Betting.create(
                new ScheduleMemberValue(schedule3.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule3.getScheduleMembers().get(1).getId()),
                10);

        betting3.setWin(10);

        Betting betting4 = Betting.create(
                new ScheduleMemberValue(schedule4.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule4.getScheduleMembers().get(1).getId()),
                10);

        betting4.setWin(10);

        Betting betting5 = Betting.create(
                new ScheduleMemberValue(schedule5.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule5.getScheduleMembers().get(1).getId()),
                10);

        betting5.setWin(10);

        Betting betting6 = Betting.create(
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(1).getId()),
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(2).getId()),
                10);

        betting6.setWin(10);

        Betting betting7 = Betting.create(
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(1).getId()),
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(2).getId()),
                10);

        betting7.setWin(10);

        Betting betting8 = Betting.create(
                new ScheduleMemberValue(schedule3.getScheduleMembers().get(1).getId()),
                new ScheduleMemberValue(schedule3.getScheduleMembers().get(2).getId()),
                10);

        betting8.setWin(10);

        Betting betting9 = Betting.create(
                new ScheduleMemberValue(schedule4.getScheduleMembers().get(1).getId()),
                new ScheduleMemberValue(schedule4.getScheduleMembers().get(2).getId()),
                10);

        betting9.setWin(10);

        Betting betting10 = Betting.create(
                new ScheduleMemberValue(schedule5.getScheduleMembers().get(1).getId()),
                new ScheduleMemberValue(schedule5.getScheduleMembers().get(2).getId()),
                10);

        betting10.setWin(10);

        em.persist(betting1);
        em.persist(betting2);
        em.persist(betting3);
        em.persist(betting4);
        em.persist(betting5);
        em.persist(betting6);
        em.persist(betting7);
        em.persist(betting8);
        em.persist(betting9);
        em.persist(betting10);

        Title bettingWinning5TimesTitle = Title.create("고니", "베팅 승리 5회 달성", "img5", TitleCode.BETTING_WINNING_5_TIMES);
        em.persist(bettingWinning5TimesTitle);

        List<Long> memberIdsInSchedule = titleService.getMemberIdsInSchedule(schedule10.getId());
        titleService.checkAndGiveBettingWinning5TimesTitle(memberIdsInSchedule);

        List<TitleMember> titleMembers = bettingWinning5TimesTitle.getTitleMembers();

        Assertions.assertThat(titleMembers.size()).isEqualTo(2);
    }

    @Test
    void 베팅_승리_10회_칭호_부여() {
        Betting betting1 = Betting.create(
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule1.getScheduleMembers().get(1).getId()),
                10);

        betting1.setLose();

        Betting betting2 = Betting.create(
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule2.getScheduleMembers().get(1).getId()),
                10);

        betting2.setLose();

        Betting betting3 = Betting.create(
                new ScheduleMemberValue(schedule3.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule3.getScheduleMembers().get(1).getId()),
                10);

        betting3.setLose();

        Betting betting4 = Betting.create(
                new ScheduleMemberValue(schedule4.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule4.getScheduleMembers().get(1).getId()),
                10);

        betting4.setLose();

        Betting betting5 = Betting.create(
                new ScheduleMemberValue(schedule5.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule5.getScheduleMembers().get(1).getId()),
                10);

        betting5.setLose();

        Betting betting6 = Betting.create(
                new ScheduleMemberValue(schedule6.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule6.getScheduleMembers().get(1).getId()),
                10);

        betting6.setLose();

        Betting betting7 = Betting.create(
                new ScheduleMemberValue(schedule7.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule7.getScheduleMembers().get(1).getId()),
                10);

        betting7.setLose();

        Betting betting8 = Betting.create(
                new ScheduleMemberValue(schedule8.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule8.getScheduleMembers().get(1).getId()),
                10);

        betting8.setLose();

        Betting betting9 = Betting.create(
                new ScheduleMemberValue(schedule9.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule9.getScheduleMembers().get(1).getId()),
                10);

        betting9.setLose();

        Betting betting10 = Betting.create(
                new ScheduleMemberValue(schedule10.getScheduleMembers().get(0).getId()),
                new ScheduleMemberValue(schedule10.getScheduleMembers().get(1).getId()),
                10);

        betting10.setLose();

        em.persist(betting1);
        em.persist(betting2);
        em.persist(betting3);
        em.persist(betting4);
        em.persist(betting5);
        em.persist(betting6);
        em.persist(betting7);
        em.persist(betting8);
        em.persist(betting9);
        em.persist(betting10);

        Title bettingLosing10TimesTitle = Title.create("기부 천사", "베팅 승리 10회 달성", "img6", TitleCode.BETTING_LOSING_10_TIMES);
        em.persist(bettingLosing10TimesTitle);

        List<Long> memberIdsInSchedule = titleService.getMemberIdsInSchedule(schedule10.getId());
        titleService.checkAndGiveBettingLosing10TimesTitle(memberIdsInSchedule);

        List<TitleMember> titleMembers = bettingLosing10TimesTitle.getTitleMembers();

        Assertions.assertThat(titleMembers.size()).isEqualTo(1);
        Assertions.assertThat(titleMembers.get(0).getTitle().getTitleName()).isEqualTo("기부 천사");
        Assertions.assertThat(titleMembers.get(0).getMember().getNickname()).isEqualTo("member1");
    }
}
