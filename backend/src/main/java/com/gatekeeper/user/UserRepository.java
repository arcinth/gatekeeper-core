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
}
