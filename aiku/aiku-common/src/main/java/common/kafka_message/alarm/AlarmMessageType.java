package common.kafka_message.alarm;

public enum AlarmMessageType {
    SCHEDULE_ADD(ScheduleAlarmMessage.class),
    SCHEDULE_ENTER(ScheduleMemberAlarmMessage.class),
    SCHEDULE_EXIT(ScheduleMemberAlarmMessage.class),
    SCHEDULE_UPDATE(ScheduleAlarmMessage.class),
    SCHEDULE_OWNER(ScheduleAlarmMessage.class),
    SCHEDULE_OPEN(ScheduleAlarmMessage.class),
    SCHEDULE_AUTO_CLOSE(ScheduleAlarmMessage.class),

    MEMBER_ARRIVAL(ArrivalAlarmMessage.class),
    SCHEDULE_MAP_CLOSE(ScheduleClosedMessage.class),
    EMOJI(EmojiMessage.class),
    ASK_RACING(AskRacingMessage.class),
    RACING_AUTO_DELETED(RacingAutoDeletedMessage.class),
    RACING_DENIED(RacingDeniedMessage.class),
    RACING_TERM(RacingTermMessage.class),
    RACING_START(RacingStartMessage.class),
    TITLE_GRANTED(TitleGrantedMessage.class),
    PAYMENT_SUCCESS(PaymentSuccessMessage.class),
    PAYMENT_FAILED(PaymentFailedMessage.class),
    POINT_ERROR(PointErrorMessage.class);

    private final Class<? extends AlarmMessage> messageClass;

    AlarmMessageType(Class<? extends AlarmMessage> messageClass) {
        this.messageClass = messageClass;
    }

    public Class<? extends AlarmMessage> getMessageClass() {
        return messageClass;
    }

    public static AlarmMessageType fromName(String name) {
        try {
            return AlarmMessageType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
