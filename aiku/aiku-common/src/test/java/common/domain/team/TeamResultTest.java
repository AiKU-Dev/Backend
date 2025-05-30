package common.domain.team;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamResultTest {

    @Test
    void hasNoLateTimeResult() {
        //given
        TeamResult teamResult = new TeamResult();

        //when
        boolean result = teamResult.hasNoLateTimeResult();

        //then
        assertThat(result).isTrue();
    }

    @Test
    void hasNoLateTimeResult_결과_있을때() {
        //given
        TeamResult teamResult = new TeamResult();
        teamResult.setLateTimeResult("결과");

        //when
        boolean result = teamResult.hasNoLateTimeResult();

        //then
        assertThat(result).isFalse();
    }

    @Test
    void hasNoTeamBettingResult() {
        //given
        TeamResult teamResult = new TeamResult();

        //when
        boolean result = teamResult.hasNoTeamBettingResult();

        //then
        assertThat(result).isTrue();
    }

    @Test
    void hasNoTeamBettingResult_결과_있을때() {
        //given
        TeamResult teamResult = new TeamResult();
        teamResult.setTeamBettingResult("결과");

        //when
        boolean result = teamResult.hasNoTeamBettingResult();

        //then
        assertThat(result).isFalse();
    }

    @Test
    void hasNoTeamRacingResult() {
        //given
        TeamResult teamResult = new TeamResult();

        //when
        boolean result = teamResult.hasNoTeamRacingResult();

        //then
        assertThat(result).isTrue();
    }

    @Test
    void hasNoTeamRacingResult_결과_있을때() {
        //given
        TeamResult teamResult = new TeamResult();
        teamResult.setTeamRacingResult("결과");

        //when
        boolean result = teamResult.hasNoTeamRacingResult();

        //then
        assertThat(result).isFalse();
    }
}