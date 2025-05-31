package common.domain.schedule;

import common.domain.Location;
import common.domain.member.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static common.domain.ExecStatus.*;
import static common.domain.Status.*;
import static org.assertj.core.api.Assertions.assertThat;

class ScheduleTest {

    Member member1;
    Member member2;

    Member fakeMember;
    Schedule fakeSchedule;
    ScheduleMember fakeScheduleMember;

    @BeforeEach
    void beforeEach(){
        member1 = new Member("member1");
        member2 = new Member("member2");

        fakeMember = new Member("fakeMember");
        fakeSchedule = createSchedule(fakeMember);
        fakeScheduleMember = fakeSchedule.getScheduleMembers().get(0);
    }

    @Test
    void create() {
        //given
        String scheduleName = "sche";
        LocalDateTime scheduleTime = LocalDateTime.now();
        Location location = new Location("loc1", 1.0, 1.0);
        int point = 100;

        //when
        Schedule schedule = Schedule.create(
                member1,
                null,
                scheduleName,
                scheduleTime,
                location,
                point
        );

        //then
        assertThat(schedule.getScheduleStatus()).isEqualTo(WAIT);
        assertThat(schedule.getScheduleMembers()).hasSize(1);
        assertThat(schedule.getScheduleTime()).isEqualTo(scheduleTime);
        assertThat(schedule.getLocation()).isEqualTo(location);

        ScheduleMember scheduleMember = schedule.getScheduleMembers().get(0);
        assertThat(scheduleMember.isOwner()).isEqualTo(true);
        assertThat(scheduleMember.getPointAmount()).isEqualTo(point);
    }

    @Test
    void update() {
        //given
        Schedule schedule = createSchedule(member1);

        String newScheName = "new";
        Location newLoc = new Location("new", 2.0, 2.0);
        LocalDateTime newTime = LocalDateTime.now().plusMinutes(30);

        //when
        schedule.update(newScheName, newTime, newLoc);

        //then
        assertThat(schedule.getScheduleName()).isEqualTo(newScheName);
        assertThat(schedule.getLocation()).isEqualTo(newLoc);
        assertThat(schedule.getScheduleTime()).isEqualTo(newTime);
    }

    @Test
    void delete() {
        //given
        Schedule schedule = createSchedule(member1);

        //when
        schedule.delete();

        //then
        assertThat(schedule.getStatus()).isEqualTo(DELETE);
    }

    @Test
    void error() {
        //given
        Schedule schedule = createSchedule(member1);

        //when
        schedule.error();

        //then
        assertThat(schedule.getStatus()).isEqualTo(ERROR);
    }

    @Test
    void addScheduleMember(){
        //given
        Schedule schedule = createSchedule(member1);

        int point = 999;

        //when
        schedule.addScheduleMember(member2, false, point);

        //then

        assertThat(schedule.getScheduleMembers()).hasSize(2);
        assertThat(schedule.getScheduleMembers())
                .extracting(ScheduleMember::isOwner)
                .containsExactlyInAnyOrder(true, false);

        assertThat(schedule.getScheduleMembers())
                .extracting(ScheduleMember::getPointAmount)
                .contains(point);
    }

    @Test
    void removeScheduleMember() {
        //given
        Schedule schedule = createSchedule(member1);
        ScheduleMember scheduleMember = schedule.getScheduleMembers().get(0);

        //when
        boolean result = schedule.removeScheduleMember(scheduleMember);

        //then
        assertThat(result).isTrue();
        assertThat(scheduleMember.getStatus()).isEqualTo(DELETE);
    }

    @Test
    void removeScheduleMember_스케줄_멤버_아님() {
        //given
        Schedule schedule = createSchedule(member1);

        //when
        boolean result = schedule.removeScheduleMember(fakeScheduleMember);

        //then
        assertThat(result).isFalse();
    }

    @Test
    void changeScheduleOwner() {
        //given
        Schedule schedule = createSchedule(member1);

        schedule.addScheduleMember(member2, false, 0);
        ScheduleMember scheduleMember2 = schedule.getScheduleMembers().get(1);

        //when
        schedule.changeScheduleOwner(scheduleMember2);

        //then
        assertThat(scheduleMember2.isOwner()).isTrue();
    }

    @Test
    void changeScheduleOwner_스케줄_멤버_아님() {
        //given
        Schedule schedule = createSchedule(member1);

        fakeSchedule.addScheduleMember(member2, false, 0);
        ScheduleMember fakeScheduleMember = fakeSchedule.getScheduleMembers().get(1);

        //when
        schedule.changeScheduleOwner(fakeScheduleMember);

        //then
        assertThat(fakeScheduleMember.isOwner()).isFalse();
    }

    @Test
    void arriveScheduleMember() {
        //given
        Schedule schedule = createSchedule(member1);
        ScheduleMember scheduleMember = schedule.getScheduleMembers().get(0);

        int lateTime = 10;
        LocalDateTime arriveTime = schedule.getScheduleTime().plusMinutes(lateTime);

        //when
        boolean result = schedule.arriveScheduleMember(scheduleMember, arriveTime);

        //then
        assertThat(result).isTrue();
        assertThat(scheduleMember.getArrivalTime()).isEqualTo(arriveTime);
        assertThat(scheduleMember.getArrivalTimeDiff()).isEqualTo(lateTime * -1);
    }

