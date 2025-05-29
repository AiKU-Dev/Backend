package common.domain.team;

import common.domain.BaseTime;
import common.domain.Status;
import common.domain.member.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static common.domain.Status.ALIVE;
import static common.domain.Status.DELETE;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Team extends BaseTime {

    @Column(name = "teamId")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;
    private String teamName;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TeamMember> teamMembers = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private TeamResult teamResult;

    @Enumerated(EnumType.STRING)
    private Status status = ALIVE;

    public static Team create(Member member, String teamName){
        Team team = new Team();
        team.teamName = teamName;
        team.addTeamMember(member);

        return team;
    }

    public void delete(){
        status = DELETE;
    }

    public void addTeamMember(Member member){
        teamMembers.add(new TeamMember(this, member));
    }

    public boolean removeTeamMember(TeamMember teamMember){
        if (isValidTeamMember(teamMember)) {
            teamMember.setStatus(DELETE);
            return true;
        }

        return false;
    }

    public void setTeamLateResult(String teamLateResult){
        getOrCreateTeamResult();
        teamResult.setLateTimeResult(teamLateResult);
    }

    public void setTeamBettingResult(String teamBettingResult){
        getOrCreateTeamResult();
        teamResult.setTeamBettingResult(teamBettingResult);
    }

    public void setTeamRacingResult(String teamRacingResult){
        getOrCreateTeamResult();
        teamResult.setTeamRacingResult(teamRacingResult);
    }

    private void getOrCreateTeamResult(){
        if (teamResult == null) {
            teamResult = new TeamResult(this);
        }
    }

    private boolean isValidTeamMember(TeamMember teamMember){
        return teamMember.getTeam() == this;
    }

    public List<TeamMember> getTeamMembers() {
        return List.copyOf(teamMembers);
    }
}
