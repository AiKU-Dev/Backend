package map.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LocationsResponseDto {

    private int count;
    private Map<Long, RealTimeLocationResDto> locations;
}
