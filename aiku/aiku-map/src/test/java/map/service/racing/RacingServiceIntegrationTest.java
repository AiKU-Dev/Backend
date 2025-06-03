package map.service.racing;

import common.domain.ExecStatus;
import common.domain.Location;
import common.domain.racing.Racing;
import common.domain.Status;
import common.domain.member.Member;
import common.domain.schedule.Schedule;
import common.domain.schedule.ScheduleMember;
import common.domain.team.Team;
import common.domain.value_reference.TeamValue;
import jakarta.persistence.EntityManager;
import map.dto.DataResDto;
import map.dto.RacingAddDto;
import map.dto.RacingResDto;
import map.exception.NotEnoughPointException;
import map.exception.RacingException;
import map.exception.ScheduleException;
import map.repository.racing.RacingRepository;
import map.service.RacingService;
import org.junit.jupiter.api.AfterEach;
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
class RacingServiceIntegrationTest {

    @Autowired
    EntityManager em;

    @Autowired
    private RacingService racingService;

    Member member1;
    Member member2;
    Member member3;

    ScheduleMember scheduleMember1;
    ScheduleMember scheduleMember2;
    ScheduleMember scheduleMember3;

    Team team1;
    Schedule schedule1;

    Racing racing1;
    Racing racing2;

    @Autowired
    private RacingRepository racingRepository;

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

        racing1 = Racing.create(scheduleMember1.getId(), scheduleMember2.getId(), 20);
        em.persist(racing1);

        racing1.startRacing();

        racing2 = Racing.create(scheduleMember2.getId(), scheduleMember3.getId(), 10);
        em.persist(racing2);

        racing2.startRacing();

        em.flush();
        em.clear();

        System.out.println("========= init done =========");
    }

    @AfterEach
    void afterEach() {
        racingRepository.deleteAll();
    }

    @Test
    void 레이싱_리스트_조회() {
        DataResDto<List<RacingResDto>> racings = racingService.getRacings(member1.getId(), schedule1.getId());

        List<RacingResDto> data = racings.getData();

        assertThat(data.size()).isEqualTo(2);
    }

    @Test
    void 레이싱_생성_정상() {
        RacingAddDto racingAddDto = new RacingAddDto(member3.getId(), 0);
        Long racingId = racingService.makeRacing(member1.getId(), schedule1.getId(), racingAddDto);

        Racing findRacing = em.find(Racing.class, racingId);

        assertThat(findRacing.getRaceStatus()).isEqualTo(ExecStatus.WAIT);
        assertThat(findRacing.getPointAmount()).isEqualTo(0);
        assertThat(findRacing.getFirstRacer().getId()).isEqualTo(scheduleMember1.getId());
        assertThat(findRacing.getSecondRacer().getId()).isEqualTo(scheduleMember3.getId());
    }

    @Test
    void 레이싱_생성_외부멤버_예외() {
        Member member4 = Member.builder()
                .oauthId(4L)
                .nickname("member4")
                .email("member4@sample.com")
                .password("4")
                .build();

        em.persist(member4);

        RacingAddDto racingAddDto = new RacingAddDto(member3.getId(), 0);

        org.junit.jupiter.api.Assertions.assertThrows(ScheduleException.class, () -> {
            racingService.makeRacing(member4.getId(), schedule1.getId(), racingAddDto);
        });
    }

    @Test
    void 레이싱_생성_스케줄_대기_예외() {
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

        em.persist(schedule2);

        schedule2.addScheduleMember(member5, false, 30);

        em.flush();
        em.clear();

        RacingAddDto racingAddDto = new RacingAddDto(member5.getId(), 0);

        org.junit.jupiter.api.Assertions.assertThrows(ScheduleException.class, () -> {
            racingService.makeRacing(member4.getId(), schedule2.getId(), racingAddDto);
        });
    }

