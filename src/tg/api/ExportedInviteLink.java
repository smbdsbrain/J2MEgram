package tg.api;

import tg.tl.TlObj;

/** Bounded, UI-ready exported invite link. */
public final class ExportedInviteLink
{
    public String link = "";
    public String title = "";
    public long adminId;
    public int date;
    public int startDate;
    public int expireDate;
    public int usageLimit;
    public int usage;
    public int requested;
    public boolean revoked;
    public boolean permanent;
    public boolean requestNeeded;

    public static ExportedInviteLink from(TlObj value)
    {
        if (value == null || value.id != Api.CHAT_INVITE_EXPORTED) { return null; }
        ExportedInviteLink out = new ExportedInviteLink();
        out.revoked = value.num(Api.F_CHAT_INVITE_EXPORTED__REVOKED) != 0;
        out.permanent = value.num(Api.F_CHAT_INVITE_EXPORTED__PERMANENT) != 0;
        out.requestNeeded = value.num(
                Api.F_CHAT_INVITE_EXPORTED__REQUEST_NEEDED) != 0;
        out.link = value.strOrEmpty(Api.F_CHAT_INVITE_EXPORTED__LINK);
        out.adminId = value.num(Api.F_CHAT_INVITE_EXPORTED__ADMIN_ID);
        out.date = value.intAt(Api.F_CHAT_INVITE_EXPORTED__DATE);
        out.startDate = value.intAt(Api.F_CHAT_INVITE_EXPORTED__START_DATE);
        out.expireDate = value.intAt(Api.F_CHAT_INVITE_EXPORTED__EXPIRE_DATE);
        out.usageLimit = value.intAt(Api.F_CHAT_INVITE_EXPORTED__USAGE_LIMIT);
        out.usage = value.intAt(Api.F_CHAT_INVITE_EXPORTED__USAGE);
        out.requested = value.intAt(Api.F_CHAT_INVITE_EXPORTED__REQUESTED);
        out.title = value.strOrEmpty(Api.F_CHAT_INVITE_EXPORTED__TITLE);
        return out;
    }
}
