package tg.api;

import tg.tl.TlObj;

/** User-facing permissions backed by the inverse ChatBannedRights wire form. */
public final class ChatDefaultPermissions
{
    private static final int VIEW_MESSAGES = 1 << 0;
    private static final int SEND_MESSAGES = 1 << 1; // legacy umbrella
    private static final int SEND_MEDIA = 1 << 2;    // legacy media umbrella
    private static final int SEND_STICKERS = 1 << 3;
    private static final int SEND_GIFS = 1 << 4;
    private static final int SEND_GAMES = 1 << 5;
    private static final int SEND_INLINE = 1 << 6;
    private static final int EMBED_LINKS = 1 << 7;
    private static final int SEND_POLLS = 1 << 8;
    private static final int CHANGE_INFO = 1 << 10;
    private static final int INVITE_USERS = 1 << 15;
    private static final int PIN_MESSAGES = 1 << 17;
    private static final int MANAGE_TOPICS = 1 << 18;
    private static final int SEND_PHOTOS = 1 << 19;
    private static final int SEND_VIDEOS = 1 << 20;
    private static final int SEND_ROUNDVIDEOS = 1 << 21;
    private static final int SEND_AUDIOS = 1 << 22;
    private static final int SEND_VOICES = 1 << 23;
    private static final int SEND_DOCS = 1 << 24;
    private static final int SEND_PLAIN = 1 << 25;
    private static final int EDIT_RANK = 1 << 26;
    private static final int SEND_REACTIONS = 1 << 27;

    private static final int MEDIA_BITS = SEND_MEDIA | SEND_PHOTOS | SEND_VIDEOS
            | SEND_ROUNDVIDEOS | SEND_AUDIOS | SEND_VOICES | SEND_DOCS;
    private static final int STICKER_BITS = SEND_STICKERS | SEND_GIFS
            | SEND_GAMES | SEND_INLINE;
    private static final int EXPOSED_BITS = SEND_MESSAGES | SEND_PLAIN
            | MEDIA_BITS | STICKER_BITS | EMBED_LINKS | SEND_POLLS
            | SEND_REACTIONS | CHANGE_INFO | INVITE_USERS | PIN_MESSAGES
            | MANAGE_TOPICS;

    public int rawBannedFlags;
    public boolean sendText = true;
    public boolean sendMedia = true;
    public boolean sendStickers = true;
    public boolean embedLinks = true;
    public boolean sendPolls = true;
    public boolean sendReactions = true;
    public boolean inviteUsers = true;
    public boolean changeInfo = true;
    public boolean pinMessages = true;
    public boolean manageTopics = true;

    public static ChatDefaultPermissions from(TlObj value)
    {
        ChatDefaultPermissions out = new ChatDefaultPermissions();
        if (value == null || value.id != Api.CHAT_BANNED_RIGHTS) { return out; }
        int flags = value.flags;
        out.rawBannedFlags = flags;
        boolean legacy = has(flags, SEND_MESSAGES);
        out.sendText = !legacy && !has(flags, SEND_PLAIN);
        out.sendMedia = !legacy && (flags & MEDIA_BITS) == 0;
        out.sendStickers = !legacy && (flags & STICKER_BITS) == 0;
        out.embedLinks = !legacy && !has(flags, EMBED_LINKS);
        out.sendPolls = !legacy && !has(flags, SEND_POLLS);
        out.sendReactions = !legacy && !has(flags, SEND_REACTIONS);
        out.inviteUsers = !has(flags, INVITE_USERS);
        out.changeInfo = !has(flags, CHANGE_INFO);
        out.pinMessages = !has(flags, PIN_MESSAGES);
        out.manageTopics = !has(flags, MANAGE_TOPICS);
        return out;
    }

    public ChatDefaultPermissions copy()
    {
        ChatDefaultPermissions out = new ChatDefaultPermissions();
        out.rawBannedFlags = rawBannedFlags;
        out.sendText = sendText;
        out.sendMedia = sendMedia;
        out.sendStickers = sendStickers;
        out.embedLinks = embedLinks;
        out.sendPolls = sendPolls;
        out.sendReactions = sendReactions;
        out.inviteUsers = inviteUsers;
        out.changeInfo = changeInfo;
        out.pinMessages = pinMessages;
        out.manageTopics = manageTopics;
        return out;
    }

    /** Normalize legacy umbrellas into the groups the editor actually shows. */
    public int bannedFlags()
    {
        int out = rawBannedFlags & ~EXPOSED_BITS;
        if (!sendText) { out |= SEND_PLAIN; }
        if (!sendMedia) { out |= MEDIA_BITS; }
        if (!sendStickers) { out |= STICKER_BITS; }
        if (!embedLinks) { out |= EMBED_LINKS; }
        if (!sendPolls) { out |= SEND_POLLS; }
        if (!sendReactions) { out |= SEND_REACTIONS; }
        if (!inviteUsers) { out |= INVITE_USERS; }
        if (!changeInfo) { out |= CHANGE_INFO; }
        if (!pinMessages) { out |= PIN_MESSAGES; }
        if (!manageTopics) { out |= MANAGE_TOPICS; }
        return out;
    }

    private static boolean has(int flags, int bit) { return (flags & bit) != 0; }
}
