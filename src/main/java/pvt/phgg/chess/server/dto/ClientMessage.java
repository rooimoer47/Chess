package pvt.phgg.chess.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientMessage(
        String type,
        Integer fromRow,
        Integer fromCol,
        Integer toRow,
        Integer toCol,
        String choice
) {}
