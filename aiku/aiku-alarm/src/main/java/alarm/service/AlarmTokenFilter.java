package alarm.service;

import alarm.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class AlarmTokenFilter {

    private final MemberRepository memberRepository;

    public AlarmTokens filterAndValidate(List<String> originalTokens) {
        if (originalTokens == null || originalTokens.isEmpty()) {
            log.warn("Empty token list provided");
            return AlarmTokens.empty();
        }

        // 알람이 켜진 토큰만 필터링
        List<String> activeTokens = memberRepository.findFirebaseTokenOnlyAlarmOn(originalTokens);
        
        // 모든 토큰에 해당하는 멤버 ID 조회
        List<Long> allMemberIds = memberRepository.findMemberIdsByFirebaseTokenList(originalTokens);

        return new AlarmTokens(activeTokens, allMemberIds);
    }
}

