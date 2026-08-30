package tg.api;

import tg.tl.TlObj;

/** One pending request to enter a group/channel. */
public final class JoinRequest
{
    public Peer user;
    public int date;
    public String about = "";
    public boolean viaChatlist;

    static JoinRequest from(TlObj value, PeerCache peers)
    {
        if (value == null || value.id != Api.CHAT_INVITE_IMPORTER) { return null; }
        JoinRequest out = new JoinRequest();
        long userId = value.num(Api.F_CHAT_INVITE_IMPORTER__USER_ID);
        out.user = peers.get(Peer.USER, userId);
        if (out.user == null) { out.user = new Peer(Peer.USER, userId); }
        out.date = value.intAt(Api.F_CHAT_INVITE_IMPORTER__DATE);
        out.about = value.strOrEmpty(Api.F_CHAT_INVITE_IMPORTER__ABOUT);
        out.viaChatlist = value.num(Api.F_CHAT_INVITE_IMPORTER__VIA_CHATLIST) != 0;
        return out;
    }
}
