package common.kafka_message.alarm;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;

public class AlarmMessageTypeIdResolver extends TypeIdResolverBase {

    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        AlarmMessageType type = AlarmMessageType.fromName(id);
        if (type == null) {
            throw new IllegalArgumentException("Unknown alarmMessageType: " + id);
        }
        return context.constructType(type.getMessageClass());
    }

    @Override
    public String idFromValue(Object value) {
        if (value instanceof AlarmMessage) {
            AlarmMessageType type = ((AlarmMessage) value).getAlarmMessageType();
            return type != null ? type.name() : null;
        }
        return null;
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return idFromValue(value);
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
