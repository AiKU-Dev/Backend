package aiku_main.repository;

import aiku_main.application_event.domain.TeamResultMember;
import aiku_main.dto.MemberProfileResDto;
import aiku_main.dto.TeamEachListResDto;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import common.domain.schedule.QSchedule;
import common.domain.team.Team;
import common.domain.team.TeamMember;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

import static common.domain.ExecStatus.TERM;
import static common.domain.Status.ALIVE;
import static common.domain.member.QMember.member;
import static common.domain.schedule.QSchedule.schedule;
import static common.domain.schedule.QScheduleMember.scheduleMember;
import static common.domain.team.QTeam.team;
import static common.domain.team.QTeamMember.teamMember;

@RequiredArgsConstructor
public class TeamQueryRepositoryCustomImpl implements TeamQueryRepositoryCustom {

    private final JPAQueryFactory query;

    @Override
    public Optional<Team> findTeamWithMember(Long teamId) {
        return query.selectFrom(team)
                .leftJoin(team.teamMembers, teamMember).fetchJoin()
                .where(team.id.eq(teamId),
                        team.status.eq(ALIVE),
                        teamMember.status.eq(ALIVE))
                .stream().findFirst();
    }

    @Override
    public List<TeamMember> findTeamMembersWithMemberInTeam(Long teamId) {
        return query
                .selectFrom(teamMember)
                .join(teamMember.member, member).fetchJoin()
                .where(teamMember.team.id.eq(teamId),
                        teamMember.status.eq(ALIVE))
                .fetch();
    }

    @Override
    public boolean existTeamMember(Long memberId, Long teamId) {
        Long count = query.select(teamMember.count())
                .from(teamMember)
                .where(teamMember.member.id.eq(memberId),
                        teamMember.team.id.eq(teamId),
                        teamMember.status.eq(ALIVE))
                .fetchFirst();

        return count != null && count > 0;
    }

    @Override
    public Long countOfAliveTeamMember(Long teamId) {
        return query
                .select(teamMember.count())
                .from(teamMember)
                .where(teamMember.team.id.eq(teamId),
                        teamMember.status.eq(ALIVE))
                .fetchOne();
    }

    @Override
    public Optional<TeamMember> findAliveTeamMember(Long teamId, Long memberId) {
        TeamMember result = query.selectFrom(teamMember)
                .where(teamMember.team.id.eq(teamId),
                        teamMember.member.id.eq(memberId),
                        teamMember.status.eq(ALIVE))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<TeamMember> findTeamMember(Long teamId, Long memberId) {
        TeamMember result = query.selectFrom(teamMember)
                .where(teamMember.team.id.eq(teamId),
                        teamMember.member.id.eq(memberId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public List<TeamEachListResDto> getTeamList(Long memberId, int page) {
        QSchedule subSchedule = new QSchedule("subSchedule");

        List<Long> teamIdList = query.select(teamMember.team.id)
                .from(teamMember)
                .where(teamMember.member.id.eq(memberId))
                .offset(getOffset(page))
                .limit(11)
                .fetch();

        return query.select(Projections.constructor(TeamEachListResDto.class,
                        team.id, team.teamName, teamMember.count().intValue(),
                        schedule.scheduleTime))
                .from(team)
                .leftJoin(schedule).on(
                        schedule.team.id.eq(team.id),
                        schedule.team.id.in(teamIdList),
                        schedule.scheduleTime.eq(
                                JPAExpressions.select(subSchedule.scheduleTime.max())
                                        .from(subSchedule)
                                        .where(subSchedule.team.id.eq(team.id),
                                                subSchedule.scheduleStatus.eq(TERM),
                                                subSchedule.status.eq(ALIVE))))
                .leftJoin(teamMember).on(
                        teamMember.team.id.eq(team.id),
                        teamMember.status.eq(ALIVE),
                        teamMember.team.id.in(teamIdList))
                .where(team.id.in(teamIdList),
                        team.status.eq(ALIVE))
                .groupBy(team.id, team.teamName, schedule.scheduleTime)
                .orderBy(schedule.scheduleTime.desc())
                .fetch();
    }

    @Override
    public List<TeamResultMember> getTeamLateTimeResult(Long teamId) {
        return query
                .select(Projections.constructor(TeamResultMember.class,
                        member.id, member.nickname,
                        Projections.constructor(MemberProfileResDto.class,
                                member.profile.profileType, member.profile.profileImg, member.profile.profileCharacter, member.profile.profileBackground),
                        scheduleMember.arrivalTimeDiff.sum(), teamMember.status))
                .from(teamMember)
                .innerJoin(member).on(member.id.eq(teamMember.member.id))
                .leftJoin(scheduleMember).on(scheduleMember.member.id.eq(teamMember.member.id))
                .where(scheduleMember.arrivalTime.isNotNull(),
                        scheduleMember.arrivalTimeDiff.lt(0),
                        scheduleMember.status.eq(ALIVE))
                .groupBy(member.id, member.nickname, member.profile.profileType, member.profile.profileImg, member.profile.profileCharacter, member.profile.profileBackground, teamMember.status)
                .orderBy(scheduleMember.arrivalTimeDiff.sum().asc())
                .fetch();
    }

    private int getOffset(int page){
        return (page - 1) * 10;
    }
}