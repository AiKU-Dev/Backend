package common.domain.value_reference;

import common.domain.event.CommonEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Embeddable
public class EventValue {

    @Column(name = "eventId")
    private Long id;

    public EventValue(Long id) {
        this.id = id;
    }
}