//    @Test
//    void 레이싱_생성_깍두기_예외() {
//        Member member4 = Member.builder()
//                .kakaoId(4L)
//                .nickname("member4")
//                .email("member4@sample.com")
//                .password("4")
//                .build();
//
//        em.persist(member4);
//
//        schedule1 = em.find(Schedule.class, schedule1.getId()); // 트랜잭션 종료 후 다시 영속
//
//        schedule1.addScheduleMember(member4, false, 0);
//
//        em.flush();
//        em.clear();
//
//        RacingAddDto racingAddDto = new RacingAddDto(member3.getId(), 0);
//
//        org.junit.jupiter.api.Assertions.assertThrows(PaidMemberLimitException.class, () -> {
//            racingService.makeRacing(member4.getId(), schedule1.getId(), racingAddDto);
//        });
//    }

    @Test
    void 레이싱_생성_중복_예외() {
        RacingAddDto racingAddDto = new RacingAddDto(member2.getId(), 0);

        org.junit.jupiter.api.Assertions.assertThrows(RacingException.class, () -> {
            racingService.makeRacing(member1.getId(), schedule1.getId(), racingAddDto);
        });

        RacingAddDto racingAddDto2 = new RacingAddDto(member1.getId(), 0);

        org.junit.jupiter.api.Assertions.assertThrows(RacingException.class, () -> {
            racingService.makeRacing(member2.getId(), schedule1.getId(), racingAddDto2);
        });
    }

    //  두 유저 모두 충분한 포인트를 가졌는지 확인
    @Test
    void 레이싱_생성_포인트부족_예외() {
        Member member4 = Member.builder()
                .oauthId(4L)
                .nickname("member4")
                .email("member4@sample.com")
                .password("4")
                .build();

        em.persist(member4);

        Member member5 = Member.builder()
                .oauthId(5L)
                .nickname("member5")
                .email("member5@sample.com")
                .password("5")
                .build();

        em.persist(member5);

        schedule1 = em.find(Schedule.class, schedule1.getId()); // 트랜잭션 종료 후 다시 영속

        schedule1.addScheduleMember(member4, false, 20);
        schedule1.addScheduleMember(member5, false, 20);

        em.flush();
        em.clear();

        RacingAddDto racingAddDto = new RacingAddDto(member5.getId(), 20);

        org.junit.jupiter.api.Assertions.assertThrows(NotEnoughPointException.class, () -> {
            racingService.makeRacing(member4.getId(), schedule1.getId(), racingAddDto);
        });
    }

    @Test
    void 레이싱_수락_정상() {
        Racing newRacing = Racing.create(scheduleMember1.getId(), scheduleMember3.getId(), 0);
        em.persist(newRacing);

        racingService.acceptRacing(member3.getId(), schedule1.getId(), newRacing.getId());

        Racing findRacing = em.find(Racing.class, newRacing.getId());

        assertThat(findRacing.getRaceStatus()).isEqualTo(ExecStatus.RUN);
    }

    @Test
    void 레이싱_거절_정상() {
        Racing newRacing = Racing.create(scheduleMember1.getId(), scheduleMember3.getId(), 0);
        em.persist(newRacing);

        racingService.denyRacing(member3.getId(), schedule1.getId(), newRacing.getId());

        em.flush();
        em.clear();

        Racing findRacing = em.find(Racing.class, newRacing.getId());

        assertThat(findRacing.getStatus()).isEqualTo(Status.DELETE);
    }

    @Test
    void 멤버_도착_레이싱_종료() {
        Racing newRacing = Racing.create(scheduleMember1.getId(), scheduleMember3.getId(), 0);
        newRacing.startRacing();
        em.persist(newRacing);

        racingService.makeMemberWinnerInRacing(member2.getId(), schedule1.getId(), schedule1.getScheduleName());

        em.flush();
        em.clear();

        racing1 = em.find(Racing.class, racing1.getId());
        racing2 = em.find(Racing.class, racing2.getId());
        newRacing = em.find(Racing.class, newRacing.getId());

        assertThat(racing1.getRaceStatus()).isEqualTo(ExecStatus.TERM);
        assertThat(racing1.getWinner().getId()).isEqualTo(scheduleMember2.getId());
        assertThat(racing2.getRaceStatus()).isEqualTo(ExecStatus.TERM);
        assertThat(racing2.getWinner().getId()).isEqualTo(scheduleMember2.getId());
        assertThat(newRacing.getRaceStatus()).isEqualTo(ExecStatus.RUN);
        assertThat(newRacing.getWinner()).isNull();
    }

    @Test
    void 스케줄_종료_레이싱_무승부() {
        Racing newRacing = Racing.create(scheduleMember1.getId(), scheduleMember3.getId(), 0);
        newRacing.startRacing();
        em.persist(newRacing);

        racingService.makeMemberWinnerInRacing(member2.getId(), schedule1.getId(), schedule1.getScheduleName());
        racingService.terminateRunningRacing(schedule1.getId());

        em.flush();
        em.clear();

        racing1 = em.find(Racing.class, racing1.getId());
        racing2 = em.find(Racing.class, racing2.getId());
        newRacing = em.find(Racing.class, newRacing.getId());

        assertThat(racing1.getRaceStatus()).isEqualTo(ExecStatus.TERM);
        assertThat(racing1.getWinner().getId()).isEqualTo(scheduleMember2.getId());
        assertThat(racing2.getRaceStatus()).isEqualTo(ExecStatus.TERM);
        assertThat(racing2.getWinner().getId()).isEqualTo(scheduleMember2.getId());
        assertThat(newRacing.getRaceStatus()).isEqualTo(ExecStatus.TERM);
        assertThat(newRacing.getWinner()).isNull();
    }

}