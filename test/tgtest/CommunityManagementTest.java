package tgtest;

import java.io.IOException;

import tg.api.Api;
import tg.api.ChatInfo;
import tg.api.ChatAdminRightsDef;
import tg.api.ChatParticipant;
import tg.api.ChatParticipantPage;
import tg.api.InviteLink;
import tg.api.InviteResult;
import tg.api.Peer;
import tg.api.PeerCache;
import tg.api.Requests;
import tg.mt.RpcError;
import tg.tl.TlObj;
import tg.tl.TlReader;

/** Wire shapes, capability matrix and bounded participant parsing. */
public final class CommunityManagementTest implements Test
{
    public String name() { return "community/management-model-wire"; }

    public void run() throws Exception
    {
        requestWire();
        inviteLinks();
        channelCapabilities();
        basicCapabilities();
        participantParsing();
        partialInvite();
        inviteRequestSignal();
    }

    private static void requestWire() throws Exception
    {
        Peer channel = channel();
        Peer user = user();

        TlReader members = new TlReader(Requests.getParticipants(channel,
                ChatParticipantPage.MEMBERS, 20, 10));
        Assert.equal("participants method", Api.CHANNELS_GET_PARTICIPANTS,
                members.readInt());
        assertInputChannel(members);
        Assert.equal("recent filter", Api.CHANNEL_PARTICIPANTS_RECENT,
                members.readInt());
        Assert.equal("participant offset", 20, members.readInt());
        Assert.equal("participant limit", 10, members.readInt());
        Assert.equal("participant hash", 0L, members.readLong());

        TlReader exact = new TlReader(Requests.getParticipant(channel, user));
        Assert.equal("exact participant method", Api.CHANNELS_GET_PARTICIPANT,
                exact.readInt());
        assertInputChannel(exact);
        Assert.equal("exact target peer", Api.INPUT_PEER_USER, exact.readInt());
        Assert.equal("exact target id", 11L, exact.readLong());
        Assert.equal("exact target hash", 22L, exact.readLong());

        TlReader removed = new TlReader(Requests.getParticipants(channel,
                ChatParticipantPage.REMOVED, 0, 20));
        removed.readInt();
        assertInputChannel(removed);
        Assert.equal("removed filter", Api.CHANNEL_PARTICIPANTS_KICKED,
                removed.readInt());
        Assert.equal("removed query", "", removed.readString());

        Peer basic = new Peer(Peer.CHAT, 55);
        TlReader add = new TlReader(Requests.addChatUser(basic, user));
        Assert.equal("basic invite method", Api.MESSAGES_ADD_CHAT_USER,
                add.readInt());
        Assert.equal("basic invite id", 55L, add.readLong());
        assertInputUser(add);
        Assert.equal("no forwarded history", 0, add.readInt());

        TlReader delete = new TlReader(Requests.deleteChatUser(basic, user));
        Assert.equal("basic kick method", Api.MESSAGES_DELETE_CHAT_USER,
                delete.readInt());
        Assert.equal("basic kick flags", 0, delete.readInt());
        Assert.equal("basic kick id", 55L, delete.readLong());
        assertInputUser(delete);

        TlReader invite = new TlReader(Requests.inviteToChannel(channel, user));
        Assert.equal("channel invite method", Api.CHANNELS_INVITE_TO_CHANNEL,
                invite.readInt());
        assertInputChannel(invite);
        Assert.equal("single invite vector", 1, invite.readVectorCount());
        assertInputUser(invite);

        TlReader ban = new TlReader(Requests.editBanned(channel, user, true));
        Assert.equal("ban method", Api.CHANNELS_EDIT_BANNED, ban.readInt());
        assertInputChannel(ban);
        Assert.equal("ban target", Api.INPUT_PEER_USER, ban.readInt());
        Assert.equal("ban target id", 11L, ban.readLong());
        Assert.equal("ban target hash", 22L, ban.readLong());
        Assert.equal("banned rights ctor", Api.CHAT_BANNED_RIGHTS,
                ban.readInt());
        Assert.equal("ban view_messages", 1, ban.readInt());
        Assert.equal("ban forever", 0, ban.readInt());

        TlReader unban = new TlReader(Requests.editBanned(channel, user, false));
        unban.readInt();
        assertInputChannel(unban);
        unban.readInt(); unban.readLong(); unban.readLong(); unban.readInt();
        Assert.equal("empty rights kick/unban", 0, unban.readInt());
        Assert.equal("empty rights until", 0, unban.readInt());

        TlReader join = new TlReader(Requests.joinChannel(channel));
        Assert.equal("join method", Api.CHANNELS_JOIN_CHANNEL, join.readInt());
        assertInputChannel(join);
        TlReader leave = new TlReader(Requests.leaveChannel(channel));
        Assert.equal("leave method", Api.CHANNELS_LEAVE_CHANNEL, leave.readInt());
        assertInputChannel(leave);

        TlReader check = new TlReader(Requests.checkChatInvite("Ab_1-x"));
        Assert.equal("check invite method", Api.MESSAGES_CHECK_CHAT_INVITE,
                check.readInt());
        Assert.equal("check invite hash", "Ab_1-x", check.readString());
        TlReader imported = new TlReader(Requests.importChatInvite("Ab_1-x"));
        Assert.equal("import invite method", Api.MESSAGES_IMPORT_CHAT_INVITE,
                imported.readInt());
        Assert.equal("import invite hash", "Ab_1-x", imported.readString());
    }

