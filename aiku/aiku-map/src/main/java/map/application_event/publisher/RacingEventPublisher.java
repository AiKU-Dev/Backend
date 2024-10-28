package map.application_event.publisher;

import lombok.RequiredArgsConstructor;
import map.application_event.domain.RacingInfo;
import map.application_event.event.AskRacingEvent;
import map.application_event.event.RacingStatusNotChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RacingEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publishAskRacingEvent(RacingInfo racingInfo){
        AskRacingEvent event = new AskRacingEvent(racingInfo);
        publisher.publishEvent(event);
    }

    public void publishRacingStatusNotChangedEvent(RacingInfo racingInfo){
        RacingStatusNotChangedEvent event = new RacingStatusNotChangedEvent(racingInfo);
        publisher.publishEvent(event);
    }

}