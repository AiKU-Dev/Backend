package common.domain.betting;

import common.domain.member.Member;
import common.domain.value_reference.ScheduleMemberValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static common.domain.ExecStatus.TERM;
import static common.domain.Status.DELETE;
import static org.assertj.core.api.Assertions.assertThat;

class BettingTest {

    Member member1;
    Member member2;

    @BeforeEach
    void beforeEach(){
        member1 = new Member("member1");
        member2 = new Member("member2");
    }

    @Test
    void create() {
        // given
        ScheduleMemberValue bettor = new ScheduleMemberValue(1L);
        ScheduleMemberValue betee = new ScheduleMemberValue(2L);
        int pointAmount = 50;

        // when
        Betting betting = Betting.create(bettor, betee, pointAmount);

        // then
        assertThat(betting.getBettor()).isEqualTo(bettor);
        assertThat(betting.getBetee()).isEqualTo(betee);
        assertThat(betting.getPointAmount()).isEqualTo(pointAmount);
    }

    @Test
    void setWin() {
        //given
        Betting betting = new Betting();
        int rewardPoint = 100;

        //when
        betting.setWin(rewardPoint);

        //then
        assertThat(betting.getBettingStatus()).isEqualTo(TERM);
        assertThat(betting.isWinner()).isTrue();
        assertThat(betting.getRewardPointAmount()).isEqualTo(rewardPoint);
    }

    @Test
    void setDraw() {
        //given
        int pointAmount = 100;
        Betting betting = Betting.create(null, null, pointAmount);

        //when
        betting.setDraw();

        //then
        assertThat(betting.getBettingStatus()).isEqualTo(TERM);
        assertThat(betting.isWinner()).isFalse();
        assertThat(betting.getRewardPointAmount()).isEqualTo(pointAmount);
    }

    @Test
    void setLose() {
        //given
        Betting betting = new Betting();

        //when
        betting.setLose();

        //then
        assertThat(betting.getBettingStatus()).isEqualTo(TERM);
        assertThat(betting.isWinner()).isFalse();
        assertThat(betting.getRewardPointAmount()).isEqualTo(0);
    }

    @Test
    void delete() {
        //given
        Betting betting = new Betting();

        //when
        betting.delete();

        //then
        assertThat(betting.getStatus()).isEqualTo(DELETE);
    }
}