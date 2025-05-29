package common.domain.schedule;

import common.domain.*;
import common.domain.member.Member;
import common.domain.value_reference.TeamValue;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static common.domain.ExecStatus.*;
import static common.domain.Status.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Schedule extends BaseTime {

    @Column(name = "scheduleId")
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teamId")
    @Embedded
    private TeamValue team;

    private String scheduleName;
    private LocalDateTime scheduleTime;

    @Embedded
    private Location location;

    private LocalDateTime scheduleTermTime;

    @Enumerated(value = EnumType.STRING)
    private ExecStatus scheduleStatus = WAIT;

    @Enumerated(value = EnumType.STRING)
    private Status status = ALIVE;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL)
    private List<ScheduleMember> scheduleMembers = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private ScheduleResult scheduleResult;

    protected Schedule(TeamValue team, String scheduleName, LocalDateTime scheduleTime, Location location) {
        this.team = team;
        this.scheduleName = scheduleName;
        this.scheduleTime = scheduleTime;
        this.location = location;
    }

    public static Schedule create(Member member, TeamValue team, String scheduleName, LocalDateTime scheduleTime, Location location, int pointAmount) {
        Schedule schedule = new Schedule(team, scheduleName, scheduleTime, location);

        schedule.addScheduleMember(member, true, pointAmount);
        return schedule;
    }

    public void update(String scheduleName, LocalDateTime scheduleTime, Location location){
        this.scheduleName = scheduleName;
        this.scheduleTime = scheduleTime;
        this.location = location;
    }

    public void delete(){
        this.status = DELETE;
    }

    public void error(){
        this.status = ERROR;
    }

    public void addScheduleMember(Member member, boolean isOwner, int pointAmount) {
        ScheduleMember scheduleMember = new ScheduleMember(member, this, isOwner, pointAmount);
        this.scheduleMembers.add(scheduleMember);
    }

    public boolean removeScheduleMember(ScheduleMember scheduleMember) {
        if(isScheduleMember(scheduleMember)){
            scheduleMember.setStatus(DELETE);
            return true;
        }

        return false;
    }

    public void changeScheduleOwner(ScheduleMember nextOwner){
        if(isScheduleMember(nextOwner)){
            nextOwner.setOwner();
        }
    }

    public boolean arriveScheduleMember(ScheduleMember scheduleMember, LocalDateTime arrivalTime){
        if(isScheduleMember(scheduleMember)){
            scheduleMember.arrive(arrivalTime, (int) Duration.between(arrivalTime, this.scheduleTime).toMinutes());
            return true;
        }
        return false;
    }

    public boolean rewardMember(ScheduleMember scheduleMember, int rewardPointAmount){
        if(isScheduleMember(scheduleMember)) {
            scheduleMember.setRewardPointAmount(rewardPointAmount);
            return true;
        }
        return false;
    }

    public void close(LocalDateTime scheduleCloseTime, Map<Long, LocalDateTime> scheMemArrivalTime){
        scheduleMembers.forEach(scheduleMember -> {
            LocalDateTime arrivalTime = scheMemArrivalTime.get(scheduleMember.getId());
            scheduleMember.arrive(
                    arrivalTime,
                    getArrivalTimeDiff(arrivalTime)
            );
        });

        setTerm(scheduleCloseTime);
    }

    private int getArrivalTimeDiff(LocalDateTime arrivalTime){
        return (int) Duration.between(arrivalTime, this.scheduleTime).toMinutes();
    }

    public void setTerm(LocalDateTime scheduleTermTime){
        this.scheduleTermTime = scheduleTermTime;
        this.scheduleStatus = TERM;
    }

    public void setRun(){
        scheduleStatus = RUN;
    }

    public void setScheduleArrivalResult(String scheduleArrivalResult) {
        checkScheduleResultExist();
        scheduleResult.setScheduleArrivalResult(scheduleArrivalResult);
    }

    public void setScheduleBettingResult(String scheduleBettingResult) {
        checkScheduleResultExist();
        scheduleResult.setScheduleBettingResult(scheduleBettingResult);
    }

    public void setScheduleRacingResult(String scheduleRacingResult) {
        checkScheduleResultExist();
        scheduleResult.setScheduleRacingResult(scheduleRacingResult);
    }

    private void checkScheduleResultExist(){
        if(scheduleResult == null){
            scheduleResult = new ScheduleResult(this);
        }
    }

    private boolean isScheduleMember(ScheduleMember scheduleMember){
        return scheduleMember.getSchedule() == this;
    }

    public List<ScheduleMember> getScheduleMembers() {
        return List.copyOf(scheduleMembers);
    }

    public boolean isTerm() {
        return scheduleStatus == TERM;
    }

    public boolean isWait() {
        return scheduleStatus == WAIT;
    }
}
