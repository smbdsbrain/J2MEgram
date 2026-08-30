package tg.api;

import java.io.IOException;

import tg.tl.TlObj;

/** One active-link page owned by the current administrator. */
public final class ExportedInviteLinkPage
{
    public ExportedInviteLink[] links = new ExportedInviteLink[0];
    public int total;
    public boolean exhausted;

    public static ExportedInviteLinkPage from(TlObj reply, int limit,
                                               PeerCache peers)
            throws IOException
    {
        if (reply == null || reply.id != Api.MESSAGES_EXPORTED_CHAT_INVITES)
        {
            throw new IOException("unexpected exported invite list reply");
        }
        peers.absorb(reply.vec(Api.F_MESSAGES_EXPORTED_CHAT_INVITES__USERS),
                null);
        TlObj[] raw = reply.vec(Api.F_MESSAGES_EXPORTED_CHAT_INVITES__INVITES);
        ExportedInviteLink[] out = new ExportedInviteLink[raw.length];
        int count = 0;
        for (int i = 0; i < raw.length; i++)
        {
            ExportedInviteLink link = ExportedInviteLink.from(raw[i]);
            if (link != null && !link.revoked) { out[count++] = link; }
        }
        if (count != out.length)
        {
            ExportedInviteLink[] exact = new ExportedInviteLink[count];
            System.arraycopy(out, 0, exact, 0, count);
            out = exact;
        }
        ExportedInviteLinkPage page = new ExportedInviteLinkPage();
        page.links = out;
        page.total = reply.intAt(Api.F_MESSAGES_EXPORTED_CHAT_INVITES__COUNT);
        page.exhausted = raw.length < limit || raw.length >= page.total;
        return page;
    }

    public ExportedInviteLink last()
    {
        return links.length == 0 ? null : links[links.length - 1];
    }
}
