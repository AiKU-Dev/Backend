package aiku_main.application_event.handler;

import aiku_main.application_event.event.ScheduleOpenEvent;
import aiku_main.service.schedule.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ScheduleOpenEventHandler {

    private final ScheduleService scheduleService;

    @EventListener
    public void openSchedule(ScheduleOpenEvent event){
        scheduleService.openSchedule(event.getScheduleId());
    }
}
