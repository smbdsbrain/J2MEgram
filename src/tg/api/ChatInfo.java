package tg.api;

import java.io.IOException;

import tg.tl.TlObj;

/** Bounded, UI-ready full information and current-account rights for a chat. */
public final class ChatInfo
{
    public static final int BASIC_GROUP = 1;
    public static final int SUPERGROUP = 2;
    public static final int BROADCAST = 3;
    public static final int FORUM = 4;

    public static final int ROLE_LEFT = 0;
    public static final int ROLE_MEMBER = 1;
    public static final int ROLE_ADMIN = 2;
    public static final int ROLE_CREATOR = 3;

    public Peer peer;
    public int type;
    public int role;
    public String about = "";
    public int participantsCount = -1;
    public int adminsCount = -1;
    public int removedCount = -1;
    public int bannedCount = -1;
    public int onlineCount = -1;
    public boolean left;
    public boolean creator;
    public int pendingJoinRequests = -1;
    public ChatAdminRightsDef adminRights = new ChatAdminRightsDef();
    public ChatDefaultPermissions defaultPermissions =
            new ChatDefaultPermissions();
    public ChatDefaultPermissions personalPermissions =
            new ChatDefaultPermissions();
    public ExportedInviteLink primaryInvite;
    public ChatCapabilities capabilities = new ChatCapabilities();

    public String typeLabel()
    {
        switch (type)
        {
            case BASIC_GROUP: return "Basic group";
            case SUPERGROUP: return "Supergroup";
            case BROADCAST: return "Channel";
            case FORUM: return "Forum";
            default: return "Community";
        }
    }

    public String roleLabel()
    {
        switch (role)
        {
            case ROLE_CREATOR: return "Creator";
            case ROLE_ADMIN: return "Administrator";
            case ROLE_MEMBER: return "Member";
            default: return "Not joined";
        }
    }

