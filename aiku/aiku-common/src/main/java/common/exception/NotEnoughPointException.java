package common.exception;

import common.response.status.BaseErrorCode;
import common.response.status.StatusCode;

/**
 * 소유 포인트가 부족할 때 throw
 * 디폴트 status는 400 BadRequest
 */
public class NotEnoughPointException extends BaseException{

    public NotEnoughPointException(StatusCode status, String errorMessage) {
        super(status, errorMessage);
    }

    public NotEnoughPointException(String errorMessage) {
        super(BaseErrorCode.BAD_REQUEST, errorMessage);
    }

    public NotEnoughPointException() {
        super(BaseErrorCode.BAD_REQUEST, "소유 포인트가 부족합니다.");
    }
}