    private static void inviteLinks() throws Exception
    {
        Assert.equal("plus link", "Hash_1-x",
                InviteLink.hash("https://t.me/+Hash_1-x"));
        Assert.equal("joinchat link", "Hash_1-x",
                InviteLink.hash("telegram.me/joinchat/Hash_1-x"));
        Assert.equal("tg link", "Hash_1-x",
                InviteLink.hash("tg://join?invite=Hash_1-x&x=1"));
        Assert.equal("fragment stripped", "Hash_1-x",
                InviteLink.hash("https://t.me/+Hash_1-x#fragment"));
        rejects("raw invite hash", "Hash_1-x");
        rejects("wrong host", "https://example.com/+Hash_1-x");
        rejects("unsafe hash", "https://t.me/+bad/value");
    }

    private static void channelCapabilities() throws Exception
    {
        TlObj admin = rights(true, true);
        ChatInfo creator = channelInfo((1 << 0) | (1 << 8), null,
                banned(false, false), banned(false, false), true, true);
        Assert.equal("creator role", ChatInfo.ROLE_CREATOR, creator.role);
        Assert.isTrue("creator views participants",
                creator.capabilities.canViewParticipants);
        Assert.isTrue("creator invites", creator.capabilities.canInvite);
        Assert.isTrue("creator kicks", creator.capabilities.canKick);
        Assert.isTrue("creator bans", creator.capabilities.canBan);
        Assert.isTrue("creator unbans", creator.capabilities.canUnban);
        Assert.isTrue("creator promotes", creator.capabilities.canPromote);
        Assert.isTrue("creator edits defaults",
                creator.capabilities.canEditDefaultPermissions);
        Assert.isTrue("creator manages invite links",
                creator.capabilities.canManageInviteLinks);
        Assert.isTrue("creator manages join requests",
                creator.capabilities.canManageJoinRequests);
        Assert.isFalse("creator cannot leave", creator.capabilities.canLeave);

        ChatInfo administrator = channelInfo(1 << 8, admin,
                banned(false, false), banned(false, false), true, true);
        Assert.equal("admin role", ChatInfo.ROLE_ADMIN, administrator.role);
        Assert.isTrue("admin invite", administrator.capabilities.canInvite);
        Assert.isTrue("admin ban", administrator.capabilities.canBan);
        Assert.isTrue("ban right edits defaults",
                administrator.capabilities.canEditDefaultPermissions);
        Assert.isTrue("invite right manages links",
                administrator.capabilities.canManageInviteLinks);
        Assert.isFalse("admin without add_admins cannot promote",
                administrator.capabilities.canPromote);
        Assert.isTrue("admin leave", administrator.capabilities.canLeave);

        ChatInfo member = channelInfo(1 << 8, null,
                banned(false, false), banned(false, false), true, true);
        Assert.equal("member role", ChatInfo.ROLE_MEMBER, member.role);
        Assert.isTrue("default allows member invite",
                member.capabilities.canInvite);
        Assert.isFalse("member cannot kick", member.capabilities.canKick);
        Assert.isFalse("member cannot edit defaults",
                member.capabilities.canEditDefaultPermissions);

        ChatInfo restricted = channelInfo(1 << 8, null,
                banned(false, true), banned(false, false), true, true);
        Assert.isFalse("default invite ban wins",
                restricted.capabilities.canInvite);

        ChatInfo broadcast = channelInfo(1 << 5, null,
                banned(false, false), banned(false, false), true, true);
        Assert.equal("broadcast type", ChatInfo.BROADCAST, broadcast.type);
        Assert.isFalse("ordinary broadcast member cannot invite",
                broadcast.capabilities.canInvite);

        ChatInfo publicLeft = channelInfo((1 << 2) | (1 << 8), null,
                banned(false, false), banned(false, false), true, true);
        Assert.isTrue("left public supergroup can join",
                publicLeft.capabilities.canJoin);
        Assert.isFalse("left account cannot leave",
                publicLeft.capabilities.canLeave);

        ChatInfo bannedLeft = channelInfo((1 << 2) | (1 << 8), null,
                banned(false, false), banned(true, false), true, true);
        Assert.isFalse("banned account cannot join",
                bannedLeft.capabilities.canJoin);
    }

