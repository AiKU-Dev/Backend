package aiku_main.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DataResDto<T> {

    private Long totalCount;
    private int page;
    private T data;
}
