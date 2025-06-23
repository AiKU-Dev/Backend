package aiku_main.service.title;

import aiku_main.exception.TitleException;
import aiku_main.kafka.KafkaProducerService;
import aiku_main.repository.title.TitleRepository;
import common.domain.member.Member;
import common.domain.title.Title;
import common.domain.title.TitleCode;
import common.kafka_message.alarm.AlarmMessageType;
import common.kafka_message.alarm.TitleGrantedMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import static common.domain.title.TitleCode.*;
import static common.kafka_message.KafkaTopic.ALARM;

@ExtendWith(MockitoExtension.class)
public class TitleServiceTest {

    @Mock
    TitleRepository titleRepository;
    @Mock
    KafkaProducerService kafkaProducerService;
    @InjectMocks
    TitleService titleService;

    private final Long SCHEDULE_ID = 1L;
    private final Long MEMBER_ID_1 = 1L;
    private final Long MEMBER_ID_2 = 2L;
    private final Long TITLE_ID = 10L;
    private final String TITLE_NAME = "Test Title";
    private final String TITLE_DESCRIPTION = "Test Description";
    private final String FIREBASE_TOKEN = "test-firebase-token";

    @Test
    void getMemberIdsInSchedule_정상() {
        // Given
        List<Long> expectedMemberIds = List.of(MEMBER_ID_1, MEMBER_ID_2);
        given(titleRepository.findMemberIdsInSchedule(SCHEDULE_ID)).willReturn(expectedMemberIds);

        // When
        List<Long> result = titleService.getMemberIdsInSchedule(SCHEDULE_ID);

        // Then
        assertThat(result).isEqualTo(expectedMemberIds);
        then(titleRepository).should().findMemberIdsInSchedule(SCHEDULE_ID);
    }

