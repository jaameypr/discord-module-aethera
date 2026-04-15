package it.pruefert.discordmodule.dto;

/**
 * DTO mirroring {@link it.pruefert.discordmodule.model.ServerDiscordConfig}.
 * Used for GET and PUT /api/servers/{serverId}/config
 */
public class ServerDiscordConfigDto {

    private String guildId;
    private String guildName;

    private ChannelConfigDto playerChat        = new ChannelConfigDto();
    private ChannelConfigDto playerEvents      = new ChannelConfigDto();
    private ChannelConfigDto whitelistRequests = new ChannelConfigDto();
    private ChannelConfigDto serverEvents      = new ChannelConfigDto();

    public static class ChannelConfigDto {
        private boolean enabled       = false;
        private String  channelId     = null;
        private String  requiredRoleId = null;

        public boolean isEnabled()                { return enabled; }
        public void    setEnabled(boolean e)      { this.enabled = e; }
        public String  getChannelId()             { return channelId; }
        public void    setChannelId(String id)    { this.channelId = id; }
        public String  getRequiredRoleId()        { return requiredRoleId; }
        public void    setRequiredRoleId(String r){ this.requiredRoleId = r; }
    }

    public String            getGuildId()                              { return guildId; }
    public void              setGuildId(String id)                     { this.guildId = id; }
    public String            getGuildName()                            { return guildName; }
    public void              setGuildName(String n)                    { this.guildName = n; }
    public ChannelConfigDto  getPlayerChat()                           { return playerChat; }
    public void              setPlayerChat(ChannelConfigDto c)         { this.playerChat = c; }
    public ChannelConfigDto  getPlayerEvents()                         { return playerEvents; }
    public void              setPlayerEvents(ChannelConfigDto c)       { this.playerEvents = c; }
    public ChannelConfigDto  getWhitelistRequests()                    { return whitelistRequests; }
    public void              setWhitelistRequests(ChannelConfigDto c)  { this.whitelistRequests = c; }
    public ChannelConfigDto  getServerEvents()                         { return serverEvents; }
    public void              setServerEvents(ChannelConfigDto c)       { this.serverEvents = c; }
}
