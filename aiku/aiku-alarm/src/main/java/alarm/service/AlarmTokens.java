package alarm.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AlarmTokens {
    private final List<String> activeTokens;
    private final List<Long> allMemberIds;

    public static AlarmTokens empty() {
        return new AlarmTokens(List.of(), List.of());
    }

}
