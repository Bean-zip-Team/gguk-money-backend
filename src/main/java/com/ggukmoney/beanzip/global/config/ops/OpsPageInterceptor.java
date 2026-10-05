package com.ggukmoney.beanzip.global.config.ops;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 운영 화면(/ops/**) 보호.
 *
 * <ul>
 *   <li>쓰기 요청(POST 등)은 같은 출처에서 보낸 것만 받는다. SameSite 는 같은 사이트(*.compounding.co.kr)의
 *       다른 페이지가 보내는 폼을 막지 못하므로 브라우저가 붙이는 Sec-Fetch-Site 로 가른다. 헤더가 없으면 거부한다.</li>
 *   <li>로그인 화면 말고는 로그인 쿠키가 있어야 한다. 없으면 로그인 화면으로 보낸다.</li>
 *   <li>캐시·프레임 삽입·리퍼러 노출을 막는다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class OpsPageInterceptor implements HandlerInterceptor {

    private final OpsSession session;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
        // no-referrer 면 브라우저가 같은 출처 폼에도 Origin: null 을 붙여 CORS 검사에 걸린다.
        response.setHeader("Referrer-Policy", "same-origin");

        boolean write = !"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod());
        if (write && !"same-origin".equals(request.getHeader("Sec-Fetch-Site"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        if (isLoginPage(request) || session.isAuthenticated(request)) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/ops/login");
        return false;
    }

    private static boolean isLoginPage(HttpServletRequest request) {
        return (request.getContextPath() + "/ops/login").equals(request.getRequestURI());
    }
}
