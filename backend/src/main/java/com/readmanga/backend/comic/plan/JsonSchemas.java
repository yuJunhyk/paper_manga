package com.readmanga.backend.comic.plan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI에게 "이 모양으로만 답하라"고 알려 주는 JSON 스키마를 만드는 도우미. 칸 순서가 바뀌지 않게 LinkedHashMap을 쓴다. */
public final class JsonSchemas {

    private JsonSchemas() {
    }

    public static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    /** 정해진 값 중 하나만 쓸 수 있는 글자. 예: 화자는 콩이·냥박사·해설 중 하나. */
    public static Map<String, Object> stringEnum(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    public static Map<String, Object> integer() {
        return Map.of("type", "integer");
    }

    public static Map<String, Object> array(Map<String, Object> items, int minItems, int maxItems) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("minItems", minItems);
        schema.put("maxItems", maxItems);
        schema.put("items", items);
        return schema;
    }

    /** 칸 이름과 모양을 번갈아 넘긴다. 예: object("title", string(), "count", integer()). 모든 칸이 필수다. */
    public static Map<String, Object> object(Object... namesAndSchemas) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < namesAndSchemas.length; i += 2) {
            properties.put((String) namesAndSchemas[i], namesAndSchemas[i + 1]);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        return schema;
    }
}
