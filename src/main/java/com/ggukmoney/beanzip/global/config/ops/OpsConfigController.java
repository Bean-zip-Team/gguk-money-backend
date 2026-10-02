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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** app_config 를 보고 바꾸는 운영 화면. 서버마다 자기 DB 만 바꾼다(알파 화면 → 알파 DB). */
@Controller
@RequiredArgsConstructor
@RequestMapping("/ops")
public class OpsConfigController {

    private static final Logger log = LoggerFactory.getLogger(OpsConfigController.class);
    private static final DateTimeFormatter KST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Seoul"));

    private final OpsConfigService service;
    private final OpsSession session;
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

    @GetMapping("/config")
    String list(Model model) {
        model.addAttribute("rows", service.currentValues().stream().map(this::row).toList());
        return "ops/config-list";
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
        model.addAttribute("rule", service.rule(key).orElse(null));
        model.addAttribute("history", service.history(key).stream().map(this::row).toList());
        return "ops/config-edit";
    }

    private Row row(AppConfig config) {
        return new Row(config.getConfigKey(), config.getPublicId().toString(), config.getConfigValue(),
                format(config.getEffectiveAt()), config.getChangedBy(), config.getChangeReason(),
                service.rule(config.getConfigKey()).isPresent());
    }

    private static String format(Instant instant) {
        return instant == null ? "" : KST.format(instant);
    }

    record Row(String key, String publicId, String value, String effectiveAt, String changedBy, String changeReason,
               boolean editable) {
    }
}
