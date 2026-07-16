package com.gatekeeper.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Overrides the inherited findAll() to eagerly fetch the lazy Role
     * association in a single query, avoiding an N+1 lazy load per user
     * (UserResponse.from() reads user.getRole().getName() for every row).
     */
    @Override
    @EntityGraph(attributePaths = "role")
    List<User> findAll();

    /**
     * Overrides the inherited findById(Long) for the same reason findAll()
     * already is above: UserService.findById's own transaction closes the
     * instant it returns (open-in-view is disabled project-wide), and both
     * of its callers read a lazy association afterward -
     * UserController.findById via UserResponse.from (role), and
     * AuthController.currentUser via CurrentUserResponse.from (role and
     * organization) - each throwing LazyInitializationException. Both
     * associations are cheap, always-present (non-nullable) to-one joins, so
     * eagerly fetching both here unconditionally, rather than adding a
     * second, differently-scoped lookup method, mirrors findAll()'s own
     * "just always fetch it" choice.
     */
    @Override
    @EntityGraph(attributePaths = {"role", "organization"})
    Optional<User> findById(Long id);
}
