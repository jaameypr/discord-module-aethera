package it.pruefert.discordmodule.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A pending whitelist-request event triggered by a player trying to join
 * while the server has whitelist enabled.
 */
@Document(collection = "whitelist_requests")
public class WhitelistRequest {

    @Id
    private String id;

    private String serverId;
    private String playerName;
    private String playerUuid;   // may be null if UUID not found in logs
    private String skinUrl;      // crafatar URL, set after lookup

    /** Discord message ID that contains the approval button. */
    private String discordMessageId;
    /** Channel where the message was posted. */
    private String channelId;
    /** Whether the request has been acted upon (approved). */
    private boolean processed = false;
    /** Username of the Discord user who approved (for audit). */
    private String approvedBy;
    /** Required Discord role ID to approve this request (may be null). */
    private String requiredRoleId;

    @CreatedDate
    private Instant createdAt;

    // Getters / Setters

    public String  getId()                       { return id; }
    public void    setId(String id)              { this.id = id; }
    public String  getServerId()                 { return serverId; }
    public void    setServerId(String s)         { this.serverId = s; }
    public String  getPlayerName()               { return playerName; }
    public void    setPlayerName(String n)       { this.playerName = n; }
    public String  getPlayerUuid()               { return playerUuid; }
    public void    setPlayerUuid(String u)       { this.playerUuid = u; }
    public String  getSkinUrl()                  { return skinUrl; }
    public void    setSkinUrl(String u)          { this.skinUrl = u; }
    public String  getDiscordMessageId()         { return discordMessageId; }
    public void    setDiscordMessageId(String m) { this.discordMessageId = m; }
    public String  getChannelId()                { return channelId; }
    public void    setChannelId(String c)        { this.channelId = c; }
    public boolean isProcessed()                 { return processed; }
    public void    setProcessed(boolean p)       { this.processed = p; }
    public String  getApprovedBy()               { return approvedBy; }
    public void    setApprovedBy(String a)       { this.approvedBy = a; }
    public String  getRequiredRoleId()           { return requiredRoleId; }
    public void    setRequiredRoleId(String r)   { this.requiredRoleId = r; }
    public Instant getCreatedAt()                { return createdAt; }
    public void    setCreatedAt(Instant t)       { this.createdAt = t; }
}
