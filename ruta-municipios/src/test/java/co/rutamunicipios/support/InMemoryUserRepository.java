package co.rutamunicipios.support;

import co.rutamunicipios.application.port.out.UserRepository;
import co.rutamunicipios.domain.model.Email;
import co.rutamunicipios.domain.model.User;
import co.rutamunicipios.domain.model.UserId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Doble de prueba (fake) del puerto UserRepository: gracias a la arquitectura hexagonal no hace falta disco. */
public final class InMemoryUserRepository implements UserRepository {
    public final Map<String, User> data = new HashMap<>();

    @Override public void save(User user) { data.put(user.id().value(), user); }
    @Override public Optional<User> findById(UserId id) { return Optional.ofNullable(data.get(id.value())); }
    @Override public Optional<User> findByEmail(Email email) {
        return data.values().stream().filter(u -> u.email().equals(email)).findFirst();
    }
    @Override public boolean existsByEmail(Email email) { return findByEmail(email).isPresent(); }
}
