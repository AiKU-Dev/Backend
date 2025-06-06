package aiku_main.service;

import aiku_main.dto.betting.BettingAddDto;
import aiku_main.exception.BettingException;
import aiku_main.exception.ScheduleException;
import aiku_main.repository.betting.BettingRepository;
import aiku_main.repository.member.MemberRepository;
import aiku_main.repository.schedule.ScheduleRepository;
import aiku_main.service.betting.BettingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.domain.*;
import common.domain.betting.Betting;
import common.domain.member.Member;
import common.domain.schedule.Schedule;
import common.domain.schedule.ScheduleMember;
import common.domain.value_reference.ScheduleMemberValue;
import common.exception.BaseException;
import common.exception.NotEnoughPointException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static common.domain.ExecStatus.*;
import static common.domain.Status.DELETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@SpringBootTest
class BettingServiceIntegrationTest {

    @Autowired
    EntityManager em;
    @Autowired
    BettingService bettingService;
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    ScheduleRepository scheduleRepository;
    @Autowired
    BettingRepository bettingRepository;
    @Autowired
    ObjectMapper objectMapper;

    Member member1;
    Member member2;
    Member member3;
    Member noScheduleMember;
    Member freeMember;

    Schedule schedule1;

    @BeforeEach
    void beforeEach(){
        member1 = Member.create("member1");
        member2 = Member.create("member2");
        member3 = Member.create("member3");
        freeMember = Member.create("freeMember");
        noScheduleMember = Member.create("noScheduleMember");
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(freeMember);
        em.persist(noScheduleMember);

        schedule1 = Schedule.create(
                member1,
                null,
                "schedule1",
                LocalDateTime.now(),
                new Location("loc1", 1.0, 1.0),
                1000
        );
        schedule1.addScheduleMember(member2, false, 1000);
        schedule1.addScheduleMember(member3, false, 1000);
        schedule1.addScheduleMember(freeMember, false, 0);
        em.persist(schedule1);
    }

