package it.pruefert.discordmodule.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Per-server Discord configuration: which guild and which channels are mapped
 * to which Minecraft server events.
 */
@Document(collection = "server_discord_configs")
public class ServerDiscordConfig {

    @Id
    private String serverId; // Aethera server MongoDB ID

    private String guildId;   // Discord guild (server) ID
    private String guildName; // Cached guild name

    private ChannelConfig playerChat       = new ChannelConfig();
    private ChannelConfig playerEvents     = new ChannelConfig();
    private ChannelConfig whitelistRequests = new ChannelConfig();

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    public static class ChannelConfig {
        private boolean enabled   = false;
        private String  channelId = null;
        /** Discord role ID required to approve whitelist requests (whitelist channel only). */
        private String  requiredRoleId = null;

        public boolean isEnabled()                { return enabled; }
        public void    setEnabled(boolean e)      { this.enabled = e; }
        public String  getChannelId()             { return channelId; }
        public void    setChannelId(String id)    { this.channelId = id; }
        public String  getRequiredRoleId()        { return requiredRoleId; }
        public void    setRequiredRoleId(String r){ this.requiredRoleId = r; }
    }

    // -----------------------------------------------------------------------
    // Getters / Setters
    // -----------------------------------------------------------------------

    public String getServerId()                            { return serverId; }
    public void   setServerId(String id)                   { this.serverId = id; }
    public String getGuildId()                             { return guildId; }
    public void   setGuildId(String id)                    { this.guildId = id; }
    public String getGuildName()                           { return guildName; }
    public void   setGuildName(String name)                { this.guildName = name; }
    public ChannelConfig getPlayerChat()                   { return playerChat; }
    public void          setPlayerChat(ChannelConfig c)    { this.playerChat = c; }
    public ChannelConfig getPlayerEvents()                 { return playerEvents; }
    public void          setPlayerEvents(ChannelConfig c)  { this.playerEvents = c; }
    public ChannelConfig getWhitelistRequests()            { return whitelistRequests; }
    public void          setWhitelistRequests(ChannelConfig c) { this.whitelistRequests = c; }
}
