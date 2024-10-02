package login.dto;

import common.domain.member.MemberProfileBackground;
import common.domain.member.MemberProfileCharacter;
import common.domain.member.MemberProfileType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfileDto {
    private MemberProfileType profileType;
    private MultipartFile profileImg;
    private MemberProfileCharacter profileCharacter;
    private MemberProfileBackground profileBackground;
}