    @AfterEach
    void afterEach(){
        scheduleRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void 베팅_등록() {
        //when
        BettingAddDto bettingDto = new BettingAddDto(member2.getId(), 0);
        Long bettingId = bettingService.addBetting(member1.getId(), schedule1.getId(), bettingDto);

        //then
        Betting betting = bettingRepository.findById(bettingId).orElse(null);
        ScheduleMember bettor = scheduleRepository.findScheduleMember(member1.getId(), schedule1.getId()).orElse(null);
        ScheduleMember betee = scheduleRepository.findScheduleMember(member2.getId(), schedule1.getId()).orElse(null);

        assertThat(betting).isNotNull();
        assertThat(betting.getBettor().getId()).isEqualTo(bettor.getId());
        assertThat(betting.getBetee().getId()).isEqualTo(betee.getId());
    }

    @Test
    void 베팅_등록_대기스케줄x() {
        //given
        schedule1.setTerm(LocalDateTime.now());

        //when
        BettingAddDto bettingDto = new BettingAddDto(member2.getId(), 0);
        assertThatThrownBy(() -> bettingService.addBetting(member1.getId(), schedule1.getId(), bettingDto))
                .isInstanceOf(ScheduleException.class);
    }

    @Test
    void 베팅_등록_스케줄멤버x() {
        //when
        BettingAddDto bettingDto = new BettingAddDto(member2.getId(), 0);
        assertThatThrownBy(() -> bettingService.addBetting(noScheduleMember.getId(), schedule1.getId(), bettingDto))
                .isInstanceOf(BaseException.class);
    }

    @Test
    void 베팅_등록_중복() {
        //given
        Long ScheduleMemberIdOfBettor = scheduleRepository.findScheduleMemberId(member1.getId(), schedule1.getId()).orElseThrow();
        Long ScheduleMemberIdOfBetee = scheduleRepository.findScheduleMemberId(member2.getId(), schedule1.getId()).orElseThrow();

        Betting betting = Betting.create(new ScheduleMemberValue(ScheduleMemberIdOfBettor), new ScheduleMemberValue(ScheduleMemberIdOfBetee), 0);
        em.persist(betting);

        //when
        BettingAddDto bettingDto = new BettingAddDto(member2.getId(), 0);
        assertThatThrownBy(() -> bettingService.addBetting(member1.getId(), schedule1.getId(), bettingDto))
                .isInstanceOf(BettingException.class);
    }

    @Test
    void 베팅_등록_포인트부족() {
        //when
        BettingAddDto bettingDto = new BettingAddDto(member2.getId(), 1000);

        //then
        assertThatThrownBy(() -> bettingService.addBetting(member1.getId(), schedule1.getId(), bettingDto))
                .isInstanceOf(NotEnoughPointException.class);
    }

    @Test
    void 베팅_취소() {
        //given
        Long ScheduleMemberIdOfBettor = scheduleRepository.findScheduleMemberId(member1.getId(), schedule1.getId()).orElseThrow();
        Long ScheduleMemberIdOfBetee = scheduleRepository.findScheduleMemberId(member2.getId(), schedule1.getId()).orElseThrow();

        Betting betting = Betting.create(new ScheduleMemberValue(ScheduleMemberIdOfBettor), new ScheduleMemberValue(ScheduleMemberIdOfBetee), 100);
        em.persist(betting);

        //when
        bettingService.cancelBetting(member1.getId(), schedule1.getId(), betting.getId());

        //then
        Betting findBetting = bettingRepository.findById(betting.getId()).orElseThrow();
        assertThat(findBetting.getBettingStatus()).isEqualTo(WAIT);
        assertThat(findBetting.getStatus()).isEqualTo(DELETE);
    }

    @Test
    void 베팅_취소_중복() {
        //given
        Long ScheduleMemberIdOfBettor = scheduleRepository.findScheduleMemberId(member1.getId(), schedule1.getId()).orElseThrow();
        Long ScheduleMemberIdOfBetee = scheduleRepository.findScheduleMemberId(member2.getId(), schedule1.getId()).orElseThrow();

        Betting betting = Betting.create(new ScheduleMemberValue(ScheduleMemberIdOfBettor), new ScheduleMemberValue(ScheduleMemberIdOfBetee), 100);
        em.persist(betting);

        //when
        bettingService.cancelBetting(member1.getId(), schedule1.getId(), betting.getId());
        assertThatThrownBy(() -> bettingService.cancelBetting(member1.getId(), schedule1.getId(), betting.getId()))
                .isInstanceOf(BettingException.class);
    }

    @Test
    void 베팅_취소_베터x() {
        //given
        Long ScheduleMemberIdOfBettor = scheduleRepository.findScheduleMemberId(member1.getId(), schedule1.getId()).orElseThrow();
        Long ScheduleMemberIdOfBetee = scheduleRepository.findScheduleMemberId(member2.getId(), schedule1.getId()).orElseThrow();

        Betting betting = Betting.create(new ScheduleMemberValue(ScheduleMemberIdOfBettor), new ScheduleMemberValue(ScheduleMemberIdOfBetee), 100);
        em.persist(betting);

        //when
        assertThatThrownBy(() -> bettingService.cancelBetting(member2.getId(), schedule1.getId(), betting.getId()))
                .isInstanceOf(BettingException.class);
    }

    @Test
    void 베팅_취소_스케줄멤버x() {
        //given
        Long ScheduleMemberIdOfBettor = scheduleRepository.findScheduleMemberId(member1.getId(), schedule1.getId()).orElseThrow();
        Long ScheduleMemberIdOfBetee = scheduleRepository.findScheduleMemberId(member2.getId(), schedule1.getId()).orElseThrow();

        Betting betting = Betting.create(new ScheduleMemberValue(ScheduleMemberIdOfBettor), new ScheduleMemberValue(ScheduleMemberIdOfBetee), 100);
        em.persist(betting);

        //when
        assertThatThrownBy(() -> bettingService.cancelBetting(noScheduleMember.getId(), schedule1.getId(), betting.getId())).isInstanceOf(ScheduleException.class);
    }

    @Test
    void 이벤트핸들러_스케줄퇴장_퇴장멤버가_베터인_베팅제거(){
        //given
        Long ScheduleMemberIdOfBettor = scheduleRepository.findScheduleMemberId(member1.getId(), schedule1.getId()).orElseThrow();
        Long ScheduleMemberIdOfBetee = scheduleRepository.findScheduleMemberId(member2.getId(), schedule1.getId()).orElseThrow();

        Betting betting = Betting.create(new ScheduleMemberValue(ScheduleMemberIdOfBettor), new ScheduleMemberValue(ScheduleMemberIdOfBetee), 100);
        em.persist(betting);

        //when
        bettingService.deleteBettingForBettor(member1.getId(), ScheduleMemberIdOfBettor, schedule1.getId());

        //then
        Betting findBetting = bettingRepository.findById(betting.getId()).orElse(null);
        assertThat(findBetting).isNotNull();
        assertThat(findBetting.getStatus()).isEqualTo(DELETE);
    }

    @Test
    void 이벤트핸들러_스케줄퇴장_퇴장멤버가_베티인_베팅제거(){
        //given
        Long ScheduleMemberIdOfBettor = scheduleRepository.findScheduleMemberId(member1.getId(), schedule1.getId()).orElseThrow();
        Long ScheduleMemberIdOfBetee = scheduleRepository.findScheduleMemberId(member2.getId(), schedule1.getId()).orElseThrow();

        Betting betting = Betting.create(new ScheduleMemberValue(ScheduleMemberIdOfBettor), new ScheduleMemberValue(ScheduleMemberIdOfBetee), 100);
        em.persist(betting);

        //when
        bettingService.deleteBettingForBettor(member2.getId(), ScheduleMemberIdOfBettor, schedule1.getId());

        //then
        Betting findBetting = bettingRepository.findById(betting.getId()).orElse(null);
        assertThat(findBetting).isNotNull();
        assertThat(findBetting.getStatus()).isEqualTo(DELETE);
    }

    @Test
    void 이벤트핸들러_베팅_결과_계산_지각자x_전원환급(){
        //given
        Long scheduleMemberId1 = scheduleRepository.findScheduleMemberId(member1.getId(), schedule1.getId()).orElseThrow();
        Long scheduleMemberId2 = scheduleRepository.findScheduleMemberId(member2.getId(), schedule1.getId()).orElseThrow();
        Long scheduleMemberId3 = scheduleRepository.findScheduleMemberId(member3.getId(), schedule1.getId()).orElseThrow();

        Betting betting1 = Betting.create(new ScheduleMemberValue(scheduleMemberId1), new ScheduleMemberValue(scheduleMemberId3), 100);
        Betting betting2 = Betting.create(new ScheduleMemberValue(scheduleMemberId2), new ScheduleMemberValue(scheduleMemberId3), 200);
        em.persist(betting1);
        em.persist(betting2);

        List<ScheduleMember> scheduleMembers = schedule1.getScheduleMembers();
        schedule1.arriveScheduleMember(scheduleMembers.get(0), LocalDateTime.now());
        schedule1.arriveScheduleMember(scheduleMembers.get(1), LocalDateTime.now());
        schedule1.arriveScheduleMember(scheduleMembers.get(2), LocalDateTime.now());

        //when
        bettingService.termBettingAndProcessResultPoint(schedule1.getId());

        //then
        List<Betting> bettings = bettingRepository.findBettingsInSchedule(schedule1.getId(), TERM);
        assertThat(bettings)
                .extracting(Betting::getRewardPointAmount)
                .contains(100, 200);
        assertThat(bettings)
                .extracting(Betting::isWinner)
                .contains(false, false);
    }

    @Test
    void 이벤트핸들러_베팅_결과_계산(){
        //given
        Long scheduleMemberId1 = scheduleRepository.findScheduleMemberId(member1.getId(), schedule1.getId()).orElseThrow();
        Long scheduleMemberId2 = scheduleRepository.findScheduleMemberId(member2.getId(), schedule1.getId()).orElseThrow();
        Long scheduleMemberId3 = scheduleRepository.findScheduleMemberId(member3.getId(), schedule1.getId()).orElseThrow();

        Betting betting1 = Betting.create(new ScheduleMemberValue(scheduleMemberId1), new ScheduleMemberValue(scheduleMemberId3), 100);
        Betting betting2 = Betting.create(new ScheduleMemberValue(scheduleMemberId2), new ScheduleMemberValue(scheduleMemberId3), 200);
        Betting betting3 = Betting.create(new ScheduleMemberValue(scheduleMemberId3), new ScheduleMemberValue(scheduleMemberId1), 300);
        em.persist(betting1);
        em.persist(betting2);
        em.persist(betting3);

        List<ScheduleMember> scheduleMembers = schedule1.getScheduleMembers();
        schedule1.arriveScheduleMember(scheduleMembers.get(0), LocalDateTime.now());
        schedule1.arriveScheduleMember(scheduleMembers.get(1), LocalDateTime.now());
        schedule1.arriveScheduleMember(scheduleMembers.get(2), LocalDateTime.now().plusMinutes(30));

        //when
        bettingService.termBettingAndProcessResultPoint(schedule1.getId());

        //then
        List<Betting> bettings = bettingRepository.findBettingsInSchedule(schedule1.getId(), TERM);
        assertThat(bettings.size()).isEqualTo(3);
        assertThat(bettings)
                .extracting(Betting::getRewardPointAmount)
                .contains(200, 400, 0);
        assertThat(bettings)
                .extracting(Betting::isWinner)
                .containsExactly(true, true, false);
    }

    @Test
    void 이벤트핸들러_베팅_결과_계산_꼴찌_여러명(){
        //given
        Long scheduleMemberId1 = scheduleRepository.findScheduleMemberId(member1.getId(), schedule1.getId()).orElseThrow();
        Long scheduleMemberId2 = scheduleRepository.findScheduleMemberId(member2.getId(), schedule1.getId()).orElseThrow();
        Long scheduleMemberId3 = scheduleRepository.findScheduleMemberId(member3.getId(), schedule1.getId()).orElseThrow();

        Betting betting1 = Betting.create(new ScheduleMemberValue(scheduleMemberId1), new ScheduleMemberValue(scheduleMemberId3), 100);
        Betting betting2 = Betting.create(new ScheduleMemberValue(scheduleMemberId2), new ScheduleMemberValue(scheduleMemberId2), 300);
        Betting betting3 = Betting.create(new ScheduleMemberValue(scheduleMemberId3), new ScheduleMemberValue(scheduleMemberId1), 200);
        em.persist(betting1);
        em.persist(betting2);
        em.persist(betting3);

        List<ScheduleMember> scheduleMembers = schedule1.getScheduleMembers();
        schedule1.arriveScheduleMember(scheduleMembers.get(0), schedule1.getScheduleTime().plusMinutes(30));
        schedule1.arriveScheduleMember(scheduleMembers.get(1), LocalDateTime.now());
        schedule1.arriveScheduleMember(scheduleMembers.get(2), schedule1.getScheduleTime().plusMinutes(30));

        //when
        bettingService.termBettingAndProcessResultPoint(schedule1.getId());

        //then
        List<Betting> bettings = bettingRepository.findBettingsInSchedule(schedule1.getId(), TERM);
        assertThat(bettings)
                .extracting(Betting::getRewardPointAmount)
                .contains(200, 400, 0);
        assertThat(bettings)
                .extracting(Betting::isWinner)
                .containsExactly(true, false, true);
    }
}