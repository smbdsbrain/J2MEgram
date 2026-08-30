package tg.api;

import tg.tl.TlObj;

/** One member/removed row with enough server status to gate moderation UI. */
public final class ChatParticipant
{
    public static final int MEMBER = 1;
    public static final int ADMIN = 2;
    public static final int CREATOR = 3;

    public static final int ACTIVE = 1;
    public static final int REMOVED = 2;
    public static final int LEFT = 3;

    public Peer peer;
    public int role = MEMBER;
    public int status = ACTIVE;
    public int date;
    public String rank = "";
    public boolean self;
    public boolean canEdit = true;
    public ChatAdminRightsDef adminRights = new ChatAdminRightsDef();

    public String subtitle()
    {
        if (status == REMOVED) { return "Removed"; }
        if (status == LEFT) { return "Left"; }
        if (role == CREATOR) { return "Creator"; }
        if (role == ADMIN) { return rank.length() == 0 ? "Administrator" : rank; }
        return "Member";
    }

    public static ChatParticipant fromBasic(TlObj raw, PeerCache peers)
    {
        if (raw == null) { return null; }
        ChatParticipant out = new ChatParticipant();
        long userId;
        if (raw.id == Api.CHAT_PARTICIPANT_CREATOR)
        {
            userId = raw.num(Api.F_CHAT_PARTICIPANT_CREATOR__USER_ID);
            out.role = CREATOR;
            out.rank = raw.strOrEmpty(Api.F_CHAT_PARTICIPANT_CREATOR__RANK);
            out.canEdit = false;
        }
        else if (raw.id == Api.CHAT_PARTICIPANT_ADMIN)
        {
            userId = raw.num(Api.F_CHAT_PARTICIPANT_ADMIN__USER_ID);
            out.role = ADMIN;
            out.date = raw.intAt(Api.F_CHAT_PARTICIPANT_ADMIN__DATE);
            out.rank = raw.strOrEmpty(Api.F_CHAT_PARTICIPANT_ADMIN__RANK);
        }
        else if (raw.id == Api.CHAT_PARTICIPANT)
        {
            userId = raw.num(Api.F_CHAT_PARTICIPANT__USER_ID);
            out.date = raw.intAt(Api.F_CHAT_PARTICIPANT__DATE);
            out.rank = raw.strOrEmpty(Api.F_CHAT_PARTICIPANT__RANK);
        }
        else { return null; }
        out.peer = peers.get(Peer.USER, userId);
        if (out.peer == null) { out.peer = new Peer(Peer.USER, userId); }
        out.self = out.peer.self;
        if (out.self) { out.canEdit = false; }
        return out;
    }

    public static ChatParticipant fromChannel(TlObj raw, PeerCache peers)
    {
        if (raw == null) { return null; }
        ChatParticipant out = new ChatParticipant();
        Peer reference = null;
        if (raw.id == Api.CHANNEL_PARTICIPANT)
        {
            reference = new Peer(Peer.USER,
                    raw.num(Api.F_CHANNEL_PARTICIPANT__USER_ID));
            out.date = raw.intAt(Api.F_CHANNEL_PARTICIPANT__DATE);
            out.rank = raw.strOrEmpty(Api.F_CHANNEL_PARTICIPANT__RANK);
        }
        else if (raw.id == Api.CHANNEL_PARTICIPANT_SELF)
        {
            reference = new Peer(Peer.USER,
                    raw.num(Api.F_CHANNEL_PARTICIPANT_SELF__USER_ID));
            out.date = raw.intAt(Api.F_CHANNEL_PARTICIPANT_SELF__DATE);
            out.rank = raw.strOrEmpty(Api.F_CHANNEL_PARTICIPANT_SELF__RANK);
            out.self = true;
            out.canEdit = false;
        }
        else if (raw.id == Api.CHANNEL_PARTICIPANT_CREATOR)
        {
            reference = new Peer(Peer.USER,
                    raw.num(Api.F_CHANNEL_PARTICIPANT_CREATOR__USER_ID));
            out.role = CREATOR;
            out.rank = raw.strOrEmpty(Api.F_CHANNEL_PARTICIPANT_CREATOR__RANK);
            out.adminRights = ChatAdminRightsDef.from(raw.obj(
                    Api.F_CHANNEL_PARTICIPANT_CREATOR__ADMIN_RIGHTS));
            out.canEdit = false;
        }
        else if (raw.id == Api.CHANNEL_PARTICIPANT_ADMIN)
        {
            reference = new Peer(Peer.USER,
                    raw.num(Api.F_CHANNEL_PARTICIPANT_ADMIN__USER_ID));
            out.role = ADMIN;
            out.date = raw.intAt(Api.F_CHANNEL_PARTICIPANT_ADMIN__DATE);
            out.rank = raw.strOrEmpty(Api.F_CHANNEL_PARTICIPANT_ADMIN__RANK);
            out.adminRights = ChatAdminRightsDef.from(raw.obj(
                    Api.F_CHANNEL_PARTICIPANT_ADMIN__ADMIN_RIGHTS));
            out.self = raw.num(Api.F_CHANNEL_PARTICIPANT_ADMIN__SELF) != 0;
            out.canEdit = raw.num(Api.F_CHANNEL_PARTICIPANT_ADMIN__CAN_EDIT) != 0;
        }
        else if (raw.id == Api.CHANNEL_PARTICIPANT_BANNED)
        {
            reference = Peer.fromPeerObj(raw.obj(
                    Api.F_CHANNEL_PARTICIPANT_BANNED__PEER));
            out.status = REMOVED;
            out.date = raw.intAt(Api.F_CHANNEL_PARTICIPANT_BANNED__DATE);
            out.rank = raw.strOrEmpty(Api.F_CHANNEL_PARTICIPANT_BANNED__RANK);
        }
        else if (raw.id == Api.CHANNEL_PARTICIPANT_LEFT)
        {
            reference = Peer.fromPeerObj(raw.obj(
                    Api.F_CHANNEL_PARTICIPANT_LEFT__PEER));
            out.status = LEFT;
        }
        else { return null; }
        out.peer = peers.resolve(reference);
        if (out.peer == null) { out.peer = reference; }
        if (out.peer != null && out.peer.self) { out.self = true; }
        if (out.self) { out.canEdit = false; }
        return out;
    }
}
