package et.mahtem.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Stores the signatory list as JSON text. */
@Converter
public class SignatoryListConverter implements AttributeConverter<List<Signatory>, String> {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<Signatory>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<Signatory> attribute) {
        return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
    }

    @Override
    public List<Signatory> convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? List.of() : MAPPER.readValue(dbData, TYPE);
    }
}
