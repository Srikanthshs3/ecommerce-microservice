package org.praskanth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.UserResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserProfileAuthFilter extends OncePerRequestFilter {

    private final UserApiCall userApiCall;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String uri = request.getRequestURI();
        String path = request.getServletPath();

        // Skip actuator and any internal metric endpoints
        return (uri != null && uri.contains("/actuator")) ||
                (path != null && path.contains("/actuator"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String jwt = request.getHeader("Authorization");
        if (jwt != null && jwt.startsWith("Bearer ")) {
            try {
                ApiResponse<UserResponse> apiResponse = userApiCall.getUserProfile(jwt);
                UserResponse user =  apiResponse.getData();

                if (user != null && user.getRole() != null && !user.getRole().isEmpty()){
                    String role = user.getRole().iterator().next();

                    List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }catch (Exception e){
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);

    }
}
