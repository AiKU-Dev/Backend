package map.application_event.handler;

import lombok.RequiredArgsConstructor;
import map.application_event.event.AskRacingEvent;
import map.application_event.event.MemberArrivalEvent;
import map.application_event.event.RacingStatusNotChangedEvent;
import map.application_event.event.ScheduleCloseEvent;
import map.scheduler.RacingScheduler;
import map.service.MapService;
import map.service.RacingService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class ScheduleEventHandler {

    private final RacingService racingService;
    private final MapService mapService;

    @EventListener
    public void handleMemberArrivalEvent(MemberArrivalEvent event){
        racingService.makeMemberWinnerInRacing(event.getMemberId(), event.getScheduleId(), event.getScheduleName());
    }

    @EventListener
    public void handleScheduleCloseEvent(ScheduleCloseEvent event){
        racingService.terminateRunningRacing(event.getScheduleId());
        mapService.deleteAllLocationsInSchedule(event.getScheduleId());
    }
}
