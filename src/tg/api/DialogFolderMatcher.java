package tg.api;

/** Pure custom-folder predicate, kept independent from networking and UI. */
public final class DialogFolderMatcher
{
    private DialogFolderMatcher() { }

    public static boolean matches(Dialog dialog, DialogFilterDefinition filter,
                                  int unixNow)
    {
        if (dialog == null || dialog.peer == null || filter == null)
        {
            return false;
        }
        Peer peer = dialog.peer;
        if (filter.containsExcluded(peer)) { return false; }
        if (filter.containsIncluded(peer)) { return true; }
        if (filter.kind == DialogFilterDefinition.SHARED) { return false; }

        boolean category;
        if (peer.kind == Peer.USER)
        {
            category = peer.bot ? filter.bots
                    : (peer.contact ? filter.contacts : filter.nonContacts);
        }
        else if (peer.kind == Peer.CHAT || peer.megagroup)
        {
            category = filter.groups;
        }
        else
        {
            category = filter.broadcasts;
        }
        if (!category) { return false; }
        if (filter.excludeArchived && dialog.folderId == 1) { return false; }
        if (filter.excludeRead && !dialog.unreadMark && dialog.unreadCount <= 0)
        {
            return false;
        }
        if (filter.excludeMuted && dialog.muteUntil > unixNow) { return false; }
        return true;
    }
}
