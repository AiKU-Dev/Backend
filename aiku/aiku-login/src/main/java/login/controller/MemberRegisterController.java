package login.controller;

import common.response.BaseResponse;
import common.response.BaseResultDto;
import login.dto.*;
import login.service.MemberRegisterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/users")
@Slf4j
@RequiredArgsConstructor
@RestController
public class MemberRegisterController {
    private final MemberRegisterService memberRegisterService;
    @PostMapping
    public BaseResponse<BaseResultDto> register(@ModelAttribute MemberRegisterDto memberRegisterDto){
        Long addId = memberRegisterService.register(memberRegisterDto);

        return BaseResponse.getSimpleRes(addId);
    }

    @GetMapping("/nickname")
    public BaseResponse<NicknameExistResDto> checkNickname(@RequestBody NicknameExistDto nicknameExistDto) {
        NicknameExistResDto checkNickname = memberRegisterService.checkNickname(nicknameExistDto);

        return new BaseResponse<>(checkNickname);
    }
}
