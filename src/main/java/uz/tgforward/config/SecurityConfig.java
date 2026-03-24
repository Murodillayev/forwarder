package uz.tgforward.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import uz.tgforward.repository.AppUserRepository;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppUserRepository userRepo;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/css/**", "/js/**", "/error").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/phone-login")
                .usernameParameter("phone")
                .passwordParameter("code")
                .defaultSuccessUrl("/auth/phone-success", true)
                .failureUrl("/auth/login?phoneError")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/auth/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }

    /**
     * Ikki turdagi login uchun UserDetailsService:
     *
     * 1. Telegram widget  → AuthController qo'lda sessiya ochadi (bu yerga kelmaydi)
     * 2. Phone + code     → Spring Security /auth/phone-login ga POST qiladi,
     *                       username=+998..., password=12345
     *                       Bu yerda phone bo'yicha user topiladi,
     *                       passwordHash BCrypt bilan tekshiriladi.
     *
     * MUHIM: Phone login uchun parol muddati (expiry) bu yerda tekshirilmaydi —
     * chunki UserDetailsService faqat hash solishtiradi.
     * Expiry tekshiruvi AuthController.phoneLogin() da amalga oshiriladi
     * (login muvaffaqiyatli bo'lgandan keyin redirect orqali xabar beriladi).
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            var userOpt = username.startsWith("+")
                ? userRepo.findByPhoneNumber(username)
                : userRepo.findByTelegramId(Long.parseLong(username));

            var user = userOpt.orElseThrow(
                () -> new UsernameNotFoundException("Foydalanuvchi topilmadi: " + username));

            return User.builder()
                .username(username)
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .authorities(List.of(
                    new SimpleGrantedAuthority("ROLE_" + user.getPlanType().name())))
                .disabled(!user.isEnabled())
                .build();
        };
    }
}
