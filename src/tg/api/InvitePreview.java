package tg.api;

import java.io.IOException;

import tg.tl.TlObj;

/** Safe preview of an invite hash before messages.importChatInvite mutates state. */
public final class InvitePreview
{
    public String hash = "";
    public String title = "";
    public String about = "";
    public int participantsCount = -1;
    public int expiresAt;
    public boolean already;
    public boolean requestNeeded;
    public boolean broadcast;
    public boolean megagroup;
    public boolean publicChat;
    public Peer peer;

    static InvitePreview from(TlObj reply, String hash, PeerCache peers)
            throws IOException
    {
        InvitePreview out = new InvitePreview();
        out.hash = hash;
        if (reply != null && reply.id == Api.CHAT_INVITE_ALREADY)
        {
            Peer chat = Peer.fromChat(reply.obj(Api.F_CHAT_INVITE_ALREADY__CHAT));
            peers.put(chat);
            out.peer = peers.resolve(chat);
            out.title = out.peer == null ? "" : out.peer.title;
            out.already = true;
            return out;
        }
        if (reply != null && reply.id == Api.CHAT_INVITE_PEEK)
        {
            Peer chat = Peer.fromChat(reply.obj(Api.F_CHAT_INVITE_PEEK__CHAT));
            peers.put(chat);
            out.peer = peers.resolve(chat);
            out.title = out.peer == null ? "" : out.peer.title;
            out.expiresAt = reply.intAt(Api.F_CHAT_INVITE_PEEK__EXPIRES);
            return out;
        }
        if (reply == null || reply.id != Api.CHAT_INVITE)
        {
            throw new IOException("unexpected invite preview reply");
        }
        out.title = reply.strOrEmpty(Api.F_CHAT_INVITE__TITLE);
        out.about = reply.strOrEmpty(Api.F_CHAT_INVITE__ABOUT);
        out.participantsCount = reply.intAt(
                Api.F_CHAT_INVITE__PARTICIPANTS_COUNT);
        out.requestNeeded = reply.num(Api.F_CHAT_INVITE__REQUEST_NEEDED) != 0;
        out.broadcast = reply.num(Api.F_CHAT_INVITE__BROADCAST) != 0;
        out.megagroup = reply.num(Api.F_CHAT_INVITE__MEGAGROUP) != 0;
        out.publicChat = reply.num(Api.F_CHAT_INVITE__PUBLIC) != 0;
        return out;
    }
}
