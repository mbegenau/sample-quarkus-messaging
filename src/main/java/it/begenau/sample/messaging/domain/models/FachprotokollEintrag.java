package it.begenau.sample.messaging.domain.models;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FachprotokollEintrag {
    @PastOrPresent
    @Builder.Default
    private ZonedDateTime timestamp = ZonedDateTime.now();

    @NotEmpty
    private String emitter;

    @Length(max = 255)
    @NotEmpty
    private String payload;
}
