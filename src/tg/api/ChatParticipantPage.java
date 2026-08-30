package tg.api;

import java.io.IOException;

import tg.tl.TlObj;

/** One bounded server page of channel participants. */
public final class ChatParticipantPage
{
    public static final int MEMBERS = 0;
    public static final int REMOVED = 1;

    public ChatParticipant[] participants = new ChatParticipant[0];
    public int offset;
    public int total;
    public boolean exhausted;

    public static ChatParticipantPage from(TlObj reply, int offset, int limit,
                                    PeerCache peers) throws IOException
    {
        if (reply == null || reply.id != Api.CHANNELS_CHANNEL_PARTICIPANTS)
        {
            throw new IOException("unexpected participants reply");
        }
        peers.absorb(reply.vec(Api.F_CHANNELS_CHANNEL_PARTICIPANTS__USERS),
                reply.vec(Api.F_CHANNELS_CHANNEL_PARTICIPANTS__CHATS));
        TlObj[] raw = reply.vec(
                Api.F_CHANNELS_CHANNEL_PARTICIPANTS__PARTICIPANTS);
        ChatParticipant[] rows = new ChatParticipant[raw.length];
        int count = 0;
        for (int i = 0; i < raw.length; i++)
        {
            ChatParticipant row = ChatParticipant.fromChannel(raw[i], peers);
            if (row != null) { rows[count++] = row; }
        }
        if (count != rows.length)
        {
            ChatParticipant[] trimmed = new ChatParticipant[count];
            System.arraycopy(rows, 0, trimmed, 0, count);
            rows = trimmed;
        }
        ChatParticipantPage page = new ChatParticipantPage();
        page.participants = rows;
        page.offset = offset;
        page.total = reply.intAt(Api.F_CHANNELS_CHANNEL_PARTICIPANTS__COUNT);
        page.exhausted = raw.length < limit || offset + raw.length >= page.total;
        return page;
    }
}