    @Test
    void arriveScheduleMember_스케줄_멤버_아님() {
        //given
        Schedule schedule = createSchedule(member1);

        //when
        boolean result = schedule.arriveScheduleMember(fakeScheduleMember, LocalDateTime.now());

        //then
        assertThat(result).isFalse();
    }

    @Test
    void rewardMember() {
        //given
        Schedule schedule = createSchedule(member1);
        ScheduleMember scheduleMember = schedule.getScheduleMembers().get(0);
        int rewardPoint = 100;

        //when
        boolean result = schedule.rewardMember(scheduleMember, rewardPoint);

        //then
        assertThat(result).isTrue();
        assertThat(scheduleMember.getRewardPointAmount()).isEqualTo(rewardPoint);
    }

    @Test
    void rewardMember_스케줄_멤버_아님() {
        //given
        Schedule schedule = createSchedule(member1);

        //when
        boolean result = schedule.rewardMember(fakeScheduleMember, 100);

        //then
        assertThat(result).isFalse();
    }

    @Test
    void close() {
        //given
        Schedule schedule = createSchedule(member1);
        schedule.addScheduleMember(member2, false, 0);

        ScheduleMember scheduleMember1 = schedule.getScheduleMembers().get(0);
        ScheduleMember scheduleMember2 = schedule.getScheduleMembers().get(1);
        ReflectionTestUtils.setField(scheduleMember1, "id", 1L);
        ReflectionTestUtils.setField(scheduleMember2, "id", 2L);

        LocalDateTime closeTime = LocalDateTime.now();
        int lateTime1 = 10;
        int lateTime2 = 20;
        LocalDateTime arriveTime1 = schedule.getScheduleTime().plusMinutes(lateTime1);
        LocalDateTime arriveTime2 = schedule.getScheduleTime().plusMinutes(lateTime2);
        Map<Long, LocalDateTime> arriveTimes = Map.of(
                1L, arriveTime1,
                2L, arriveTime2
        );


        //when
        schedule.close(closeTime, arriveTimes);

        //then
        assertThat(schedule.getScheduleStatus()).isEqualTo(TERM);
        assertThat(scheduleMember1.getArrivalTimeDiff()).isEqualTo(lateTime1 * -1);
        assertThat(scheduleMember2.getArrivalTimeDiff()).isEqualTo(lateTime2 * -1);
    }

    @Test
    void setTerm() {
        //given
        Schedule schedule = createSchedule(member1);
        LocalDateTime termTime = LocalDateTime.now().minusMinutes(10);

        //when
        schedule.setTerm(termTime);

        //then
        assertThat(schedule.getScheduleStatus()).isEqualTo(TERM);
        assertThat(schedule.getScheduleTermTime()).isEqualTo(termTime);
    }

    @Test
    void setRun() {
        //given
        Schedule schedule = createSchedule(member1);

        //when
        schedule.setRun();

        //then
        assertThat(schedule.getScheduleStatus()).isEqualTo(RUN);
    }

    @Test
    void isTerm() {
        //given
        Schedule schedule = createSchedule(member1);
        schedule.setTerm(LocalDateTime.now());

        //when
        boolean result = schedule.isTerm();

        //then
        assertThat(result).isTrue();
    }

    @Test
    void isTerm_false() {
        //given
        Schedule schedule = createSchedule(member1);

        //when
        boolean result = schedule.isTerm();

        //then
        assertThat(result).isFalse();
    }

    @Test
    void isWait() {
        //given
        Schedule schedule = createSchedule(member1);

        //when
        boolean result = schedule.isWait();

        //then
        assertThat(result).isTrue();
    }

    @Test
    void isWait_false() {
        //given
        Schedule schedule = createSchedule(member1);
        schedule.setRun();

        //when
        boolean result = schedule.isWait();

        //then
        assertThat(result).isFalse();
    }

    @Test
    void setScheduleArrivalResult() {
        //given
        Schedule schedule = createSchedule(member1);
        String result = "result";

        //when
        schedule.setScheduleArrivalResult(result);

        //then
        assertThat(schedule.getScheduleResult().getScheduleArrivalResult()).isEqualTo(result);
    }

    @Test
    void setScheduleBettingResult() {
        //given
        Schedule schedule = createSchedule(member1);
        String result = "result";

        //when
        schedule.setScheduleBettingResult(result);

        //then
        assertThat(schedule.getScheduleResult().getScheduleBettingResult()).isEqualTo(result);
    }

    @Test
    void setScheduleRacingResult() {
        //given
        Schedule schedule = createSchedule(member1);
        String result = "result";

        //when
        schedule.setScheduleRacingResult(result);

        //then
        assertThat(schedule.getScheduleResult().getScheduleRacingResult()).isEqualTo(result);
    }

    Schedule createSchedule(Member owner){
        return Schedule.create(
                owner,
                null,
                "scheduleName",
                LocalDateTime.now(),
                new Location("loc1", 1.0, 1.0),
                10
        );
    }
}