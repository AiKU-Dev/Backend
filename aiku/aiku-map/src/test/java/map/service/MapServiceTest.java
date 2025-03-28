package map.service;

import common.domain.Location;
import common.domain.member.Member;
import common.domain.schedule.Schedule;
import common.domain.schedule.ScheduleMember;
import common.domain.team.Team;
import common.domain.value_reference.TeamValue;
import jakarta.persistence.EntityManager;
import map.dto.MemberArrivalDto;
import map.dto.ScheduleDetailResDto;
import map.dto.ScheduleMemberResDto;
import map.exception.ScheduleException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@SpringBootTest
public class MapServiceTest {
    @Autowired
    EntityManager em;

    @Autowired
    MapService mapService;

    Member member1;
    Member member2;
    Member member3;

    ScheduleMember scheduleMember1;
    ScheduleMember scheduleMember2;
    ScheduleMember scheduleMember3;

    Team team1;
    Schedule schedule1;

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

        List<ScheduleMember> scheduleMembers = schedule1.getScheduleMembers();

        for (ScheduleMember scheduleMember : scheduleMembers) {
            if (scheduleMember.getMember().getId().equals(member1.getId())) {
                scheduleMember1 = scheduleMember;
            } else if (scheduleMember.getMember().getId().equals(member2.getId())) {
                scheduleMember2 = scheduleMember;
            } else {
                scheduleMember3 = scheduleMember;
            }
        }

        schedule1.setRun();

        em.flush();
        em.clear();

        System.out.println("========= init done =========");
    }

    @Test
    void 멤버_도착_정상() {
        LocalDateTime arrivalTime = LocalDateTime.now();
        MemberArrivalDto memberArrivalDto = new MemberArrivalDto(arrivalTime);

        mapService.makeMemberArrive(member1.getId(), schedule1.getId(), memberArrivalDto);

        ScheduleMember scheduleMember = em.find(ScheduleMember.class, scheduleMember1.getId());

        assertThat(scheduleMember.getArrivalTime()).isEqualTo(arrivalTime);
    }

    @Test
    void 멤버_도착_외부멤버_예외() {
        Member member4 = Member.builder()
                .oauthId(4L)
                .nickname("member4")
                .email("member4@sample.com")
                .password("4")
                .build();

        em.persist(member4);

        LocalDateTime arrivalTime = LocalDateTime.now();
        MemberArrivalDto memberArrivalDto = new MemberArrivalDto(arrivalTime);

        org.junit.jupiter.api.Assertions.assertThrows(ScheduleException.class, () -> {
            mapService.makeMemberArrive(member4.getId(), schedule1.getId(), memberArrivalDto);
        });
    }

    @Test
    void 멤버_도착_스케줄_대기_예외() {
        Member member4 = Member.builder()
                .oauthId(4L)
                .nickname("member4")
                .email("member4@sample.com")
                .password("4")
                .build();

        Member member5 = Member.builder()
                .oauthId(5L)
                .nickname("member5")
                .email("member5@sample.com")
                .password("5")
                .build();

        em.persist(member4);
        em.persist(member5);

        Schedule schedule2 = Schedule.create(member4, new TeamValue(team1.getId()), "schedule2",
                LocalDateTime.of(2100, Month.JANUARY, 11, 13, 30, 00),
                new Location("location1", 1.0, 1.0), 30);

        schedule2.addScheduleMember(member5, false, 30);

        em.persist(schedule2);

        em.flush();
        em.clear();

        MemberArrivalDto memberArrivalDto = new MemberArrivalDto(LocalDateTime.now());

        org.junit.jupiter.api.Assertions.assertThrows(ScheduleException.class, () -> {
            mapService.makeMemberArrive(member4.getId(), schedule2.getId(), memberArrivalDto);
        });
    }

    @Test
    void 맵_스케줄_상세_조회() {
        //given
        Member memb1 = Member.builder()
                .oauthId(1L)
                .nickname("member1")
                .email("member1@sample.com")
                .password("1")
                .build();
        em.persist(memb1);

        Member memb2 = Member.builder()
                .oauthId(2L)
                .nickname("member2")
                .email("member2@sample.com")
                .password("2")
                .build();
        em.persist(memb2);

        Team team = Team.create(memb1, "team");
        team.addTeamMember(memb2);
        em.persist(team);

        Schedule schedule = Schedule.create(memb1, new TeamValue(team.getId()), "sche", LocalDateTime.now(),
                new Location("loc", 1.0, 1.0), 100);
        schedule.addScheduleMember(memb2, false, 0);
        em.persist(schedule);

        //when
        ScheduleDetailResDto result = mapService.getScheduleDetail(memb1.getId(), schedule.getId());

        //then
        assertThat(result.getScheduleId()).isEqualTo(schedule.getId());

        List<ScheduleMemberResDto> scheduleMembers = result.getMembers();
        assertThat(scheduleMembers.size()).isEqualTo(2);
        assertThat(scheduleMembers).extracting("memberId").contains(memb1.getId(), memb2.getId());
        assertThat(scheduleMembers).extracting("isArrive").contains(false, false);
    }

}
