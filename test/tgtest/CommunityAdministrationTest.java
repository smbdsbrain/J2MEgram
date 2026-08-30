package tgtest;

import tg.api.Api;
import tg.api.ChatAdminRightsDef;
import tg.api.ChatDefaultPermissions;
import tg.api.ExportedInviteLink;
import tg.api.ExportedInviteLinkPage;
import tg.api.JoinRequest;
import tg.api.JoinRequestPage;
import tg.api.Peer;
import tg.api.PeerCache;
import tg.api.Requests;
import tg.tl.TlObj;
import tg.tl.TlParser;
import tg.tl.TlReader;
import tg.tl.TlWriter;

/** Layer-225 rights preservation and P3 administration wire shapes. */
public final class CommunityAdministrationTest implements Test
{
    public String name() { return "community/advanced-admin-wire"; }

    public void run() throws Exception
    {
        hiddenAdminRightsSurviveVisibleEdit();
        defaultPermissionsNormalizeLegacyUmbrellas();
        adminAndPermissionRequests();
        inviteAndJoinRequestWire();
        sparseAdministrationPages();
        forumAdministrationWire();
    }

    private static void hiddenAdminRightsSurviveVisibleEdit() throws Exception
    {
        int hidden = ChatAdminRightsDef.POST_STORIES
                | ChatAdminRightsDef.MANAGE_DIRECT_MESSAGES
                | ChatAdminRightsDef.MANAGE_RANKS;
        TlWriter w = new TlWriter(16);
        w.writeInt(Api.CHAT_ADMIN_RIGHTS);
        w.writeInt(hidden | ChatAdminRightsDef.INVITE_USERS);
        ChatAdminRightsDef rights = ChatAdminRightsDef.from(
                TlParser.parse(new TlReader(w.toByteArray())));
        Assert.isTrue("visible invite parsed", rights.inviteUsers);
        rights.inviteUsers = false;
        rights.deleteMessages = true;
        Assert.equal("hidden rights preserved", hidden,
                rights.flags() & hidden);
        Assert.isTrue("new visible right serialized",
                (rights.flags() & ChatAdminRightsDef.DELETE_MESSAGES) != 0);
        Assert.isFalse("cleared visible right serialized",
                (rights.flags() & ChatAdminRightsDef.INVITE_USERS) != 0);

        ChatAdminRightsDef demoted = new ChatAdminRightsDef();
        Assert.equal("intentional demote is empty", 0, demoted.flags());
    }

    private static void defaultPermissionsNormalizeLegacyUmbrellas()
            throws Exception
    {
        int legacySendMessages = 1 << 1;
        int hiddenEditRank = 1 << 26;
        TlWriter w = new TlWriter(20);
        w.writeInt(Api.CHAT_BANNED_RIGHTS);
        w.writeInt(legacySendMessages | hiddenEditRank);
        w.writeInt(123);
        ChatDefaultPermissions permissions = ChatDefaultPermissions.from(
                TlParser.parse(new TlReader(w.toByteArray())));
        Assert.isFalse("legacy umbrella bans text", permissions.sendText);
        Assert.isFalse("legacy umbrella bans media", permissions.sendMedia);
        Assert.isFalse("legacy umbrella bans stickers",
                permissions.sendStickers);
        int normalized = permissions.bannedFlags();
        Assert.isFalse("legacy umbrella removed", (normalized & (1 << 1)) != 0);
        Assert.isTrue("hidden banned flag retained",
                (normalized & hiddenEditRank) != 0);
        Assert.isTrue("modern plain-text bit emitted",
                (normalized & (1 << 25)) != 0);
        Assert.isTrue("modern photo bit emitted",
                (normalized & (1 << 19)) != 0);
    }

