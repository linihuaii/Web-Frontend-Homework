package edu.library;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    record Credentials(
            @NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,32}", message = "用户名须为 3–32 位字母、数字或下划线") String username,
            @NotBlank(message = "请输入密码")
            @Size(min = 8, max = 72, message = "密码须为 8–72 个字符") String password) {}
    record Account(long id, String username, String role) {}

    private final JdbcTemplate db;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String fallbackHash = encoder.encode("unused-placeholder-password");

    AuthController(JdbcTemplate db) {
        this.db = db;
    }

    @Bean
    ApplicationRunner initializeAdmin(@Value("${library.admin.username}") String username,
                                      @Value("${library.admin.password}") String password) {
        return args -> {
            if (password.isEmpty()) {
                if (db.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN'", Integer.class) == 0) {
                    throw new IllegalStateException("首次启动须设置 ADMIN_PASSWORD");
                }
                return;
            }
            if (!username.matches("[A-Za-z0-9_]{3,32}") || password.isBlank()
                    || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalStateException("管理员用户名或密码格式无效");
            }
            try {
                db.update("INSERT INTO users(username,password_hash,role) VALUES (?,?,'ADMIN')",
                        username, encoder.encode(password));
            } catch (DuplicateKeyException error) {
                String role = db.queryForObject("SELECT role FROM users WHERE username=?", String.class, username);
                if (!"ADMIN".equals(role)) {
                    throw new IllegalStateException("管理员用户名已被读者占用", error);
                }
            }
        };
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    void register(@Valid @RequestBody Credentials credentials) {
        checkPasswordBytes(credentials.password());
        try {
            db.update("INSERT INTO users(username,password_hash,role) VALUES (?,?,'READER')",
                    credentials.username(), encoder.encode(credentials.password()));
        } catch (DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
    }

    @PostMapping("/login")
    Account login(@Valid @RequestBody Credentials credentials, HttpServletRequest request) {
        return establishSession(credentials, request, "READER");
    }

    @PostMapping("/admin-login")
    Account adminLogin(@Valid @RequestBody Credentials credentials, HttpServletRequest request) {
        return establishSession(credentials, request, "ADMIN");
    }

    @GetMapping("/me")
    Account me(HttpServletRequest request) {
        return require(request, null);
    }

    @PostMapping(value = "/logout", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private Account establishSession(Credentials credentials, HttpServletRequest request, String role) {
        checkPasswordBytes(credentials.password());
        var rows = db.queryForList("SELECT id,username,password_hash,role FROM users WHERE username=?",
                credentials.username());
        Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);
        String hash = (String) row.getOrDefault("password_hash", fallbackHash);
        if (!encoder.matches(credentials.password(), hash) || !role.equals(row.get("role"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        var account = new Account(((Number) row.get("id")).longValue(), (String) row.get("username"), role);
        var session = request.getSession();
        request.changeSessionId();
        session.setAttribute("account", account);
        return account;
    }

    static Account require(HttpServletRequest request, String role) {
        var session = request.getSession(false);
        var account = session == null ? null : (Account) session.getAttribute("account");
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (role != null && !role.equals(account.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权执行此操作");
        }
        return account;
    }

    private void checkPasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能超过 72 字节");
        }
    }
}
