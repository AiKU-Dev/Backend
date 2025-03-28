package map.controller;

import common.response.BaseResponse;
import common.response.BaseResultDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import map.dto.*;
import map.service.MapService;
import map.service.RacingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequestMapping({"/map/{scheduleId}"})
@RequiredArgsConstructor
@RestController
public class MapController {

    private final MapService mapService;
    private final RacingService racingService;

    @GetMapping
    public BaseResponse<ScheduleDetailResDto> getScheduleDetail(@RequestHeader(name = "Access-Member-Id") Long accessMemberId,
                                                                @PathVariable Long scheduleId){
        ScheduleDetailResDto result = mapService.getScheduleDetail(accessMemberId, scheduleId);

        return new BaseResponse<>(result);
    }


    @PostMapping("/location")
    public LocationsResponseDto sendLocation(@PathVariable Long scheduleId,
                                                    @RequestHeader(name = "Access-Member-Id") Long accessMemberId,
                                                    @RequestBody @Valid RealTimeLocationDto realTimeLocationDto) {
        LocationsResponseDto locationsResponseDto = mapService.saveAndSendAllLocation(accessMemberId, scheduleId, realTimeLocationDto);

        return locationsResponseDto;
    }

    @GetMapping("/location")
    public LocationsResponseDto getLocation(@PathVariable Long scheduleId,
                                             @RequestHeader(name = "Access-Member-Id") Long accessMemberId) {

        return mapService.getAllLocation(accessMemberId, scheduleId);
    }

    @PostMapping("/arrival")
    public BaseResponse<BaseResultDto> makeMemberArrive(@PathVariable Long scheduleId,
                                                        @RequestHeader(name = "Access-Member-Id") Long accessMemberId,
                                                        @RequestBody @Valid MemberArrivalDto arrivalDto) {
        Long scheduleResId = mapService.makeMemberArrive(accessMemberId, scheduleId, arrivalDto);

        return BaseResponse.getSimpleRes(scheduleResId);
    }

    @PostMapping("/emoji")
    public BaseResponse<BaseResultDto> sendEmoji(@PathVariable Long scheduleId,
                                                 @RequestHeader(name = "Access-Member-Id") Long accessMemberId,
                                                        @RequestBody @Valid EmojiDto emojiDto) {
        Long scheduleResId = mapService.sendEmoji(accessMemberId, scheduleId, emojiDto);

        return BaseResponse.getSimpleRes(scheduleResId);
    }

    @GetMapping("/racing")
    public BaseResponse<DataResDto> getRacings(@PathVariable Long scheduleId,
                                               @RequestHeader(name = "Access-Member-Id") Long accessMemberId) {
        DataResDto<List<RacingResDto>> racings = racingService.getRacings(accessMemberId, scheduleId);

        return new BaseResponse<>(racings);
    }

    @PostMapping("/racing")
    public BaseResponse<BaseResultDto> makeRacing(@PathVariable Long scheduleId,
                                                  @RequestHeader(name = "Access-Member-Id") Long accessMemberId,
                                                  @RequestBody @Valid RacingAddDto racingAddDto) {
        Long racingId = racingService.makeRacing(accessMemberId, scheduleId, racingAddDto);

        return BaseResponse.getSimpleRes(racingId);
    }

    @PostMapping("/racing/{racingId}/accept")
    public BaseResponse<BaseResultDto> acceptRacing(@PathVariable Long scheduleId,
                                                    @PathVariable Long racingId,
                                                    @RequestHeader(name = "Access-Member-Id") Long accessMemberId) {
        Long acceptRacingId = racingService.acceptRacing(accessMemberId, scheduleId, racingId);

        return BaseResponse.getSimpleRes(acceptRacingId);
    }

    @PostMapping("/racing/{racingId}/deny")
    public BaseResponse<BaseResultDto> denyRacing(@PathVariable Long scheduleId,
                                                  @PathVariable Long racingId,
                                                  @RequestHeader(name = "Access-Member-Id") Long accessMemberId) {
        Long deniedRacingId = racingService.denyRacing(accessMemberId, scheduleId, racingId);

        return BaseResponse.getSimpleRes(deniedRacingId);
    }


}
