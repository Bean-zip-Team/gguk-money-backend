package com.ggukmoney.beanzip.global.config.ops;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 화면에서 입력한 값을 키의 규칙({@link PolicyValueRules})으로 검사하고 app_config 에 넣을 JSON 으로 바꾼다.
 * 실패하면 화면에 보여줄 문구로 IllegalArgumentException 을 던진다. 다른 예외는 밖으로 내보내지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AppConfigValueValidator {

    private final ObjectMapper objectMapper;

    public String normalize(PolicyValueRules.Rule rule, String currentJson, String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("값을 입력하세요.");
        }

        JsonNode next = rule.kind() == PolicyValueRules.Kind.TEXT ? asText(raw) : parse(raw);
        String json = objectMapper.writeValueAsString(next);
        String checked = rule.kind() == PolicyValueRules.Kind.TEXT ? next.asString() : json;
        if (!rule.accepts(checked)) {
            throw new IllegalArgumentException("허용되지 않는 값입니다. 규칙: " + rule.hint());
        }
        if (sameAs(currentJson, next)) {
            throw new IllegalArgumentException("지금 값과 같습니다.");
        }
        return json;
    }

    /** 문자열 키는 따옴표 없이 입력해도 된다. 이미 JSON 문자열로 적었으면 그대로 쓴다. */
    private JsonNode asText(String raw) {
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            JsonNode quoted = parse(raw);
            if (quoted.isString()) {
                return quoted;
            }
        }
        return objectMapper.getNodeFactory().stringNode(raw);
    }

    private JsonNode parse(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("형식이 올바르지 않습니다.");
        }
    }

    private boolean sameAs(String currentJson, JsonNode next) {
        try {
            return objectMapper.readTree(currentJson).equals(next);
        } catch (JacksonException exception) {
            return false;
        }
    }
}
