package aiku_main.dto.member;

import aiku_main.dto.MemberProfileResDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberResDto {
    /**
     *    {
     *       "memberId":1,
     *       "nickname":"지정희",
     *       "kakaoId":"012341234",
     *       "memberProfile":{
     *          "profileType":"IMG",
     *          "profileImg":"http://amazon.s3.image.jpg",
     *          "profileCharacter":null,
     *          "profileBackground":null
     *       },
     *       "title":{
     *          "titleName":"전장의 지배자",
     *          "titleImg":"http://www.image.com"
     *       },
     *       "point":0
     *    }
     */
    private Long memberId;
    private String nickname;
    private Long kakaoId;
    private MemberProfileResDto memberProfile;
    private TitleMemberResDto title;
    private int point;
    private String email;
}
