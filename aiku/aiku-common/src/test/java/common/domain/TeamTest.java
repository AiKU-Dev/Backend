package common.domain;

import common.domain.member.Member;
import common.domain.team.Team;
import common.domain.team.TeamMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static common.domain.Status.DELETE;
import static org.assertj.core.api.Assertions.as;
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
        assertThat(team.getStatus()).isEqualTo(Status.ALIVE);
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
        assertThat(team.getStatus()).isEqualTo(Status.ALIVE);
        assertThat(team.getTeamMembers()).hasSize(2);
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
}