package tgtest;

import tg.api.Api;
import tg.api.Dialog;
import tg.api.DialogFilterDefinition;
import tg.api.DialogFolderMatcher;
import tg.api.DialogPage;
import tg.api.Peer;
import tg.api.PeerCache;
import tg.api.Requests;
import tg.tl.TlObj;
import tg.tl.TlParser;
import tg.tl.TlReader;
import tg.tl.TlWriter;

/** Wire shapes and deterministic Telegram folder semantics. */
public final class FolderTest implements Test
{
    public String name() { return "folders/wire-and-rules"; }

    public void run() throws Exception
    {
        folderAwareDialogs();
        folderReplyIsolation();
        managementWire();
        filterWire();
        filterParsing();
        membershipRules();
        explicitListsStayDisjoint();
    }

    private static void folderAwareDialogs() throws Exception
    {
        Dialog offset = dialog(Peer.CHANNEL, 5);
        offset.peer.accessHash = 6;
        offset.date = 7;
        offset.topMessageId = 8;
        TlReader r = new TlReader(Requests.getDialogs(offset, 30, 0, 1));
        Assert.equal("getDialogs", Api.MESSAGES_GET_DIALOGS, r.readInt());
        Assert.equal("folder flag", 2, r.readInt());
        Assert.equal("archive id", 1, r.readInt());
        Assert.equal("date", 7, r.readInt());
        Assert.equal("message", 8, r.readInt());
        Assert.equal("channel peer", Api.INPUT_PEER_CHANNEL, r.readInt());
        Assert.equal("channel id", 5L, r.readLong());
        Assert.equal("channel hash", 6L, r.readLong());
        Assert.equal("limit", 30, r.readInt());
        Assert.equal("hash", 0L, r.readLong());
    }

    private static void folderReplyIsolation()
    {
        Dialog mainPin = dialog(Peer.USER, 1);
        mainPin.pinned = true;
        mainPin.folderId = 0;
        Dialog archivedPin = dialog(Peer.USER, 2);
        archivedPin.pinned = true;
        archivedPin.folderId = 1;
        Dialog archived = dialog(Peer.USER, 3);
        archived.folderId = 1;
        DialogPage page = new DialogPage();
        page.dialogs = new Dialog[] { mainPin, archivedPin, archived };
        page.total = 2;
        page.retainFolder(1);
        Assert.equal("main pin excluded from archive", 2, page.dialogs.length);
        Assert.equal("archive pin retained", 2L, page.dialogs[0].peer.id);
        Assert.equal("archive row retained", 3L, page.dialogs[1].peer.id);
        Assert.equal("server total preserved", 2, page.total);
    }

    private static void managementWire() throws Exception
    {
        Peer peer = new Peer(Peer.USER, 11);
        peer.accessHash = 12;
        TlReader r = new TlReader(Requests.toggleDialogPin(peer, true));
        Assert.equal("toggle method", Api.MESSAGES_TOGGLE_DIALOG_PIN, r.readInt());
        Assert.equal("pinned flag", 1, r.readInt());
        Assert.equal("dialog peer", Api.INPUT_DIALOG_PEER, r.readInt());
        Assert.equal("user peer", Api.INPUT_PEER_USER, r.readInt());
        Assert.equal("user id", 11L, r.readLong());
        Assert.equal("user hash", 12L, r.readLong());

        r = new TlReader(Requests.editPeerFolder(peer, 1));
        Assert.equal("archive method", Api.FOLDERS_EDIT_PEER_FOLDERS, r.readInt());
        Assert.equal("one folder peer", 1, r.readVectorCount());
        Assert.equal("folder peer ctor", Api.INPUT_FOLDER_PEER, r.readInt());
        Assert.equal("folder user", Api.INPUT_PEER_USER, r.readInt());
        r.readLong();
        r.readLong();
        Assert.equal("archive destination", 1, r.readInt());

        r = new TlReader(Requests.getPeerDialogs(new Peer[] { peer }));
        Assert.equal("peer dialogs method", Api.MESSAGES_GET_PEER_DIALOGS,
                r.readInt());
        Assert.equal("one dialog peer", 1, r.readVectorCount());
        Assert.equal("input dialog peer", Api.INPUT_DIALOG_PEER, r.readInt());

        r = new TlReader(Requests.updateDialogFiltersOrder(
                new int[] { 0, 4, 2 }));
        Assert.equal("order method", Api.MESSAGES_UPDATE_DIALOG_FILTERS_ORDER,
                r.readInt());
        Assert.equal("order count", 3, r.readVectorCount());
        Assert.equal("default first", 0, r.readInt());
        Assert.equal("first custom", 4, r.readInt());
        Assert.equal("second custom", 2, r.readInt());
    }

