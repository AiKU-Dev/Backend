package common.domain.team;

import common.domain.member.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static common.domain.Status.ALIVE;
import static common.domain.Status.DELETE;
import static org.assertj.core.api.Assertions.assertThat;

class TeamTest {

    Member member1;
    Member member2;

    @BeforeEach
    void beforeEach(){
        member1 = new Member("member1");
        member2 = new Member("member2");
    }

    @Test
    void create() {
        //given
        String teamName = "team1";

        //when
        Team team = Team.create(member1, teamName);

        //then
        assertThat(team.getTeamName()).isEqualTo(teamName);
        assertThat(team.getStatus()).isEqualTo(ALIVE);
        assertThat(team.getTeamMembers()).hasSize(1);
    }

    @Test
    void delete() {
        //given
        Team team = Team.create(member1, "team");

        //when
        team.delete();

        //then
        assertThat(team.getStatus()).isEqualTo(DELETE);
    }

    @Test
    void addTeamMember() {
        //given
        String teamName = "team1";
        Team team = Team.create(member1, teamName);

        //when
        team.addTeamMember(member2);

        //then
        assertThat(team.getTeamName()).isEqualTo(teamName);
        assertThat(team.getStatus()).isEqualTo(ALIVE);
        assertThat(team.getTeamMembers()).hasSize(2);

        assertThat(team.getTeamMembers())
                .extracting(TeamMember::getStatus)
                .containsExactly(ALIVE, ALIVE);
    }

    @Test
    void removeTeamMember() {
        //given
        Team team = Team.create(member1, "team");

        team.addTeamMember(member2);
        TeamMember teamMember2 = team.getTeamMembers().get(1);

        //when
        boolean result = team.removeTeamMember(teamMember2);

        //then
        assertThat(result).isTrue();
        assertThat(teamMember2.getStatus()).isEqualTo(DELETE);
    }

    @Test
    void removeTeamMember_유효하지_않은_팀_멤버() {
        //given
        Team team = Team.create(member1, "team");

        Team fakeTeam = Team.create(member1, "team2");
        TeamMember fakeTeamMember = fakeTeam.getTeamMembers().get(0);

        //when
        boolean result = team.removeTeamMember(fakeTeamMember);

        //then
        assertThat(result).isFalse();
    }

    @Test
    void setTeamLateResult() {
        //given
        Team team = Team.create(member1, "team");

        String lateResult = "result";

        //when
        team.setTeamLateResult(lateResult);

        //then
        assertThat(team.getTeamResult().getLateTimeResult()).isEqualTo(lateResult);
    }

    @Test
    void setTeamBettingResult() {
        //given
        Team team = Team.create(member1, "team");

        String bettingResult = "result";

        //when
        team.setTeamBettingResult(bettingResult);

        //then
        assertThat(team.getTeamResult().getTeamBettingResult()).isEqualTo(bettingResult);
    }

    @Test
    void setTeamRacingResult() {
        //given
        Team team = Team.create(member1, "team");

        String racingResult = "result";

        //when
        team.setTeamRacingResult(racingResult);

        //then
        assertThat(team.getTeamResult().getTeamRacingResult()).isEqualTo(racingResult);
    }
}