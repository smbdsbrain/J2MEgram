package tg.api;

import tg.tl.TlObj;

/** Compact, editable representation of a Telegram {@code DialogFilter}. */
public final class DialogFilterDefinition
{
    public static final int DEFAULT = 0;
    public static final int PERSONAL = 1;
    public static final int SHARED = 2;

    public int kind = PERSONAL;
    public int id;
    public String title = "";
    public String emoticon;
    public int color = -1;
    public boolean titleNoAnimate;
    public boolean hasMyInvites;

    public boolean contacts;
    public boolean nonContacts;
    public boolean groups;
    public boolean broadcasts;
    public boolean bots;
    public boolean excludeMuted;
    public boolean excludeRead;
    public boolean excludeArchived;

    public Peer[] pinnedPeers = new Peer[0];
    public Peer[] includePeers = new Peer[0];
    public Peer[] excludePeers = new Peer[0];

    public boolean editable() { return kind == PERSONAL; }

    public DialogFilterDefinition copy()
    {
        DialogFilterDefinition out = new DialogFilterDefinition();
        out.kind = kind;
        out.id = id;
        out.title = title;
        out.emoticon = emoticon;
        out.color = color;
        out.titleNoAnimate = titleNoAnimate;
        out.hasMyInvites = hasMyInvites;
        out.contacts = contacts;
        out.nonContacts = nonContacts;
        out.groups = groups;
        out.broadcasts = broadcasts;
        out.bots = bots;
        out.excludeMuted = excludeMuted;
        out.excludeRead = excludeRead;
        out.excludeArchived = excludeArchived;
        out.pinnedPeers = copyPeers(pinnedPeers);
        out.includePeers = copyPeers(includePeers);
        out.excludePeers = copyPeers(excludePeers);
        return out;
    }

    public boolean hasInclusion()
    {
        return contacts || nonContacts || groups || broadcasts || bots
                || includePeers.length > 0 || pinnedPeers.length > 0;
    }

    public boolean containsPinned(Peer peer)
    {
        return indexOf(pinnedPeers, peer) >= 0;
    }

    public boolean containsIncluded(Peer peer)
    {
        return indexOf(includePeers, peer) >= 0 || containsPinned(peer);
    }

    public boolean containsExcluded(Peer peer)
    {
        return indexOf(excludePeers, peer) >= 0;
    }

    public void setPinned(Peer peer, boolean pinned)
    {
        pinnedPeers = setMembership(pinnedPeers, peer, pinned);
        if (pinned)
        {
            includePeers = setMembership(includePeers, peer, true);
            excludePeers = setMembership(excludePeers, peer, false);
        }
    }

    public void setIncluded(Peer peer, boolean included)
    {
        includePeers = setMembership(includePeers, peer, included);
        if (included) { excludePeers = setMembership(excludePeers, peer, false); }
        else { pinnedPeers = setMembership(pinnedPeers, peer, false); }
    }

    public void setExcluded(Peer peer, boolean excluded)
    {
        excludePeers = setMembership(excludePeers, peer, excluded);
        if (excluded)
        {
            includePeers = setMembership(includePeers, peer, false);
            pinnedPeers = setMembership(pinnedPeers, peer, false);
        }
    }