    private static void filterWire() throws Exception
    {
        DialogFilterDefinition f = new DialogFilterDefinition();
        f.id = 9;
        f.title = "Work";
        f.contacts = true;
        f.groups = true;
        f.excludeMuted = true;
        f.excludeArchived = true;
        f.emoticon = "W";
        f.color = 3;
        Peer pinned = new Peer(Peer.CHAT, 20);
        Peer included = new Peer(Peer.USER, 21);
        included.accessHash = 22;
        Peer excluded = new Peer(Peer.CHANNEL, 23);
        excluded.accessHash = 24;
        f.pinnedPeers = new Peer[] { pinned };
        f.includePeers = new Peer[] { included };
        f.excludePeers = new Peer[] { excluded };

        TlReader r = new TlReader(Requests.updateDialogFilter(9, f));
        Assert.equal("update method", Api.MESSAGES_UPDATE_DIALOG_FILTER, r.readInt());
        Assert.equal("filter present", 1, r.readInt());
        Assert.equal("outer id", 9, r.readInt());
        Assert.equal("filter ctor", Api.DIALOG_FILTER, r.readInt());
        int flags = r.readInt();
        Assert.isTrue("contacts", (flags & 1) != 0);
        Assert.isTrue("groups", (flags & 4) != 0);
        Assert.isTrue("exclude muted", (flags & (1 << 11)) != 0);
        Assert.isTrue("exclude archived", (flags & (1 << 13)) != 0);
        Assert.isTrue("emoticon", (flags & (1 << 25)) != 0);
        Assert.isTrue("color", (flags & (1 << 27)) != 0);
        Assert.equal("inner id", 9, r.readInt());
        Assert.equal("title ctor", Api.TEXT_WITH_ENTITIES, r.readInt());
        Assert.equal("title", "Work", r.readString());
        Assert.equal("no title entities", 0, r.readVectorCount());
        Assert.equal("emoticon value", "W", r.readString());
        Assert.equal("color value", 3, r.readInt());
        Assert.equal("one pinned", 1, r.readVectorCount());
        Assert.equal("pinned chat", Api.INPUT_PEER_CHAT, r.readInt());
        Assert.equal("pinned id", 20L, r.readLong());
        Assert.equal("one included", 1, r.readVectorCount());
        Assert.equal("included user", Api.INPUT_PEER_USER, r.readInt());
        r.readLong();
        r.readLong();
        Assert.equal("one excluded", 1, r.readVectorCount());
        Assert.equal("excluded channel", Api.INPUT_PEER_CHANNEL, r.readInt());

        r = new TlReader(Requests.updateDialogFilter(9, null));
        Assert.equal("delete method", Api.MESSAGES_UPDATE_DIALOG_FILTER, r.readInt());
        Assert.equal("filter absent", 0, r.readInt());
        Assert.equal("deleted id", 9, r.readInt());
        Assert.isFalse("no trailing filter", r.hasMore());
    }

    private static void membershipRules()
    {
        int now = 1000;
        DialogFilterDefinition f = new DialogFilterDefinition();
        f.contacts = true;
        f.groups = true;
        f.broadcasts = true;
        f.bots = true;
        f.excludeMuted = true;
        f.excludeRead = true;
        f.excludeArchived = true;

        Dialog contact = dialog(Peer.USER, 1);
        contact.peer.contact = true;
        contact.unreadCount = 1;
        Assert.isTrue("unread contact", DialogFolderMatcher.matches(contact, f, now));
        contact.unreadCount = 0;
        Assert.isFalse("read contact excluded",
                DialogFolderMatcher.matches(contact, f, now));
        contact.unreadMark = true;
        Assert.isTrue("manual unread", DialogFolderMatcher.matches(contact, f, now));
        contact.muteUntil = now + 1;
        Assert.isFalse("muted excluded", DialogFolderMatcher.matches(contact, f, now));

        Dialog bot = dialog(Peer.USER, 2);
        bot.peer.contact = true;
        bot.peer.bot = true;
        bot.unreadCount = 1;
        Assert.isTrue("bot category wins over contact",
                DialogFolderMatcher.matches(bot, f, now));

        Dialog group = dialog(Peer.CHANNEL, 3);
        group.peer.megagroup = true;
        group.unreadCount = 1;
        Assert.isTrue("megagroup", DialogFolderMatcher.matches(group, f, now));

        Dialog channel = dialog(Peer.CHANNEL, 4);
        channel.peer.broadcast = true;
        channel.unreadCount = 1;
        Assert.isTrue("broadcast", DialogFolderMatcher.matches(channel, f, now));
        channel.folderId = 1;
        Assert.isFalse("archive excluded", DialogFolderMatcher.matches(channel, f, now));

        f.includePeers = new Peer[] { channel.peer };
        Assert.isTrue("explicit include bypasses rule exclusions",
                DialogFolderMatcher.matches(channel, f, now));
        f.excludePeers = new Peer[] { channel.peer };
        Assert.isFalse("explicit exclude wins",
                DialogFolderMatcher.matches(channel, f, now));

        DialogFilterDefinition shared = new DialogFilterDefinition();
        shared.kind = DialogFilterDefinition.SHARED;
        shared.contacts = true;
        Assert.isFalse("shared ignores dynamic rules",
                DialogFolderMatcher.matches(contact, shared, now));
        shared.includePeers = new Peer[] { contact.peer };
        Assert.isTrue("shared explicit member",
                DialogFolderMatcher.matches(contact, shared, now));
    }