    private static void adminAndPermissionRequests() throws Exception
    {
        Peer channel = channel();
        Peer user = user();
        ChatAdminRightsDef rights = new ChatAdminRightsDef();
        rights.rawFlags = ChatAdminRightsDef.POST_STORIES;
        rights.deleteMessages = true;

        TlReader r = new TlReader(Requests.editChannelAdmin(channel, user,
                rights, "unchanged"));
        Assert.equal("edit admin method", Api.CHANNELS_EDIT_ADMIN, r.readInt());
        Assert.equal("rank present", 1, r.readInt());
        assertInputChannel(r);
        assertInputUser(r);
        Assert.equal("admin rights ctor", Api.CHAT_ADMIN_RIGHTS, r.readInt());
        Assert.equal("full raw rights", rights.flags(), r.readInt());
        Assert.equal("rank preserved", "unchanged", r.readString());

        Peer basic = new Peer(Peer.CHAT, 5);
        r = new TlReader(Requests.editBasicChatAdmin(basic, user, true));
        Assert.equal("basic edit admin", Api.MESSAGES_EDIT_CHAT_ADMIN,
                r.readInt());
        Assert.equal("basic chat id", 5L, r.readLong());
        assertInputUser(r);
        Assert.equal("basic bool true", Api.BOOL_TRUE, r.readInt());

        ChatDefaultPermissions permissions = new ChatDefaultPermissions();
        permissions.sendMedia = false;
        permissions.manageTopics = false;
        r = new TlReader(Requests.editDefaultPermissions(channel, permissions));
        Assert.equal("default permissions method",
                Api.MESSAGES_EDIT_CHAT_DEFAULT_BANNED_RIGHTS, r.readInt());
        assertInputPeerChannel(r);
        Assert.equal("default banned ctor", Api.CHAT_BANNED_RIGHTS, r.readInt());
        int flags = r.readInt();
        Assert.isTrue("media group inverted", (flags & (1 << 19)) != 0);
        Assert.isTrue("topics inverted", (flags & (1 << 18)) != 0);
        Assert.equal("permissions forever", 0, r.readInt());
    }

    private static void inviteAndJoinRequestWire() throws Exception
    {
        Peer channel = channel();
        TlReader r = new TlReader(Requests.exportChatInvite(channel, "Mods",
                true));
        Assert.equal("export invite", Api.MESSAGES_EXPORT_CHAT_INVITE,
                r.readInt());
        Assert.equal("request+title flags", (1 << 3) | (1 << 4), r.readInt());
        assertInputPeerChannel(r);
        Assert.equal("invite title", "Mods", r.readString());

        ExportedInviteLink offset = new ExportedInviteLink();
        offset.date = 77;
        offset.link = "https://t.me/+one";
        r = new TlReader(Requests.getExportedChatInvites(channel, offset, 20));
        Assert.equal("get invites", Api.MESSAGES_GET_EXPORTED_CHAT_INVITES,
                r.readInt());
        Assert.equal("invite offset present", 1 << 2, r.readInt());
        assertInputPeerChannel(r);
        Assert.equal("current admin self", Api.INPUT_USER_SELF, r.readInt());
        Assert.equal("invite offset date", 77, r.readInt());
        Assert.equal("invite offset link", offset.link, r.readString());
        Assert.equal("invite page size", 20, r.readInt());

        r = new TlReader(Requests.revokeExportedChatInvite(channel, offset.link));
        Assert.equal("edit invite", Api.MESSAGES_EDIT_EXPORTED_CHAT_INVITE,
                r.readInt());
        Assert.equal("revoked only", 1 << 2, r.readInt());
        assertInputPeerChannel(r);
        Assert.equal("revoked url", offset.link, r.readString());

        JoinRequest request = new JoinRequest();
        request.date = 88;
        request.user = user();
        r = new TlReader(Requests.getJoinRequests(channel, request, 10));
        Assert.equal("get join requests",
                Api.MESSAGES_GET_CHAT_INVITE_IMPORTERS, r.readInt());
        Assert.equal("pending-only flag", 1, r.readInt());
        assertInputPeerChannel(r);
        Assert.equal("request offset date", 88, r.readInt());
        assertInputUser(r);
        Assert.equal("request page size", 10, r.readInt());

        r = new TlReader(Requests.hideJoinRequest(channel, user(), true));
        Assert.equal("approve request", Api.MESSAGES_HIDE_CHAT_JOIN_REQUEST,
                r.readInt());
        Assert.equal("approved flag", 1, r.readInt());
        assertInputPeerChannel(r);
        assertInputUser(r);
    }

