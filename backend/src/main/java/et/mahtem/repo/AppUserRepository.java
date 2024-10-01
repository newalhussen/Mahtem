package et.mahtem.repo;

import et.mahtem.domain.AppUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByInviteTokenHash(String tokenHash);

    Optional<AppUser> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<AppUser> findByOrganizationIdOrderByFullNameAsc(UUID organizationId);
}
