package common.kafka_message;

public enum PointChangeReason {
    SCHEDULE_ENTER, SCHEDULE_EXIT, SCHEDULE_REWARD,
    BETTING, BETTING_CANCLE, BETTING_REWARD,
    RACING, RACING_CANCLE, RACING_REWARD,
    SHOP, EVENT, SHOP_CANCLE, EVENT_CANCLE;
}