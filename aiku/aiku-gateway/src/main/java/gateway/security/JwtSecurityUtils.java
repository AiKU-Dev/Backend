package gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;

public class JwtSecurityUtils {
    public static final String[] ALL_METHOD_PERMIT_ALL_PATHS = {
            "/login/sign-in/**",
            "/login/refresh",
            "/error",
            "/users/nickname",
            "/term/**"
    };

    public static final String[] POST_METHOD_PERMIT_ALL_PATHS = {
            "/users"
    };

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    public static boolean isPermitAllPath(String path, HttpMethod method) {
        boolean isAllMethodPermitted = Arrays.stream(ALL_METHOD_PERMIT_ALL_PATHS)
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (isAllMethodPermitted) {
            return true;
        }

        // POST 메서드인 경우 POST 전용 허용 경로도 확인
        if (HttpMethod.POST.equals(method)) {
            return Arrays.stream(POST_METHOD_PERMIT_ALL_PATHS)
                    .anyMatch(pattern -> pathMatcher.match(pattern, path));
        }

        return false;
    }


}
