package aiku_main.oauth.error;

import aiku_main.exception.InvalidIdTokenException;
import feign.Response;
import feign.codec.ErrorDecoder;

public class KauthErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        KakaoKauthErrorResponse body = KakaoKauthErrorResponse.from(response);

        try {
            KakaoKauthErrorCode kakaoKauthErrorCode =
                    KakaoKauthErrorCode.valueOf(body.getErrorCode());
            KauthErrorReason errorReason = kakaoKauthErrorCode.getErrorReason();
            throw new InvalidIdTokenException(errorReason.getReason());
        } catch (IllegalArgumentException e) {
            KakaoKauthErrorCode koeInvalidRequest = KakaoKauthErrorCode.KOE_INVALID_REQUEST;
            KauthErrorReason errorReason = koeInvalidRequest.getErrorReason();
            throw new InvalidIdTokenException(errorReason.getReason());
        }
    }
}
