package aiku_main.application_event.publisher;

import aiku_main.application_event.event.ScheduleExitEvent;
import common.domain.Schedule;
import common.domain.member.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ScheduleEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publishScheduleExitEvent(Member member, Schedule schedule){
        ScheduleExitEvent event = new ScheduleExitEvent(member, schedule);
        publisher.publishEvent(event);
    }
}
