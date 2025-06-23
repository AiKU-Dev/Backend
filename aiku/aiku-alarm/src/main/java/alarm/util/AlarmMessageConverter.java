package alarm.util;

import common.kafka_message.alarm.*;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

// DB 알림 저장 용
@NoArgsConstructor
@Component
public class AlarmMessageConverter implements AlarmMessageVisitor {

    @Override
    public String visit(ArrivalAlarmMessage message) {
        return "약속 : " + message.getScheduleName() + "에서 멤버 " + message.getArriveMemberInfo().getNickname() + "가 약속 장소에 도착하였습니다!";
    }

    @Override
    public String visit(AskRacingMessage message) {
        return "약속 : " + message.getScheduleName() + "에서 멤버 " + message.getFirstRacerInfo().getNickname() + "가 " + message.getPoint() + "아쿠의 레이싱을 신청하였습니다.";
    }

    @Override
    public String visit(EmojiMessage message) {
        return "약속 : " + message.getScheduleName() + "에서 멤버 " + message.getSenderInfo().getNickname() + "가 " + message.getEmojiType() + " 이모지를 전달했습니다.";
    }

    @Override
    public String visit(LocationAlarmMessage message) {
        return "위치 알림이 도착했습니다.";
    }

    @Override
    public String visit(PaymentFailedMessage message) {
        return "결제 : " + message.getPrice() + "원의 결제가 실패하였습니다.";
    }

    @Override
    public String visit(PaymentSuccessMessage message) {
        return "결제 : " + message.getPoint() + "원이 충전되었습니다.";
    }

    @Override
    public String visit(PointErrorMessage message) {
        return "오류가 발생하였습니다. 마이페이지 1대1 문의에 해당 내용을 남겨주세요.";
    }

    @Override
    public String visit(RacingAutoDeletedMessage message) {
        return "약속 : " + message.getScheduleName() + "에서 멤버 " + message.getSecondRacerInfo().getNickname() +
                "에게 신청한 레이싱이 거절되었습니다.";
    }

    @Override
    public String visit(RacingDeniedMessage message) {
        return "약속 : " + message.getScheduleName() + "에서 멤버 " + message.getSecondRacerInfo().getNickname() +
                "에게 신청한 레이싱이 거절되었습니다.";
    }

    @Override
    public String visit(RacingStartMessage message) {
        return "약속 : " + message.getScheduleName() + "에서 멤버 " + message.getFirstRacerInfo().getNickname() + "와 " + message.getSecondRacerInfo().getNickname()
                + "의 레이싱이 시작되었습니다.";
    }

    @Override
    public String visit(RacingTermMessage message) {
        return "약속 : " + message.getScheduleName() + "에서 멤버 " + message.getWinnerRacerInfo().getNickname() + "와 " + message.getLoserRacerInfo().getNickname()
                + "의 레이싱이 "+ message.getWinnerRacerInfo().getNickname() + "의 승리로 종료되었습니다.";
    }

    @Override
    public String visit(ScheduleClosedMessage message) {
        return "약속 : " + message.getScheduleName() + "가 종료되었습니다.";
    }

    @Override
    public String visit(ScheduleAlarmMessage message) {
        AlarmMessageType alarmMessageType = message.getAlarmMessageType();

        if (alarmMessageType.equals(AlarmMessageType.SCHEDULE_ADD))
            return "약속 : " + message.getScheduleName() + " 가 추가되었습니다.";
        else if (alarmMessageType.equals(AlarmMessageType.SCHEDULE_UPDATE))
            return "약속 : " + message.getScheduleName() + " 이 업데이트 되었습니다.";
        else if (alarmMessageType.equals(AlarmMessageType.SCHEDULE_OWNER))
            return "약속 : " + message.getScheduleName() + " 의 스케줄 장이 변경되었습니다.";
        else if (alarmMessageType.equals(AlarmMessageType.SCHEDULE_OPEN))
            return "약속 : " + message.getScheduleName() + " 맵이 생성되었습니다!";
        else if (alarmMessageType.equals(AlarmMessageType.SCHEDULE_AUTO_CLOSE))
            return "약속 : " + message.getScheduleName() + " 이 자동 종료되었습니다.";

        return "";
    }

    @Override
    public String visit(ScheduleMemberAlarmMessage message) {
        AlarmMessageType alarmMessageType = message.getAlarmMessageType();

        if (alarmMessageType.equals(AlarmMessageType.SCHEDULE_ENTER))
            return "약속 : " + message.getScheduleName() + " 에 멤버가 입장하였습니다.";
        else if (alarmMessageType.equals(AlarmMessageType.SCHEDULE_EXIT))
            return "약속 : " + message.getScheduleName() + " 에서 퇴장하였습니다.";

        return "";
    }

    @Override
    public String visit(TitleGrantedMessage message) {
        return "칭호 : " + message.getTitleName() + "을 획득하였습니다!";
    }
}
