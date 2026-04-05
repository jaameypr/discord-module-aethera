package it.pruefert.discordmodule.repository;

import it.pruefert.discordmodule.model.ServerDiscordConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServerDiscordConfigRepository
        extends MongoRepository<ServerDiscordConfig, String> {
}
