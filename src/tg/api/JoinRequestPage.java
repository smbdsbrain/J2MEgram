package tg.api;

import java.io.IOException;

import tg.tl.TlObj;

/** One bounded page of pending join requests. */
public final class JoinRequestPage
{
    public JoinRequest[] requests = new JoinRequest[0];
    public int total;
    public boolean exhausted;

    public static JoinRequestPage from(TlObj reply, int limit, PeerCache peers)
            throws IOException
    {
        if (reply == null || reply.id != Api.MESSAGES_CHAT_INVITE_IMPORTERS)
        {
            throw new IOException("unexpected join request list reply");
        }
        peers.absorb(reply.vec(Api.F_MESSAGES_CHAT_INVITE_IMPORTERS__USERS),
                null);
        TlObj[] raw = reply.vec(Api.F_MESSAGES_CHAT_INVITE_IMPORTERS__IMPORTERS);
        JoinRequest[] out = new JoinRequest[raw.length];
        int count = 0;
        for (int i = 0; i < raw.length; i++)
        {
            JoinRequest request = JoinRequest.from(raw[i], peers);
            if (request != null) { out[count++] = request; }
        }
        if (count != out.length)
        {
            JoinRequest[] exact = new JoinRequest[count];
            System.arraycopy(out, 0, exact, 0, count);
            out = exact;
        }
        JoinRequestPage page = new JoinRequestPage();
        page.requests = out;
        page.total = reply.intAt(Api.F_MESSAGES_CHAT_INVITE_IMPORTERS__COUNT);
        page.exhausted = raw.length < limit || raw.length >= page.total;
        return page;
    }

    public JoinRequest last()
    {
        return requests.length == 0 ? null : requests[requests.length - 1];
    }
}
