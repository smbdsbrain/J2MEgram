package tg.api;

import tg.tl.TlObj;

/**
 * Editable core of {@code ChatAdminRights}, while retaining every hidden bit.
 *
 * The small UI deliberately omits stories, direct messages and rank
 * management.  {@link #rawFlags} keeps those bits when an existing admin is
 * edited so saving one visible checkbox cannot silently revoke unrelated
 * rights granted by another Telegram client.
 */
public final class ChatAdminRightsDef
{
    public static final int CHANGE_INFO = 1 << 0;
    public static final int POST_MESSAGES = 1 << 1;
    public static final int EDIT_MESSAGES = 1 << 2;
    public static final int DELETE_MESSAGES = 1 << 3;
    public static final int BAN_USERS = 1 << 4;
    public static final int INVITE_USERS = 1 << 5;
    public static final int PIN_MESSAGES = 1 << 7;
    public static final int ADD_ADMINS = 1 << 9;
    public static final int ANONYMOUS = 1 << 10;
    public static final int MANAGE_CALL = 1 << 11;
    public static final int OTHER = 1 << 12;
    public static final int MANAGE_TOPICS = 1 << 13;
    public static final int POST_STORIES = 1 << 14;
    public static final int EDIT_STORIES = 1 << 15;
    public static final int DELETE_STORIES = 1 << 16;
    public static final int MANAGE_DIRECT_MESSAGES = 1 << 17;
    public static final int MANAGE_RANKS = 1 << 18;

    public static final int CORE_MASK = CHANGE_INFO | POST_MESSAGES
            | EDIT_MESSAGES | DELETE_MESSAGES | BAN_USERS | INVITE_USERS
            | PIN_MESSAGES | ADD_ADMINS | ANONYMOUS | MANAGE_CALL
            | MANAGE_TOPICS;

    public int rawFlags;
    public boolean changeInfo;
    public boolean postMessages;
    public boolean editMessages;
    public boolean deleteMessages;
    public boolean banUsers;
    public boolean inviteUsers;
    public boolean pinMessages;
    public boolean addAdmins;
    public boolean anonymous;
    public boolean manageCall;
    public boolean manageTopics;

    public static ChatAdminRightsDef from(TlObj value)
    {
        ChatAdminRightsDef out = new ChatAdminRightsDef();
        if (value == null || value.id != Api.CHAT_ADMIN_RIGHTS) { return out; }
        out.rawFlags = value.flags;
        out.changeInfo = has(out.rawFlags, CHANGE_INFO);
        out.postMessages = has(out.rawFlags, POST_MESSAGES);
        out.editMessages = has(out.rawFlags, EDIT_MESSAGES);
        out.deleteMessages = has(out.rawFlags, DELETE_MESSAGES);
        out.banUsers = has(out.rawFlags, BAN_USERS);
        out.inviteUsers = has(out.rawFlags, INVITE_USERS);
        out.pinMessages = has(out.rawFlags, PIN_MESSAGES);
        out.addAdmins = has(out.rawFlags, ADD_ADMINS);
        out.anonymous = has(out.rawFlags, ANONYMOUS);
        out.manageCall = has(out.rawFlags, MANAGE_CALL);
        out.manageTopics = has(out.rawFlags, MANAGE_TOPICS);
        return out;
    }

    public ChatAdminRightsDef copy()
    {
        ChatAdminRightsDef out = new ChatAdminRightsDef();
        out.rawFlags = rawFlags;
        out.changeInfo = changeInfo;
        out.postMessages = postMessages;
        out.editMessages = editMessages;
        out.deleteMessages = deleteMessages;
        out.banUsers = banUsers;
        out.inviteUsers = inviteUsers;
        out.pinMessages = pinMessages;
        out.addAdmins = addAdmins;
        out.anonymous = anonymous;
        out.manageCall = manageCall;
        out.manageTopics = manageTopics;
        return out;
    }

    /** Full wire flags, with hidden rights retained and visible bits replaced. */
    public int flags()
    {
        int out = rawFlags & ~CORE_MASK;
        if (changeInfo) { out |= CHANGE_INFO; }
        if (postMessages) { out |= POST_MESSAGES; }
        if (editMessages) { out |= EDIT_MESSAGES; }
        if (deleteMessages) { out |= DELETE_MESSAGES; }
        if (banUsers) { out |= BAN_USERS; }
        if (inviteUsers) { out |= INVITE_USERS; }
        if (pinMessages) { out |= PIN_MESSAGES; }
        if (addAdmins) { out |= ADD_ADMINS; }
        if (anonymous) { out |= ANONYMOUS; }
        if (manageCall) { out |= MANAGE_CALL; }
        if (manageTopics) { out |= MANAGE_TOPICS; }
        return out;
    }

    public boolean hasCoreRights() { return (flags() & CORE_MASK) != 0; }

    public boolean empty() { return flags() == 0; }

    private static boolean has(int flags, int bit) { return (flags & bit) != 0; }
}