    private static void filterParsing() throws Exception
    {
        DialogFilterDefinition source = new DialogFilterDefinition();
        source.id = 6;
        source.title = "Family";
        source.contacts = true;
        source.excludeArchived = true;
        Peer included = new Peer(Peer.USER, 40);
        included.accessHash = 41;
        source.includePeers = new Peer[] { included };
        TlWriter w = new TlWriter(128);
        Requests.writeDialogFilter(w, source);
        TlObj parsed = TlParser.parse(new TlReader(w.toByteArray()));
        DialogFilterDefinition copy = DialogFilterDefinition.from(parsed,
                new PeerCache());
        Assert.equal("parsed id", 6, copy.id);
        Assert.equal("parsed title", "Family", copy.title);
        Assert.isTrue("parsed contacts", copy.contacts);
        Assert.isTrue("parsed archive exclusion", copy.excludeArchived);
        Assert.equal("parsed include count", 1, copy.includePeers.length);
        Assert.equal("parsed include id", 40L, copy.includePeers[0].id);
        Assert.equal("parsed include hash", 41L, copy.includePeers[0].accessHash);

        w = new TlWriter(8);
        w.writeInt(Api.DIALOG_FILTER_DEFAULT);
        copy = DialogFilterDefinition.from(
                TlParser.parse(new TlReader(w.toByteArray())), null);
        Assert.equal("default kind", DialogFilterDefinition.DEFAULT, copy.kind);
        Assert.equal("default title", "All chats", copy.title);

        w = new TlWriter(96);
        w.writeInt(Api.DIALOG_FILTER_CHATLIST);
        w.writeInt(0);
        w.writeInt(8);
        w.writeInt(Api.TEXT_WITH_ENTITIES);
        w.writeString("Shared");
        w.writeVectorHeader(0);
        w.writeVectorHeader(0);
        w.writeVectorHeader(1);
        w.writeInt(Api.INPUT_PEER_CHAT);
        w.writeLong(55);
        copy = DialogFilterDefinition.from(
                TlParser.parse(new TlReader(w.toByteArray())), null);
        Assert.equal("shared kind", DialogFilterDefinition.SHARED, copy.kind);
        Assert.isFalse("shared is read-only", copy.editable());
        Assert.equal("shared member", 55L, copy.includePeers[0].id);
    }

    private static void explicitListsStayDisjoint()
    {
        DialogFilterDefinition f = new DialogFilterDefinition();
        Peer p = new Peer(Peer.USER, 7);
        f.setExcluded(p, true);
        Assert.isTrue("excluded", f.containsExcluded(p));
        f.setIncluded(p, true);
        Assert.isTrue("included", f.containsIncluded(p));
        Assert.isFalse("include removes exclude", f.containsExcluded(p));
        f.setPinned(p, true);
        Assert.isTrue("pinned", f.containsPinned(p));
        Assert.equal("pin does not synthesize include", 1,
                f.includePeers.length);
        f.setIncluded(p, false);
        Assert.isTrue("uninclude keeps independent pin", f.containsPinned(p));
        Assert.equal("explicit include removed", 0, f.includePeers.length);
        Assert.isTrue("pin still includes for matching", f.containsIncluded(p));
        f.setPinned(p, false);
        Assert.isFalse("unpin removes only pin", f.containsPinned(p));
        Assert.equal("unpin leaves no artificial include", 0,
                f.includePeers.length);
        f.setPinned(p, true);
        f.setExcluded(p, true);
        Assert.isFalse("exclude removes pin", f.containsPinned(p));
        Assert.isFalse("exclude removes include", f.containsIncluded(p));
    }

    private static Dialog dialog(int kind, long id)
    {
        Dialog d = new Dialog();
        d.peer = new Peer(kind, id);
        return d;
    }
}
