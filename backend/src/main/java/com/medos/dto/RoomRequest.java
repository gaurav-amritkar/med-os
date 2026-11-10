package com.medos.dto;

import com.medos.entity.Room;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Create-room request.
 *
 * <p>Deliberately carries no {@code id}, {@code tenantId}, {@code occupied} or
 * {@code version} field. Accepting the {@link Room} entity directly let a client
 * supply its own primary key, move a room to another tenant, or create an
 * already-occupied room (#56). Those values come from trusted server context.
 */
@Data
public class RoomRequest {

    @NotBlank
    @Size(max = 32)
    @Pattern(regexp = "[A-Za-z0-9._-]+",
            message = "roomNumber may contain only letters, digits, dot, underscore and hyphen")
    private String roomNumber;

    /**
     * Normalised ward name. Wards are free text in this schema, but
     * "ICU", "icu" and "I.C.U." must not become three wards, so the value is
     * trimmed and upper-cased before it is stored.
     */
    @NotBlank
    @Size(max = 64)
    private String ward;

    @NotNull
    private Room.RoomType roomType;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("1000000.00")
    private BigDecimal dailyRate;

    /** Beds in the room. A room with zero capacity cannot admit anyone. */
    @NotNull
    @Min(1)
    private Integer capacity;

    private Integer floor;

    @Size(max = 2000)
    private String notes;
}
