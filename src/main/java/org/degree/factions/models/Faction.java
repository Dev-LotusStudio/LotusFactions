package org.degree.factions.models;

import java.sql.Timestamp;

public class Faction {
    private int id;
    private String name;
    private String leaderUuid;
    private String leaderName;
    private Timestamp creationDate;
    private String colorHex;
    private boolean discordRoleSyncEnabled;

    public Faction() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLeaderUuid() { return leaderUuid; }
    public void setLeaderUuid(String leaderUuid) { this.leaderUuid = leaderUuid; }

    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }

    public Timestamp getCreationDate() { return creationDate; }
    public void setCreationDate(Timestamp creationDate) { this.creationDate = creationDate; }

    public String getColorHex() { return colorHex; }
    public void setColorHex(String colorHex) {
        if (colorHex == null || !colorHex.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new IllegalArgumentException("Invalid hex color: " + colorHex);
        }
        this.colorHex = colorHex;
    }

    public boolean isDiscordRoleSyncEnabled() { return discordRoleSyncEnabled; }
    public void setDiscordRoleSyncEnabled(boolean discordRoleSyncEnabled) {
        this.discordRoleSyncEnabled = discordRoleSyncEnabled;
    }
}
