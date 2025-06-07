package common.kafka_message.alarm;

// AlarmMessage를 상속하는 클래스를 방문하는 인터페이스
public interface AlarmMessageVisitor {

    String visit(ArrivalAlarmMessage message);
    String visit(AskRacingMessage message);
    String visit(EmojiMessage message);
    String visit(LocationAlarmMessage message);

    String visit(PaymentFailedMessage message);
    String visit(PaymentSuccessMessage message);
    String visit(PointErrorMessage message);
    String visit(RacingAutoDeletedMessage message);
    String visit(RacingDeniedMessage message);
    String visit(RacingStartMessage message);
    String visit(RacingTermMessage message);
    String visit(ScheduleClosedMessage message);
    String visit(ScheduleAlarmMessage message);
    String visit(ScheduleMemberAlarmMessage message);
    String visit(TitleGrantedMessage message);

}
