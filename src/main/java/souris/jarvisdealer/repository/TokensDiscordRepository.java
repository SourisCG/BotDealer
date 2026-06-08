package souris.jarvisdealer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import souris.jarvisdealer.model.TokensDiscord;

@Repository
public interface TokensDiscordRepository extends JpaRepository<TokensDiscord, Long> {

}
