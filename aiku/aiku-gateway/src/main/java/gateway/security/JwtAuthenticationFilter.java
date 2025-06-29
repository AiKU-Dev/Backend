package gateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        HttpMethod method = exchange.getRequest().getMethod();

        // /login/refresh는 특별 처리 - 만료된 토큰에서도 Member ID 추출
        if ("/login/refresh".equals(path) && HttpMethod.POST.equals(method)) {
            String token = resolveToken(exchange);
            if (token != null) {
                try {
                    // 만료된 토큰에서 member ID 추출
                    jwtTokenProvider.extractMemberIdFromExpiredToken(token);

                    // MDC에서 Member ID를 가져와서 헤더에 추가
                    String memberId = MDC.get("accessMemberId");
                    if (memberId != null) {
                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("Access-Member-Id", memberId)
                                .build();

                        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
                        return chain.filter(mutatedExchange);
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract member ID from expired token", e);
                }
            }
            return chain.filter(exchange);
        }

        if (JwtSecurityUtils.isPermitAllPath(path, method)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            SecurityContext context = new SecurityContextImpl(authentication);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("Access-Member-Id", MDC.get("accessMemberId"))
                    .build();

            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
            return chain.filter(mutatedExchange)
                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
        }

        return chain.filter(exchange);
    }

    private String resolveToken(ServerWebExchange exchange) {
        String bearerToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }


}
