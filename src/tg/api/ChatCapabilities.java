package tg.api;

/** Server-derived actions that are safe to offer for one chat snapshot. */
public final class ChatCapabilities
{
    public boolean canViewParticipants;
    public boolean canInvite;
    public boolean canKick;
    public boolean canBan;
    public boolean canUnban;
    public boolean canLeave;
    public boolean canJoin;
    public boolean canPromote;
    public boolean canEditDefaultPermissions;
    public boolean canManageInviteLinks;
    public boolean canManageJoinRequests;
    public boolean canCreateTopics;
    public boolean canManageTopics;
}
