package gateway;

import gateway.security.JwtAuthenticationFilter;
import gateway.security.JwtExceptionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
public class GatewayConfig {

    private final JwtExceptionFilter jwtExceptionFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("aiku-main-signUp", r -> r.path("/users", "/user/nickname", "/term")
                        .uri("http://localhost:8081"))
                .route("aiku-main-login", r -> r.path("/login/sign-in", "/login/refresh")
                        .uri("http://localhost:8081"))
                .route("aiku-main", r -> r.path("/users/**", "/groups/**", "/schedules/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())) // 필터 적용
                                .filter(jwtExceptionFilter.apply(new JwtExceptionFilter.Config())))
                        .uri("http://localhost:8081"))
                .route("aiku-map", r -> r.path("/map/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())) // 필터 적용
                                .filter(jwtExceptionFilter.apply(new JwtExceptionFilter.Config())))
                        .uri("http://localhost:8082"))
                .build();
    }
}