    public static DialogFilterDefinition from(TlObj obj, PeerCache cache)
    {
        if (obj == null) { return null; }
        DialogFilterDefinition out = new DialogFilterDefinition();
        if (obj.id == Api.DIALOG_FILTER_DEFAULT)
        {
            out.kind = DEFAULT;
            out.id = 0;
            out.title = "All chats";
            return out;
        }
        if (obj.id == Api.DIALOG_FILTER)
        {
            out.kind = PERSONAL;
            out.id = obj.intAt(Api.F_DIALOG_FILTER__ID);
            out.title = title(obj.obj(Api.F_DIALOG_FILTER__TITLE));
            out.contacts = obj.num(Api.F_DIALOG_FILTER__CONTACTS) != 0;
            out.nonContacts = obj.num(Api.F_DIALOG_FILTER__NON_CONTACTS) != 0;
            out.groups = obj.num(Api.F_DIALOG_FILTER__GROUPS) != 0;
            out.broadcasts = obj.num(Api.F_DIALOG_FILTER__BROADCASTS) != 0;
            out.bots = obj.num(Api.F_DIALOG_FILTER__BOTS) != 0;
            out.excludeMuted = obj.num(Api.F_DIALOG_FILTER__EXCLUDE_MUTED) != 0;
            out.excludeRead = obj.num(Api.F_DIALOG_FILTER__EXCLUDE_READ) != 0;
            out.excludeArchived = obj.num(Api.F_DIALOG_FILTER__EXCLUDE_ARCHIVED) != 0;
            out.titleNoAnimate = obj.flag(28);
            out.emoticon = obj.str(Api.F_DIALOG_FILTER__EMOTICON);
            out.color = obj.flag(27) ? obj.intAt(Api.F_DIALOG_FILTER__COLOR) : -1;
            out.pinnedPeers = peers(obj.vec(Api.F_DIALOG_FILTER__PINNED_PEERS), cache);
            out.includePeers = peers(obj.vec(Api.F_DIALOG_FILTER__INCLUDE_PEERS), cache);
            out.excludePeers = peers(obj.vec(Api.F_DIALOG_FILTER__EXCLUDE_PEERS), cache);
            return out;
        }
        if (obj.id == Api.DIALOG_FILTER_CHATLIST)
        {
            out.kind = SHARED;
            out.id = obj.intAt(Api.F_DIALOG_FILTER_CHATLIST__ID);
            out.title = title(obj.obj(Api.F_DIALOG_FILTER_CHATLIST__TITLE));
            out.hasMyInvites = obj.flag(26);
            out.titleNoAnimate = obj.flag(28);
            out.emoticon = obj.str(Api.F_DIALOG_FILTER_CHATLIST__EMOTICON);
            out.color = obj.flag(27)
                    ? obj.intAt(Api.F_DIALOG_FILTER_CHATLIST__COLOR) : -1;
            out.pinnedPeers = peers(
                    obj.vec(Api.F_DIALOG_FILTER_CHATLIST__PINNED_PEERS), cache);
            out.includePeers = peers(
                    obj.vec(Api.F_DIALOG_FILTER_CHATLIST__INCLUDE_PEERS), cache);
            return out;
        }
        return null;
    }

    private static String title(TlObj text)
    {
        return text == null ? "" : text.strOrEmpty(Api.F_TEXT_WITH_ENTITIES__TEXT);
    }

    private static Peer[] peers(TlObj[] values, PeerCache cache)
    {
        if (values == null || values.length == 0) { return new Peer[0]; }
        Peer[] out = new Peer[values.length];
        int count = 0;
        for (int i = 0; i < values.length; i++)
        {
            Peer peer = Peer.fromInputPeerObj(values[i]);
            if (peer == null) { continue; }
            if (peer.self && cache != null && cache.self() != null)
            {
                peer = cache.self();
            }
            else if (cache != null) { peer = cache.resolve(peer); }
            out[count++] = peer;
        }
        if (count == out.length) { return out; }
        Peer[] exact = new Peer[count];
        System.arraycopy(out, 0, exact, 0, count);
        return exact;
    }

    public static int indexOf(Peer[] peers, Peer wanted)
    {
        if (peers == null || wanted == null) { return -1; }
        for (int i = 0; i < peers.length; i++)
        {
            Peer peer = peers[i];
            if (peer != null && peer.kind == wanted.kind && peer.id == wanted.id)
            {
                return i;
            }
        }
        return -1;
    }

    private static Peer[] setMembership(Peer[] values, Peer peer, boolean add)
    {
        if (values == null) { values = new Peer[0]; }
        int at = indexOf(values, peer);
        if (add && at >= 0 || !add && at < 0) { return values; }
        if (add)
        {
            Peer[] out = new Peer[values.length + 1];
            System.arraycopy(values, 0, out, 0, values.length);
            out[values.length] = peer;
            return out;
        }
        Peer[] out = new Peer[values.length - 1];
        if (at > 0) { System.arraycopy(values, 0, out, 0, at); }
        if (at + 1 < values.length)
        {
            System.arraycopy(values, at + 1, out, at, values.length - at - 1);
        }
        return out;
    }

    private static Peer[] copyPeers(Peer[] peers)
    {
        if (peers == null || peers.length == 0) { return new Peer[0]; }
        Peer[] out = new Peer[peers.length];
        System.arraycopy(peers, 0, out, 0, peers.length);
        return out;
    }
}
