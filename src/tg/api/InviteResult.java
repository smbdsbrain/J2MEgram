package tg.api;

import java.io.IOException;

import tg.tl.TlObj;

/** Result of adding users, including Telegram's privacy-related omissions. */
public final class InviteResult
{
    public int missingCount;
    public long[] missingUserIds = new long[0];
    public boolean premiumWouldAllow;
    public boolean premiumRequiredForPm;

    public boolean complete() { return missingCount == 0; }

    public static InviteResult from(TlObj reply) throws IOException
    {
        if (reply == null || reply.id != Api.MESSAGES_INVITED_USERS)
        {
            throw new IOException("unexpected invite result");
        }
        TlObj[] raw = reply.vec(Api.F_MESSAGES_INVITED_USERS__MISSING_INVITEES);
        InviteResult out = new InviteResult();
        out.missingCount = raw.length;
        out.missingUserIds = new long[raw.length];
        for (int i = 0; i < raw.length; i++)
        {
            TlObj item = raw[i];
            if (item == null || item.id != Api.MISSING_INVITEE) { continue; }
            out.missingUserIds[i] = item.num(Api.F_MISSING_INVITEE__USER_ID);
            out.premiumWouldAllow |= item.num(
                    Api.F_MISSING_INVITEE__PREMIUM_WOULD_ALLOW_INVITE) != 0;
            out.premiumRequiredForPm |= item.num(
                    Api.F_MISSING_INVITEE__PREMIUM_REQUIRED_FOR_PM) != 0;
        }
        return out;
    }
}