    private static void forumAdministrationWire() throws Exception
    {
        Peer channel = channel();
        TlReader r = new TlReader(Requests.createForumTopic(channel, "Topic",
                123456789L));
        Assert.equal("create topic", Api.MESSAGES_CREATE_FORUM_TOPIC,
                r.readInt());
        Assert.equal("server icon color", 0, r.readInt());
        assertInputPeerChannel(r);
        Assert.equal("topic title", "Topic", r.readString());
        Assert.equal("topic random id", 123456789L, r.readLong());

        r = new TlReader(Requests.renameForumTopic(channel, 41, "Renamed"));
        Assert.equal("edit topic", Api.MESSAGES_EDIT_FORUM_TOPIC, r.readInt());
        Assert.equal("title-only flags", 1, r.readInt());
        assertInputPeerChannel(r);
        Assert.equal("topic id", 41, r.readInt());
        Assert.equal("new title", "Renamed", r.readString());

        r = new TlReader(Requests.setForumTopicClosed(channel, 41, true));
        Assert.equal("edit close", Api.MESSAGES_EDIT_FORUM_TOPIC, r.readInt());
        Assert.equal("closed-only flags", 1 << 2, r.readInt());
        assertInputPeerChannel(r);
        Assert.equal("closed topic", 41, r.readInt());
        Assert.equal("closed true", Api.BOOL_TRUE, r.readInt());

        r = new TlReader(Requests.setGeneralTopicHidden(channel, true));
        Assert.equal("hide flags", 1 << 3, skipMethod(r));
        assertInputPeerChannel(r);
        Assert.equal("General id", 1, r.readInt());
        Assert.equal("hidden true", Api.BOOL_TRUE, r.readInt());

        r = new TlReader(Requests.setForumTopicPinned(channel, 41, false));
        Assert.equal("pin method", Api.MESSAGES_UPDATE_PINNED_FORUM_TOPIC,
                r.readInt());
        assertInputPeerChannel(r);
        Assert.equal("pinned topic", 41, r.readInt());
        Assert.equal("pinned false", Api.BOOL_FALSE, r.readInt());

        r = new TlReader(Requests.deleteForumTopic(channel, 41));
        Assert.equal("delete topic history", Api.MESSAGES_DELETE_TOPIC_HISTORY,
                r.readInt());
        assertInputPeerChannel(r);
        Assert.equal("deleted topic", 41, r.readInt());
    }

    private static void sparseAdministrationPages() throws Exception
    {
        TlWriter w = new TlWriter(48);
        w.writeInt(Api.MESSAGES_EXPORTED_CHAT_INVITES);
        w.writeInt(1);
        w.writeVectorHeader(1);
        w.writeInt(Api.CHAT_INVITE_PUBLIC_JOIN_REQUESTS);
        w.writeVectorHeader(0);
        ExportedInviteLinkPage links = ExportedInviteLinkPage.from(
                TlParser.parse(new TlReader(w.toByteArray())), 20,
                new PeerCache());
        Assert.equal("unsupported invite variants are isolated", 0,
                links.links.length);
        Assert.isTrue("short sparse invite page is exhausted", links.exhausted);

        w = new TlWriter(64);
        w.writeInt(Api.MESSAGES_CHAT_INVITE_IMPORTERS);
        w.writeInt(1);
        w.writeVectorHeader(1);
        w.writeInt(Api.CHAT_INVITE_IMPORTER);
        w.writeInt(1); // requested, with no optional about/approved_by
        w.writeLong(55);
        w.writeInt(1234);
        w.writeVectorHeader(0);
        JoinRequestPage requests = JoinRequestPage.from(
                TlParser.parse(new TlReader(w.toByteArray())), 10,
                new PeerCache());
        Assert.equal("sparse join request parsed", 1,
                requests.requests.length);
        Assert.equal("missing about is empty", "",
                requests.requests[0].about);
        Assert.equal("missing user object retains id", 55L,
                requests.requests[0].user.id);
        Assert.isTrue("short join request page is exhausted",
                requests.exhausted);

        w = new TlWriter(24);
        w.writeInt(Api.MESSAGES_EXPORTED_CHAT_INVITES);
        w.writeInt(0);
        w.writeVectorHeader(0);
        w.writeVectorHeader(0);
        links = ExportedInviteLinkPage.from(
                TlParser.parse(new TlReader(w.toByteArray())), 20,
                new PeerCache());
        Assert.equal("empty invite reply parsed", 0, links.links.length);
    }

    private static int skipMethod(TlReader r) throws Exception
    {
        Assert.equal("edit topic method", Api.MESSAGES_EDIT_FORUM_TOPIC,
                r.readInt());
        return r.readInt();
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

    private static void assertInputChannel(TlReader r) throws Exception
    {
        Assert.equal("input channel", Api.INPUT_CHANNEL, r.readInt());
        Assert.equal("channel id", 77L, r.readLong());
        Assert.equal("channel hash", 88L, r.readLong());
    }

    private static void assertInputPeerChannel(TlReader r) throws Exception
    {
        Assert.equal("input peer channel", Api.INPUT_PEER_CHANNEL, r.readInt());
        Assert.equal("peer channel id", 77L, r.readLong());
        Assert.equal("peer channel hash", 88L, r.readLong());
    }

    private static void assertInputUser(TlReader r) throws Exception
    {
        Assert.equal("input user", Api.INPUT_USER, r.readInt());
        Assert.equal("user id", 11L, r.readLong());
        Assert.equal("user hash", 22L, r.readLong());
    }
}
