package et.mahtem.util;

import et.mahtem.domain.AppUser;
import et.mahtem.domain.Role;
import java.util.Arrays;
import java.util.UUID;

/**
 * The signed-in staff member, loaded fresh from the database on every request so that a disabled
 * account or a role change takes effect immediately. The organization always comes from here,
 * never from request parameters, which is what keeps tenants apart.
 */
public record Actor(UUID userId, UUID organizationId, String name, String email, Role role, String ip) {

    public static Actor of(AppUser user, String ip) {
        return new Actor(user.getId(), user.getOrganizationId(), user.getFullName(), user.getEmail(), user.getRole(), ip);
    }

    public void require(Role... allowed) {
        if (!Arrays.asList(allowed).contains(role)) {
            throw ApiException.forbidden("Your role (" + role.name().toLowerCase() + ") cannot do that.");
        }
    }
}