    private static void basicCapabilities() throws Exception
    {
        TlObj chat = obj(Api.CHAT, 16);
        chat.nums[Api.F_CHAT__ID] = 55;
        chat.refs[Api.F_CHAT__TITLE] = "Basic";
        chat.nums[Api.F_CHAT__PARTICIPANTS_COUNT] = 3;
        chat.refs[Api.F_CHAT__DEFAULT_BANNED_RIGHTS] = banned(false, false);
        TlObj full = obj(Api.CHAT_FULL, 21);
        full.refs[Api.F_CHAT_FULL__ABOUT] = "About";
        full.refs[Api.F_CHAT_FULL__PARTICIPANTS] = obj(Api.CHAT_PARTICIPANTS, 3);
        ChatInfo info = ChatInfo.from(fullReply(full, chat),
                new Peer(Peer.CHAT, 55), new PeerCache());
        Assert.equal("basic type", ChatInfo.BASIC_GROUP, info.type);
        Assert.isTrue("basic participant vector is visible",
                info.capabilities.canViewParticipants);
        Assert.isTrue("ordinary basic member can invite", info.capabilities.canInvite);
        Assert.isFalse("ordinary basic member cannot kick", info.capabilities.canKick);
        Assert.isFalse("basic groups do not expose ban", info.capabilities.canBan);
        Assert.isTrue("basic member can leave", info.capabilities.canLeave);
    }