    @Test
    void checkAndGivePointsMoreThan10kTitle_신규타이틀부여_정상() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1, MEMBER_ID_2);
        Member member1 = mock(Member.class);
        Member member2 = mock(Member.class);
        List<Member> members = List.of(member1, member2);
        Title title = mock(Title.class);
        
        given(member1.getId()).willReturn(MEMBER_ID_1);
        given(member1.getFirebaseToken()).willReturn(FIREBASE_TOKEN);
        given(member2.getId()).willReturn(MEMBER_ID_2);
        given(member2.getFirebaseToken()).willReturn(FIREBASE_TOKEN);
        given(title.getId()).willReturn(TITLE_ID);
        given(title.getTitleName()).willReturn(TITLE_NAME);
        given(title.getTitleDescription()).willReturn(TITLE_DESCRIPTION);
        given(title.getTitleCode()).willReturn(POINTS_MORE_THAN_10K);
        
        given(titleRepository.find10kPointsMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(POINTS_MORE_THAN_10K)).willReturn(Optional.of(title));
        given(titleRepository.existTitleMember(MEMBER_ID_1, TITLE_ID)).willReturn(false);
        given(titleRepository.existTitleMember(MEMBER_ID_2, TITLE_ID)).willReturn(false);

        // When
        titleService.checkAndGivePointsMoreThan10kTitle(memberIds);

        // Then
        then(titleRepository).should().find10kPointsMembersByMemberIds(memberIds);
        then(titleRepository).should().findByTitleCode(POINTS_MORE_THAN_10K);
        then(titleRepository).should().existTitleMember(MEMBER_ID_1, TITLE_ID);
        then(titleRepository).should().existTitleMember(MEMBER_ID_2, TITLE_ID);
        then(title).should().giveTitleToMember(member1);
        then(title).should().giveTitleToMember(member2);
        then(kafkaProducerService).should(times(2)).sendMessage(eq(ALARM), any(TitleGrantedMessage.class));
    }

    @Test
    void checkAndGivePointsMoreThan10kTitle_이미보유한타이틀_중복부여안함() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        Member member = mock(Member.class);
        List<Member> members = List.of(member);
        Title title = mock(Title.class);
        
        given(member.getId()).willReturn(MEMBER_ID_1);
        given(title.getId()).willReturn(TITLE_ID);
        
        given(titleRepository.find10kPointsMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(POINTS_MORE_THAN_10K)).willReturn(Optional.of(title));
        given(titleRepository.existTitleMember(MEMBER_ID_1, TITLE_ID)).willReturn(true);

        // When
        titleService.checkAndGivePointsMoreThan10kTitle(memberIds);

        // Then
        then(title).should(never()).giveTitleToMember(any(Member.class));
        then(kafkaProducerService).should(never()).sendMessage(any(), any());
    }

    @Test
    void checkAndGivePointsMoreThan10kTitle_조건맞는멤버없음_정상() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        List<Member> emptyMembers = List.of();
        Title title = mock(Title.class);
        
        given(titleRepository.find10kPointsMembersByMemberIds(memberIds)).willReturn(emptyMembers);
        given(titleRepository.findByTitleCode(POINTS_MORE_THAN_10K)).willReturn(Optional.of(title));

        // When
        titleService.checkAndGivePointsMoreThan10kTitle(memberIds);

        // Then
        then(title).should(never()).giveTitleToMember(any(Member.class));
        then(kafkaProducerService).should(never()).sendMessage(any(), any());
    }

    @Test
    void checkAndGivePointsMoreThan10kTitle_타이틀없음_예외() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        List<Member> members = List.of(mock(Member.class));
        
        given(titleRepository.find10kPointsMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(POINTS_MORE_THAN_10K)).willReturn(Optional.empty());

        // When & Then
        assertThrows(TitleException.class, () -> titleService.checkAndGivePointsMoreThan10kTitle(memberIds));
    }

    @Test
    void checkAndGiveEarlyArrival10TimesTitle_신규타이틀부여_정상() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        Member member = mock(Member.class);
        List<Member> members = List.of(member);
        Title title = mock(Title.class);
        
        given(member.getId()).willReturn(MEMBER_ID_1);
        given(member.getFirebaseToken()).willReturn(FIREBASE_TOKEN);
        given(title.getId()).willReturn(TITLE_ID);
        given(title.getTitleName()).willReturn(TITLE_NAME);
        given(title.getTitleDescription()).willReturn(TITLE_DESCRIPTION);
        given(title.getTitleCode()).willReturn(EARLY_ARRIVAL_10_TIMES);
        
        given(titleRepository.findEarlyArrival10TimesMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(EARLY_ARRIVAL_10_TIMES)).willReturn(Optional.of(title));
        given(titleRepository.existTitleMember(MEMBER_ID_1, TITLE_ID)).willReturn(false);

        // When
        titleService.checkAndGiveEarlyArrival10TimesTitle(memberIds);

        // Then
        then(titleRepository).should().findEarlyArrival10TimesMembersByMemberIds(memberIds);
        then(titleRepository).should().findByTitleCode(EARLY_ARRIVAL_10_TIMES);
        then(title).should().giveTitleToMember(member);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), any(TitleGrantedMessage.class));
    }

    @Test
    void checkAndGiveEarlyArrival10TimesTitle_타이틀없음_예외() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        List<Member> members = List.of(mock(Member.class));
        
        given(titleRepository.findEarlyArrival10TimesMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(EARLY_ARRIVAL_10_TIMES)).willReturn(Optional.empty());

        // When & Then
        assertThrows(TitleException.class, () -> titleService.checkAndGiveEarlyArrival10TimesTitle(memberIds));
    }

    @Test
    void checkAndGiveLateArrival5TimesTitle_신규타이틀부여_정상() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        Member member = mock(Member.class);
        List<Member> members = List.of(member);
        Title title = mock(Title.class);
        
        given(member.getId()).willReturn(MEMBER_ID_1);
        given(member.getFirebaseToken()).willReturn(FIREBASE_TOKEN);
        given(title.getId()).willReturn(TITLE_ID);
        given(title.getTitleName()).willReturn(TITLE_NAME);
        given(title.getTitleDescription()).willReturn(TITLE_DESCRIPTION);
        given(title.getTitleCode()).willReturn(LATE_ARRIVAL_5_TIMES);
        
        given(titleRepository.findLateArrival5TimesMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(LATE_ARRIVAL_5_TIMES)).willReturn(Optional.of(title));
        given(titleRepository.existTitleMember(MEMBER_ID_1, TITLE_ID)).willReturn(false);

        // When
        titleService.checkAndGiveLateArrival5TimesTitle(memberIds);

        // Then
        then(titleRepository).should().findLateArrival5TimesMembersByMemberIds(memberIds);
        then(titleRepository).should().findByTitleCode(LATE_ARRIVAL_5_TIMES);
        then(title).should().giveTitleToMember(member);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), any(TitleGrantedMessage.class));
    }

    @Test
    void checkAndGiveLateArrival5TimesTitle_타이틀없음_예외() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        List<Member> members = List.of(mock(Member.class));
        
        given(titleRepository.findLateArrival5TimesMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(LATE_ARRIVAL_5_TIMES)).willReturn(Optional.empty());

        // When & Then
        assertThrows(TitleException.class, () -> titleService.checkAndGiveLateArrival5TimesTitle(memberIds));
    }

    @Test
    void checkAndGiveLateArrival10TimesTitle_신규타이틀부여_정상() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        Member member = mock(Member.class);
        List<Member> members = List.of(member);
        Title title = mock(Title.class);
        
        given(member.getId()).willReturn(MEMBER_ID_1);
        given(member.getFirebaseToken()).willReturn(FIREBASE_TOKEN);
        given(title.getId()).willReturn(TITLE_ID);
        given(title.getTitleName()).willReturn(TITLE_NAME);
        given(title.getTitleDescription()).willReturn(TITLE_DESCRIPTION);
        given(title.getTitleCode()).willReturn(LATE_ARRIVAL_10_TIMES);
        
        given(titleRepository.findLateArrival10TimesMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(LATE_ARRIVAL_10_TIMES)).willReturn(Optional.of(title));
        given(titleRepository.existTitleMember(MEMBER_ID_1, TITLE_ID)).willReturn(false);

        // When
        titleService.checkAndGiveLateArrival10TimesTitle(memberIds);

        // Then
        then(titleRepository).should().findLateArrival10TimesMembersByMemberIds(memberIds);
        then(titleRepository).should().findByTitleCode(LATE_ARRIVAL_10_TIMES);
        then(title).should().giveTitleToMember(member);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), any(TitleGrantedMessage.class));
    }

    @Test
    void checkAndGiveLateArrival10TimesTitle_타이틀없음_예외() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        List<Member> members = List.of(mock(Member.class));
        
        given(titleRepository.findLateArrival10TimesMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(LATE_ARRIVAL_10_TIMES)).willReturn(Optional.empty());

        // When & Then
        assertThrows(TitleException.class, () -> titleService.checkAndGiveLateArrival10TimesTitle(memberIds));
    }

    @Test
    void checkAndGiveBettingWinning5TimesTitle_신규타이틀부여_정상() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        Member member = mock(Member.class);
        List<Member> members = List.of(member);
        Title title = mock(Title.class);
        
        given(member.getId()).willReturn(MEMBER_ID_1);
        given(member.getFirebaseToken()).willReturn(FIREBASE_TOKEN);
        given(title.getId()).willReturn(TITLE_ID);
        given(title.getTitleName()).willReturn(TITLE_NAME);
        given(title.getTitleDescription()).willReturn(TITLE_DESCRIPTION);
        given(title.getTitleCode()).willReturn(BETTING_WINNING_5_TIMES);
        
        given(titleRepository.findBettingWinning5TimesMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(BETTING_WINNING_5_TIMES)).willReturn(Optional.of(title));
        given(titleRepository.existTitleMember(MEMBER_ID_1, TITLE_ID)).willReturn(false);

        // When
        titleService.checkAndGiveBettingWinning5TimesTitle(memberIds);

        // Then
        then(titleRepository).should().findBettingWinning5TimesMembersByMemberIds(memberIds);
        then(titleRepository).should().findByTitleCode(BETTING_WINNING_5_TIMES);
        then(title).should().giveTitleToMember(member);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), any(TitleGrantedMessage.class));
    }

    @Test
    void checkAndGiveBettingWinning5TimesTitle_타이틀없음_예외() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        List<Member> members = List.of(mock(Member.class));
        
        given(titleRepository.findBettingWinning5TimesMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(BETTING_WINNING_5_TIMES)).willReturn(Optional.empty());

        // When & Then
        assertThrows(TitleException.class, () -> titleService.checkAndGiveBettingWinning5TimesTitle(memberIds));
    }

    @Test
    void checkAndGiveBettingLosing10TimesTitle_신규타이틀부여_정상() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        Member member = mock(Member.class);
        List<Member> members = List.of(member);
        Title title = mock(Title.class);
        
        given(member.getId()).willReturn(MEMBER_ID_1);
        given(member.getFirebaseToken()).willReturn(FIREBASE_TOKEN);
        given(title.getId()).willReturn(TITLE_ID);
        given(title.getTitleName()).willReturn(TITLE_NAME);
        given(title.getTitleDescription()).willReturn(TITLE_DESCRIPTION);
        given(title.getTitleCode()).willReturn(BETTING_LOSING_10_TIMES);
        
        given(titleRepository.findBettingLosing10TimesMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(BETTING_LOSING_10_TIMES)).willReturn(Optional.of(title));
        given(titleRepository.existTitleMember(MEMBER_ID_1, TITLE_ID)).willReturn(false);

        // When
        titleService.checkAndGiveBettingLosing10TimesTitle(memberIds);

        // Then
        then(titleRepository).should().findBettingLosing10TimesMembersByMemberIds(memberIds);
        then(titleRepository).should().findByTitleCode(BETTING_LOSING_10_TIMES);
        then(title).should().giveTitleToMember(member);
        then(kafkaProducerService).should().sendMessage(eq(ALARM), any(TitleGrantedMessage.class));
    }

    @Test
    void checkAndGiveBettingLosing10TimesTitle_타이틀없음_예외() {
        // Given
        List<Long> memberIds = List.of(MEMBER_ID_1);
        List<Member> members = List.of(mock(Member.class));
        
        given(titleRepository.findBettingLosing10TimesMembersByMemberIds(memberIds)).willReturn(members);
        given(titleRepository.findByTitleCode(BETTING_LOSING_10_TIMES)).willReturn(Optional.empty());

        // When & Then
        assertThrows(TitleException.class, () -> titleService.checkAndGiveBettingLosing10TimesTitle(memberIds));
    }
}
