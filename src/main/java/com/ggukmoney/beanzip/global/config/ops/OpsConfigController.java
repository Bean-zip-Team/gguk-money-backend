package com.ggukmoney.beanzip.global.config.ops;

import com.ggukmoney.beanzip.global.config.entity.AppConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** app_config 를 보고 바꾸는 운영 화면. 서버마다 자기 DB 만 바꾼다(알파 화면 → 알파 DB). */
@Controller
@RequiredArgsConstructor
@RequestMapping("/ops")
public class OpsConfigController {

    private static final Logger log = LoggerFactory.getLogger(OpsConfigController.class);
    private static final DateTimeFormatter KST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Seoul"));

    private static final String UNUSED_GROUP = "코드에서 쓰지 않는 키";

    private final OpsConfigService service;
    private final OpsSession session;
    private final OpsSettings settings;
    private final PolicyValueRules rules;
    private final Environment environment;

    @ModelAttribute("env")
    String env() {
        // 프로필이 없으면 운영으로 표시한다. 헷갈리면 조심하는 쪽으로 틀리게 한다.
        return environment.matchesProfiles("alpha") ? "ALPHA" : "PRODUCTION";
    }

    @GetMapping("/login")
    String loginPage() {
        return "ops/login";
    }

    @PostMapping("/login")
    String login(@RequestParam(defaultValue = "") String token, HttpServletRequest request,
                 HttpServletResponse response, Model model) {
        // 토큰이 주소(?token=)에 실리면 접근 로그에 남는다. 폼 본문으로만 받는다.
        if (request.getQueryString() != null || !session.matchesToken(token)) {
            log.warn("OPS_LOGIN_FAILED remote={}", request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            model.addAttribute("error", "토큰이 맞지 않습니다.");
            return "ops/login";
        }
        session.start(request, response);
        return "redirect:/ops/config";
    }

    @PostMapping("/logout")
    String logout(HttpServletRequest request, HttpServletResponse response) {
        session.end(request, response);
        return "redirect:/ops/login";
    }

    /** 사이드바와 목록이 같은 id(g0, g1, ...)로 분류를 가리킨다. */
    @ModelAttribute("groups")
    List<Group> groups() {
        List<String> names = new ArrayList<>(rules.all().values().stream().map(PolicyValueRules.Rule::group).distinct().toList());
        names.add(UNUSED_GROUP);
        List<Group> groups = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            groups.add(new Group("g" + i, names.get(i)));
        }
        return groups;
    }

    /** 분류 순서·키 순서는 PolicyValueRules 에 적힌 순서를 따른다. 규칙이 없는 키는 맨 뒤에 모은다. */
    @GetMapping("/config")
    String list(Model model) {
        Map<String, AppConfig> current = new LinkedHashMap<>();
        service.currentValues().forEach(config -> current.put(config.getConfigKey(), config));

        Map<String, List<Row>> rowsByGroup = new LinkedHashMap<>();
        rules.all().forEach((key, rule) -> {
            AppConfig config = current.remove(key);
            if (config != null) {
                rowsByGroup.computeIfAbsent(rule.group(), group -> new ArrayList<>()).add(row(config));
            }
        });
        current.values().forEach(config -> rowsByGroup.computeIfAbsent(UNUSED_GROUP, group -> new ArrayList<>()).add(row(config)));
        model.addAttribute("sections", groups().stream()
                .filter(group -> rowsByGroup.containsKey(group.name()))
                .map(group -> new Section(group, rowsByGroup.get(group.name())))
                .toList());
        return "ops/config-list";
    }

    @GetMapping("/settings")
    String settings(Model model) {
        Map<String, List<OpsSettings.Entry>> sections = new LinkedHashMap<>();
        settings.entries().forEach(entry -> sections.computeIfAbsent(entry.group(), group -> new ArrayList<>()).add(entry));
        model.addAttribute("sections", sections);
        return "ops/settings";
    }

    @GetMapping("/config/{key:.+}")
    String edit(@PathVariable String key, Model model) {
        return editPage(key, model);
    }

    @PostMapping("/config/{key:.+}")
    String change(@PathVariable String key,
                  @RequestParam(defaultValue = "") String basedOn,
                  @RequestParam(defaultValue = "") String value,
                  @RequestParam(defaultValue = "") String changedBy,
                  @RequestParam(defaultValue = "") String reason,
                  HttpServletResponse response,
                  Model model) {
        try {
            service.change(key, basedOn, value, changedBy, reason);
        } catch (IllegalArgumentException exception) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", exception.getMessage());
            model.addAttribute("value", value);
            model.addAttribute("changedBy", changedBy);
            model.addAttribute("reason", reason);
            return editPage(key, model);
        }
        // 경로 변수를 다시 채워 인코딩까지 Spring 에 맡긴다.
        return "redirect:/ops/config/{key}";
    }

    private String editPage(String key, Model model) {
        model.addAttribute("current", row(service.current(key)));
        model.addAttribute("rule", rules.find(key).orElse(null));
        model.addAttribute("history", service.history(key).stream().map(this::row).toList());
        return "ops/config-edit";
    }

    private Row row(AppConfig config) {
        String key = config.getConfigKey();
        PolicyValueRules.Rule rule = rules.find(key).orElse(null);
        String summary = rule == null ? null : rule.summarize(config.getConfigValue());
        return new Row(key, config.getPublicId().toString(), config.getConfigValue(),
                summary != null ? summary : readable(key, config.getConfigValue()),
                rule == null ? "코드가 읽지 않는 키입니다. 바꿔도 동작에 반영되지 않습니다." : rule.description(),
                format(config.getEffectiveAt()), config.getChangedBy(), config.getChangeReason(), rule != null);
    }

    /** 초·분 단위 값은 사람이 읽는 시간을 덧붙인다(1800 → 1800 · 30분). */
    private static String readable(String key, String value) {
        try {
            if (key.endsWith("Seconds")) {
                return value + " · " + duration(Duration.ofSeconds(Long.parseLong(value)));
            }
            if (key.endsWith("Minutes")) {
                return value + " · " + duration(Duration.ofMinutes(Long.parseLong(value)));
            }
        } catch (NumberFormatException ignored) {
            // 숫자가 아니면 값 그대로 보여준다.
        }
        return value;
    }

    private static String duration(Duration duration) {
        if (duration.isZero()) {
            return "0";
        }
        StringBuilder text = new StringBuilder();
        if (duration.toDays() > 0) text.append(duration.toDays()).append("일 ");
        if (duration.toHoursPart() > 0) text.append(duration.toHoursPart()).append("시간 ");
        if (duration.toMinutesPart() > 0) text.append(duration.toMinutesPart()).append("분 ");
        if (duration.toSecondsPart() > 0) text.append(duration.toSecondsPart()).append("초");
        return text.toString().trim();
    }

    private static String format(Instant instant) {
        return instant == null ? "" : KST.format(instant);
    }

    record Group(String id, String name) {
    }

    record Section(Group group, List<Row> rows) {
    }

    record Row(String key, String publicId, String value, String display, String description, String effectiveAt,
               String changedBy, String changeReason, boolean editable) {
    }
}
