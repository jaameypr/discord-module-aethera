package it.pruefert.discordmodule.repository;

import it.pruefert.discordmodule.model.WhitelistRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WhitelistRequestRepository
        extends MongoRepository<WhitelistRequest, String> {

    List<WhitelistRequest> findByServerIdAndProcessedFalseOrderByCreatedAtDesc(String serverId);

    List<WhitelistRequest> findByProcessedFalse();
}
