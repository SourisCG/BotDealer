package souris.jarvisdealer.service;

import org.springframework.stereotype.Service;

import souris.jarvisdealer.repository.TokensDiscordRepository;

@Service
public class TokensDiscordService {
    private final TokensDiscordRepository repository;

    public TokensDiscordService(TokensDiscordRepository repository) {
        this.repository = repository;
    }
}
