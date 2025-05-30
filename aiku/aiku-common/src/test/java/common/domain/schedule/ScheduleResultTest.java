package common.domain.schedule;

import common.domain.Location;
import common.domain.member.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleResultTest {

    Member member;
    Schedule schedule;

    @BeforeEach
    void beforeEach() {
        member = new Member("member1");
        schedule = createSchedule(member);
    }

    @Test
    void setScheduleArrivalResult() {
        //given
        String result = "result";

        //when
        schedule.setScheduleArrivalResult(result);

        //then
        assertThat(schedule.getScheduleResult().getScheduleArrivalResult()).isEqualTo(result);
    }

    @Test
    void setScheduleBettingResult() {
        //given
        String result = "result";

        //when
        schedule.setScheduleBettingResult(result);

        //then
        assertThat(schedule.getScheduleResult().getScheduleBettingResult()).isEqualTo(result);
    }

    @Test
    void setScheduleRacingResult() {
        //given
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