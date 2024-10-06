/*
package common.domain;

import common.domain.schedule.ScheduleMember;
import common.domain.value_reference.ScheduleMemberValue;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.*;

class BettingTest {

    @Test
    void create() {
        ScheduleMemberValue bettor = new ScheduleMemberValue(Mockito.spy(new ScheduleMember()));
        ScheduleMemberValue betee = new ScheduleMemberValue(Mockito.spy(new ScheduleMember()));
        int pointAmount = 3000;
        Betting betting = Betting.create(bettor, betee, pointAmount);

        assertThat(betting.getBettor()).isEqualTo(bettor);
        assertThat(betting.getBetee()).isEqualTo(betee);
        assertThat(betting.getPointAmount()).isEqualTo(pointAmount);
        assertThat(betting.getBettingStatus()).isEqualTo(ExecStatus.WAIT);
        assertThat(betting.getStatus()).isEqualTo(Status.ALIVE);
    }
}*/
