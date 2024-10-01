package login.oauth;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OauthInfo {
    private String provider = "KAKAO";

    private String oid;
    private String email;

    @Builder
    public OauthInfo(String oid, String email) {
        this.oid = oid;
        this.email = email;
    }
}
