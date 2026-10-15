// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** 同源会话认证、CSRF 防护和匿名接口限制。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Configuration
public class SecurityConfig {
  /** 默认保护写接口，健康和 CSRF 引导可匿名读取。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Bean
  SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/health",
                        "/actuator/health/readiness",
                        "/api/auth/csrf",
                        "/api/auth/login",
                        "/api/player/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .csrf(c -> c.ignoringRequestMatchers("/api/player/**"))
        .formLogin(a -> a.disable())
        .httpBasic(a -> a.disable())
        .exceptionHandling(
            a ->
                a.authenticationEntryPoint(
                        (req, res, e) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          res.getWriter().write("{\"code\":\"UNAUTHENTICATED\"}");
                        })
                    .accessDeniedHandler(
                        (req, res, e) -> {
                          res.setStatus(403);
                          res.setContentType("application/json");
                          res.getWriter().write("{\"code\":\"FORBIDDEN\"}");
                        }))
        .logout(a -> a.disable());
    return http.build();
  }

  /** 禁用Spring默认示例账户及其密码日志；身份只走本系统登录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Bean
  org.springframework.security.core.userdetails.UserDetailsService noDefaultUsers() {
    return username -> {
      throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
          "UNAUTHENTICATED");
    };
  }

  /** BCrypt 12 轮密码散列。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Bean
  BCryptPasswordEncoder encoder() {
    return new BCryptPasswordEncoder(12);
  }
}
