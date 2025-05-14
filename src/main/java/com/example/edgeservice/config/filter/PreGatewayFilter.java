package com.example.edgeservice.config.filter;

import com.example.edgeservice.config.client.AuthServiceClient;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Slf4j
@Order(0)
@Component
public class PreGatewayFilter extends AbstractGatewayFilterFactory<PreGatewayFilter.Config> {

    private final AuthServiceClient authServiceClient;

    public PreGatewayFilter(AuthServiceClient authServiceClient) {
        super(Config.class);
        this.authServiceClient = authServiceClient;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 요청 경로를 확인하여 로그인 및 회원가입 요청이라면 토큰 체크를 우회
            String path = exchange.getRequest().getURI().getPath();
            HttpMethod method = exchange.getRequest().getMethod();

            log.info("Request path: {}", path);

            if (path.equals("/auths/login") ||
                    path.equals("/auths/join") ||
                    path.equals("/auths/login/oauth") ||
                    path.equals("/auths/logout") ||
                    path.equals("/auths/refresh") ||
                    path.equals("/auths/re/tokens") ||
                    path.startsWith("/auths/email") ||
                    path.startsWith("/api/geocode") ||
                    path.equals("/orders/prepare") ||
                    path.equals("/orders/update-fail") ||
                    path.equals("/orders/update-success") ||
                    path.startsWith("/auths/check-id")
            ) {
                // 로그인과 회원가입 요청에는 토큰 검증을 생략
                return chain.filter(exchange);
            }

            String token = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION);
            log.info("tokens: {}",token);

            if (token != null && !token.startsWith(config.getTokenPrefix())) {
                exchange.getResponse().setStatusCode(UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            return authServiceClient.validToken(token)
                    .flatMap(statusNum -> {
                        if(statusNum == 2){
                            // 토큰 만료
                            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(config.getAuthenticationTimeoutCode()));
                            return exchange.getResponse().setComplete();
                        } else if (statusNum == 3 || statusNum == -1) {
                            // 토큰 무효
                            exchange.getResponse().setStatusCode(INTERNAL_SERVER_ERROR);
                            return exchange.getResponse().setComplete();
                        }else if (statusNum == 0) {
                            // 토큰 없음
                            exchange.getResponse().setStatusCode(UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }

                        return chain.filter(exchange);
                    })
                    .onErrorResume(e -> {
                        log.error("token filter error: {}", e.getMessage());
                        exchange.getResponse().setStatusCode(INTERNAL_SERVER_ERROR);
                        return exchange.getResponse().setComplete();
                    });

        };
    }

    @Getter
    @Setter
    public static class Config {
        private String tokenPrefix = "Bearer ";
        private int authenticationTimeoutCode = 419;
    }
}