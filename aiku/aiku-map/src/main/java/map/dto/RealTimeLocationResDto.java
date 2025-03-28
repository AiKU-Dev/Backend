package map.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RealTimeLocationResDto {
    private Double latitude;
    private Double longitude;
    private Boolean isArrived;
}
