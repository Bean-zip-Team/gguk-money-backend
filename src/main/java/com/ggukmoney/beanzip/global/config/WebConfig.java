package com.ggukmoney.beanzip.global.config;

import com.ggukmoney.beanzip.global.config.ops.OpsPageInterceptor;
import com.ggukmoney.beanzip.global.interceptor.AuthInterceptor;
import com.ggukmoney.beanzip.global.logging.RequestLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final OpsPageInterceptor opsPageInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8081}")
    private String[] allowedOrigins;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 운영 화면은 유저 JWT 가 아니라 로그인 쿠키로 가른다. 두 인터셉터가 같은 경로 해석을 쓰도록
        // 둘 다 패턴으로 나눈다(서블릿 경로 문자열 비교와 섞으면 /x/../ops 같은 경로에서 어긋날 수 있다).
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/ops/**")
                .order(1);
        registry.addInterceptor(opsPageInterceptor)
                .addPathPatterns("/ops/**")
                .order(2);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // API 만 다른 출처(앱인토스 웹뷰 등)에 연다. 운영 화면(/ops)은 같은 출처에서만 쓴다.
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()
                )
                .allowedHeaders("*")
                .exposedHeaders("Location", RequestLogContext.REQUEST_ID_HEADER)
                .maxAge(3600);
    }
}
