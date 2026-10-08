package com.clauseiq.auth;

import com.clauseiq.auth.AuthDtos.AuthResponse;
import com.clauseiq.auth.AuthDtos.CreateUserRequest;
import com.clauseiq.auth.AuthDtos.LoginRequest;
import com.clauseiq.auth.AuthDtos.RegisterOrganizationRequest;
import com.clauseiq.auth.AuthDtos.UserResponse;
import com.clauseiq.common.ApiException;
import com.clauseiq.security.JwtService;
import com.clauseiq.security.TenantContext;
import com.clauseiq.tenant.Tenant;
import com.clauseiq.tenant.TenantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(TenantRepository tenantRepository, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Creates a new tenant (organization) and its first ADMIN user. */
    @Transactional
    public AuthResponse registerOrganization(RegisterOrganizationRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        Tenant tenant = tenantRepository.save(new Tenant(request.organizationName().trim()));
        User admin = userRepository.save(
                new User(tenant.getId(), email, passwordEncoder.encode(request.password()), Role.ADMIN));
        return toAuthResponse(admin, tenant);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalize(request.email()))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                // Same message for unknown email and wrong password, so accounts can't be enumerated.
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return toAuthResponse(user, tenantRepository.getReferenceById(user.getTenantId()));
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        User user = userRepository.findById(TenantContext.currentUser().userId())
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
        return toUserResponse(user, tenantRepository.getReferenceById(user.getTenantId()));
    }

    /** ADMIN adds a user to their own tenant; the tenant always comes from the admin's token. */
    @Transactional
    public UserResponse createUserInCurrentTenant(CreateUserRequest request) {
        Long tenantId = TenantContext.currentTenantId();
        String email = normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = userRepository.save(
                new User(tenantId, email, passwordEncoder.encode(request.password()), request.role()));
        return toUserResponse(user, tenantRepository.getReferenceById(tenantId));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsersInCurrentTenant() {
        Long tenantId = TenantContext.currentTenantId();
        Tenant tenant = tenantRepository.getReferenceById(tenantId);
        return userRepository.findAllByTenantIdOrderByIdAsc(tenantId).stream()
                .map(u -> toUserResponse(u, tenant))
                .toList();
    }

    private AuthResponse toAuthResponse(User user, Tenant tenant) {
        return new AuthResponse(jwtService.generateToken(user), jwtService.expiresInSeconds(),
                toUserResponse(user, tenant));
    }

    private static UserResponse toUserResponse(User user, Tenant tenant) {
        return new UserResponse(user.getId(), user.getTenantId(), tenant.getName(), user.getEmail(), user.getRole());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