    private static void participantParsing() throws Exception
    {
        PeerCache peers = new PeerCache();
        Peer one = user();
        one.title = "One";
        peers.put(one);
        TlObj regular = obj(Api.CHANNEL_PARTICIPANT, 5);
        regular.nums[Api.F_CHANNEL_PARTICIPANT__USER_ID] = one.id;
        ChatParticipant row = ChatParticipant.fromChannel(regular, peers);
        Assert.equal("regular participant id", one.id, row.peer.id);
        Assert.isTrue("regular participant is editable", row.canEdit);

        TlObj creator = obj(Api.CHANNEL_PARTICIPANT_CREATOR, 4);
        creator.nums[Api.F_CHANNEL_PARTICIPANT_CREATOR__USER_ID] = one.id;
        row = ChatParticipant.fromChannel(creator, peers);
        Assert.equal("creator participant role", ChatParticipant.CREATOR, row.role);
        Assert.isFalse("creator participant is protected", row.canEdit);

        TlObj rawRights = rights(true, true);
        rawRights.flags |= 1 << 14; // hidden post_stories
        TlObj admin = obj(Api.CHANNEL_PARTICIPANT_ADMIN, 10);
        admin.nums[Api.F_CHANNEL_PARTICIPANT_ADMIN__USER_ID] = one.id;
        admin.nums[Api.F_CHANNEL_PARTICIPANT_ADMIN__CAN_EDIT] = 1;
        admin.refs[Api.F_CHANNEL_PARTICIPANT_ADMIN__ADMIN_RIGHTS] = rawRights;
        admin.refs[Api.F_CHANNEL_PARTICIPANT_ADMIN__RANK] = "unchanged";
        row = ChatParticipant.fromChannel(admin, peers);
        Assert.equal("admin participant role", ChatParticipant.ADMIN, row.role);
        Assert.isTrue("server can_edit retained", row.canEdit);
        Assert.equal("custom rank retained", "unchanged", row.rank);
        Assert.isTrue("raw hidden admin right retained",
                (row.adminRights.rawFlags & (1 << 14)) != 0);

        TlObj peerRef = obj(Api.PEER_USER, 1);
        peerRef.nums[Api.F_PEER_USER__USER_ID] = one.id;
        TlObj removed = obj(Api.CHANNEL_PARTICIPANT_BANNED, 7);
        removed.refs[Api.F_CHANNEL_PARTICIPANT_BANNED__PEER] = peerRef;
        row = ChatParticipant.fromChannel(removed, peers);
        Assert.equal("banned participant status", ChatParticipant.REMOVED,
                row.status);

        TlObj response = obj(Api.CHANNELS_CHANNEL_PARTICIPANTS, 4);
        response.nums[Api.F_CHANNELS_CHANNEL_PARTICIPANTS__COUNT] = 3;
        response.refs[Api.F_CHANNELS_CHANNEL_PARTICIPANTS__PARTICIPANTS] =
                new TlObj[] { regular, creator };
        response.refs[Api.F_CHANNELS_CHANNEL_PARTICIPANTS__USERS] = new TlObj[0];
        response.refs[Api.F_CHANNELS_CHANNEL_PARTICIPANTS__CHATS] = new TlObj[0];
        ChatParticipantPage page = ChatParticipantPage.from(response, 0, 2, peers);
        Assert.equal("participant page rows", 2, page.participants.length);
        Assert.equal("participant page total", 3, page.total);
        Assert.isFalse("full page is not exhausted", page.exhausted);
    }

    private static void partialInvite() throws Exception
    {
        TlObj missing = obj(Api.MISSING_INVITEE, 4);
        missing.nums[Api.F_MISSING_INVITEE__USER_ID] = 11;
        missing.nums[Api.F_MISSING_INVITEE__PREMIUM_WOULD_ALLOW_INVITE] = 1;
        TlObj reply = obj(Api.MESSAGES_INVITED_USERS, 2);
        reply.refs[Api.F_MESSAGES_INVITED_USERS__MISSING_INVITEES] =
                new TlObj[] { missing };
        InviteResult result = InviteResult.from(reply);
        Assert.equal("one missing invitee", 1, result.missingCount);
        Assert.isFalse("partial invite is not complete", result.complete());
        Assert.isTrue("premium hint retained", result.premiumWouldAllow);
    }

    private static void inviteRequestSignal()
    {
        RpcError sent = new RpcError(400, "INVITE_REQUEST_SENT");
        Assert.isTrue("request-needed invite signal", sent.isInviteRequestSent());
        Assert.isFalse("different invite error is not success",
                new RpcError(400, "INVITE_HASH_INVALID").isInviteRequestSent());
        Assert.isFalse("same text with wrong code is not success",
                new RpcError(500, "INVITE_REQUEST_SENT").isInviteRequestSent());
    }