    public static ChatInfo from(TlObj reply, Peer requested, PeerCache peers)
            throws IOException
    {
        if (reply == null || reply.id != Api.MESSAGES_CHAT_FULL)
        {
            throw new IOException("unexpected full chat reply");
        }
        TlObj[] chats = reply.vec(Api.F_MESSAGES_CHAT_FULL__CHATS);
        peers.absorb(reply.vec(Api.F_MESSAGES_CHAT_FULL__USERS), chats);
        Peer resolved = peers.resolve(requested);
        TlObj chat = findChat(chats, requested);
        TlObj full = reply.obj(Api.F_MESSAGES_CHAT_FULL__FULL_CHAT);
        if (chat == null || full == null)
        {
            throw new IOException("full chat reply omitted the requested chat");
        }

        ChatInfo out = new ChatInfo();
        out.peer = resolved;
        boolean basic = chat.id == Api.CHAT;
        if (basic)
        {
            out.type = BASIC_GROUP;
            out.about = full.strOrEmpty(Api.F_CHAT_FULL__ABOUT);
            out.participantsCount = chat.intAt(Api.F_CHAT__PARTICIPANTS_COUNT);
            if (full.flag(17))
            {
                out.pendingJoinRequests = full.intAt(
                        Api.F_CHAT_FULL__REQUESTS_PENDING);
            }
            if (full.flag(13))
            {
                out.primaryInvite = ExportedInviteLink.from(full.obj(
                        Api.F_CHAT_FULL__EXPORTED_INVITE));
            }
        }
        else if (chat.id == Api.CHANNEL)
        {
            out.type = resolved != null && resolved.forum ? FORUM
                    : (resolved != null && resolved.megagroup
                            ? SUPERGROUP : BROADCAST);
            out.about = full.strOrEmpty(Api.F_CHANNEL_FULL__ABOUT);
            if (full.flag(0))
            {
                out.participantsCount = full.intAt(
                        Api.F_CHANNEL_FULL__PARTICIPANTS_COUNT);
            }
            if (full.flag(1))
            {
                out.adminsCount = full.intAt(Api.F_CHANNEL_FULL__ADMINS_COUNT);
            }
            if (full.flag(2))
            {
                out.removedCount = full.intAt(Api.F_CHANNEL_FULL__KICKED_COUNT);
                out.bannedCount = full.intAt(Api.F_CHANNEL_FULL__BANNED_COUNT);
            }
            if (full.flag(13))
            {
                out.onlineCount = full.intAt(Api.F_CHANNEL_FULL__ONLINE_COUNT);
            }
            if (full.flag(28))
            {
                out.pendingJoinRequests = full.intAt(
                        Api.F_CHANNEL_FULL__REQUESTS_PENDING);
            }
            if (full.flag(23))
            {
                out.primaryInvite = ExportedInviteLink.from(full.obj(
                        Api.F_CHANNEL_FULL__EXPORTED_INVITE));
            }
        }
        else
        {
            throw new IOException("unsupported full chat constructor");
        }

        out.creator = chat.num(basic ? Api.F_CHAT__CREATOR
                : Api.F_CHANNEL__CREATOR) != 0;
        out.left = chat.num(basic ? Api.F_CHAT__LEFT
                : Api.F_CHANNEL__LEFT) != 0;
        TlObj admin = chat.obj(basic ? Api.F_CHAT__ADMIN_RIGHTS
                : Api.F_CHANNEL__ADMIN_RIGHTS);
        TlObj personal = basic ? null
                : chat.obj(Api.F_CHANNEL__BANNED_RIGHTS);
        TlObj defaults = chat.obj(basic
                ? Api.F_CHAT__DEFAULT_BANNED_RIGHTS
                : Api.F_CHANNEL__DEFAULT_BANNED_RIGHTS);

        out.adminRights = ChatAdminRightsDef.from(admin);
        out.defaultPermissions = ChatDefaultPermissions.from(defaults);
        out.personalPermissions = ChatDefaultPermissions.from(personal);

        out.role = out.left ? ROLE_LEFT : (out.creator ? ROLE_CREATOR
                : (admin != null ? ROLE_ADMIN : ROLE_MEMBER));
        boolean member = !out.left;
        boolean adminInvite = admin != null
                && admin.num(Api.F_CHAT_ADMIN_RIGHTS__INVITE_USERS) != 0;
        boolean adminBan = admin != null
                && admin.num(Api.F_CHAT_ADMIN_RIGHTS__BAN_USERS) != 0;
        boolean defaultInviteBanned = defaults != null
                && defaults.num(Api.F_CHAT_BANNED_RIGHTS__INVITE_USERS) != 0;
        boolean personalInviteBanned = personal != null
                && personal.num(Api.F_CHAT_BANNED_RIGHTS__INVITE_USERS) != 0;
        boolean personallyBanned = personal != null
                && personal.num(Api.F_CHAT_BANNED_RIGHTS__VIEW_MESSAGES) != 0;

        ChatCapabilities cap = out.capabilities;
        if (basic)
        {
            TlObj participants = full.obj(Api.F_CHAT_FULL__PARTICIPANTS);
            cap.canViewParticipants = participants != null
                    && participants.id == Api.CHAT_PARTICIPANTS;
        }
        else
        {
            cap.canViewParticipants = full.num(
                    Api.F_CHANNEL_FULL__CAN_VIEW_PARTICIPANTS) != 0;
        }
        boolean ordinaryInvite = member && out.type != BROADCAST
                && !defaultInviteBanned && !personalInviteBanned;
        cap.canInvite = member && (out.creator || adminInvite || ordinaryInvite);
        cap.canKick = member && (out.creator || adminBan);
        cap.canBan = !basic && cap.canKick;
        cap.canUnban = !basic && cap.canKick;
        cap.canLeave = member && !out.creator;
        cap.canJoin = !basic && out.left && !personallyBanned
                && resolved != null && resolved.username != null
                && resolved.username.length() > 0;
        cap.canPromote = member && (out.creator || out.adminRights.addAdmins);
        cap.canEditDefaultPermissions = member
                && (out.creator || out.adminRights.banUsers);
        cap.canManageInviteLinks = member
                && (out.creator || out.adminRights.inviteUsers);
        cap.canManageJoinRequests = cap.canManageInviteLinks;
        cap.canManageTopics = out.type == FORUM && member
                && (out.creator || out.adminRights.manageTopics);
        cap.canCreateTopics = out.type == FORUM && member
                && (cap.canManageTopics
                || (out.defaultPermissions.manageTopics
                        && out.personalPermissions.manageTopics));
        return out;
    }

    private static TlObj findChat(TlObj[] chats, Peer requested)
    {
        if (requested == null) { return null; }
        for (int i = 0; i < chats.length; i++)
        {
            TlObj chat = chats[i];
            if (chat == null) { continue; }
            if (requested.kind == Peer.CHAT && chat.id == Api.CHAT
                    && chat.num(Api.F_CHAT__ID) == requested.id)
            {
                return chat;
            }
            if (requested.kind == Peer.CHANNEL && chat.id == Api.CHANNEL
                    && chat.num(Api.F_CHANNEL__ID) == requested.id)
            {
                return chat;
            }
        }
        return null;
    }
}
