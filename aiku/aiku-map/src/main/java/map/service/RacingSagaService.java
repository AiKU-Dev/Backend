package map.service;

import common.domain.Racing;
import common.exception.JsonParseException;
import common.kafka_message.PointChangeReason;
import common.kafka_message.PointChangedType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import map.exception.RacingException;
import map.repository.RacingQueryRepository;
import org.apache.kafka.common.KafkaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static common.response.status.BaseErrorCode.NO_SUCH_RACING;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class RacingSagaService {

    private final RacingQueryRepository racingQueryRepository;

    /*
    무승부, 승자, 성사
    (ExecStatus, Winner)
    (TERM, null) : 무승부, 양 참가자 금액 보상 오류
    (TERM, O) : 승자, 승리자 금액 보상 오류
    (RUN, null) : 레이싱 성사 금액 차감 오류
     */
    @Transactional
    public void rollbackRacing(Long memberId, PointChangedType pointChangedType, Integer pointAmount, PointChangeReason reason, Long racingId){
        Racing racing = racingQueryRepository.findById(racingId)
                .orElseThrow(() -> new RacingException(NO_SUCH_RACING));
        racing.specifyError();
    }
}