    private static ChatInfo channelInfo(int flags, TlObj admin, TlObj defaults,
                                        TlObj personal, boolean username,
                                        boolean canView) throws Exception
    {
        TlObj chat = obj(Api.CHANNEL, 41);
        chat.flags = flags;
        chat.hasFlags = true;
        chat.nums[Api.F_CHANNEL__ID] = 77;
        chat.nums[Api.F_CHANNEL__ACCESS_HASH] = 88;
        chat.refs[Api.F_CHANNEL__TITLE] = "Community";
        if ((flags & (1 << 5)) != 0) chat.nums[Api.F_CHANNEL__BROADCAST] = 1;
        if ((flags & (1 << 8)) != 0) chat.nums[Api.F_CHANNEL__MEGAGROUP] = 1;
        if ((flags & (1 << 0)) != 0) chat.nums[Api.F_CHANNEL__CREATOR] = 1;
        if ((flags & (1 << 2)) != 0) chat.nums[Api.F_CHANNEL__LEFT] = 1;
        if (username) chat.refs[Api.F_CHANNEL__USERNAME] = "publicfixture";
        chat.refs[Api.F_CHANNEL__ADMIN_RIGHTS] = admin;
        chat.refs[Api.F_CHANNEL__DEFAULT_BANNED_RIGHTS] = defaults;
        chat.refs[Api.F_CHANNEL__BANNED_RIGHTS] = personal;

        TlObj full = obj(Api.CHANNEL_FULL, 63);
        full.flags = (1 << 0) | (1 << 1) | (1 << 2) | (1 << 13)
                | (canView ? (1 << 3) : 0);
        full.hasFlags = true;
        if (canView) full.nums[Api.F_CHANNEL_FULL__CAN_VIEW_PARTICIPANTS] = 1;
        full.refs[Api.F_CHANNEL_FULL__ABOUT] = "About";
        full.nums[Api.F_CHANNEL_FULL__PARTICIPANTS_COUNT] = 2;
        full.nums[Api.F_CHANNEL_FULL__ADMINS_COUNT] = 1;
        full.nums[Api.F_CHANNEL_FULL__KICKED_COUNT] = 0;
        full.nums[Api.F_CHANNEL_FULL__BANNED_COUNT] = 0;
        full.nums[Api.F_CHANNEL_FULL__ONLINE_COUNT] = 2;
        Peer requested = channel();
        return ChatInfo.from(fullReply(full, chat), requested, new PeerCache());
    }

    private static TlObj fullReply(TlObj full, TlObj chat)
    {
        TlObj reply = obj(Api.MESSAGES_CHAT_FULL, 3);
        reply.refs[Api.F_MESSAGES_CHAT_FULL__FULL_CHAT] = full;
        reply.refs[Api.F_MESSAGES_CHAT_FULL__CHATS] = new TlObj[] { chat };
        reply.refs[Api.F_MESSAGES_CHAT_FULL__USERS] = new TlObj[0];
        return reply;
    }

    private static TlObj rights(boolean invite, boolean ban)
    {
        TlObj out = obj(Api.CHAT_ADMIN_RIGHTS, 20);
        out.hasFlags = true;
        if (invite)
        {
            out.flags |= ChatAdminRightsDef.INVITE_USERS;
            out.nums[Api.F_CHAT_ADMIN_RIGHTS__INVITE_USERS] = 1;
        }
        if (ban)
        {
            out.flags |= ChatAdminRightsDef.BAN_USERS;
            out.nums[Api.F_CHAT_ADMIN_RIGHTS__BAN_USERS] = 1;
        }
        return out;
    }

    private static TlObj banned(boolean view, boolean invite)
    {
        TlObj out = obj(Api.CHAT_BANNED_RIGHTS, 30);
        if (view) out.nums[Api.F_CHAT_BANNED_RIGHTS__VIEW_MESSAGES] = 1;
        if (invite) out.nums[Api.F_CHAT_BANNED_RIGHTS__INVITE_USERS] = 1;
        return out;
    }

    private static TlObj obj(int id, int fields)
    {
        TlObj out = new TlObj(id, fields);
        out.refs = new Object[fields];
        return out;
    }

    private static Peer channel()
    {
        Peer peer = new Peer(Peer.CHANNEL, 77);
        peer.accessHash = 88;
        peer.megagroup = true;
        return peer;
    }

    private static Peer user()
    {
        Peer peer = new Peer(Peer.USER, 11);
        peer.accessHash = 22;
        return peer;
    }

    private static void assertInputChannel(TlReader reader) throws Exception
    {
        Assert.equal("input channel ctor", Api.INPUT_CHANNEL, reader.readInt());
        Assert.equal("input channel id", 77L, reader.readLong());
        Assert.equal("input channel hash", 88L, reader.readLong());
    }

    private static void assertInputUser(TlReader reader) throws Exception
    {
        Assert.equal("input user ctor", Api.INPUT_USER, reader.readInt());
        Assert.equal("input user id", 11L, reader.readLong());
        Assert.equal("input user hash", 22L, reader.readLong());
    }

    private static void rejects(String label, String link)
    {
        try
        {
            InviteLink.hash(link);
            Assert.fail(label);
        }
        catch (IOException expected) { }
    }
}
