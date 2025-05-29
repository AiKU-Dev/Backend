package common.domain;

import common.domain.member.Member;
import common.domain.team.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}