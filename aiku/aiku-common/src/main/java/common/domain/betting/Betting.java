package common.domain.betting;

import common.domain.BaseTime;
import common.domain.ExecStatus;
import common.domain.Status;
import common.domain.value_reference.ScheduleMemberValue;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static common.domain.ExecStatus.TERM;
import static common.domain.ExecStatus.WAIT;
import static common.domain.Status.ALIVE;
import static common.domain.Status.DELETE;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Betting extends BaseTime {

    @Column(name = "bettingId")
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @AttributeOverride(name = "id", column = @Column(name = "bettorScheduleMemberId"))
    @Embedded
    private ScheduleMemberValue bettor;

    @AttributeOverride(name = "id", column = @Column(name = "beteeScheduleMemberId"))
    @Embedded
    private ScheduleMemberValue betee;

    private boolean isWinner = false;

    @Enumerated(value = EnumType.STRING)
    private ExecStatus bettingStatus = WAIT;

    private int pointAmount;
    private int rewardPointAmount;

    @Enumerated(value = EnumType.STRING)
    private Status status = ALIVE;

    public static Betting create(ScheduleMemberValue bettor, ScheduleMemberValue betee, int pointAmount){
        Betting betting = new Betting();
        betting.bettor = bettor;
        betting.betee = betee;
        betting.pointAmount = pointAmount;
        return betting;
    }

    public void setWin(int rewardPointAmount) {
        this.isWinner = true;
        this.rewardPointAmount = rewardPointAmount;
        this.bettingStatus = TERM;
    }

    public void setDraw(){
        isWinner = false;
        rewardPointAmount = pointAmount;
        this.bettingStatus = TERM;
    }

    public void setLose(){
        isWinner = false;
        rewardPointAmount = 0;
        this.bettingStatus = TERM;
    }

    public void delete(){
        this.status = DELETE;
    }
}
