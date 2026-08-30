package tg.api;

/** Session state for a non-root dialog list. */
public final class DialogListState
{
    public static final int ALL = 0;
    public static final int ARCHIVED = 1;
    public static final int CUSTOM = 2;

    public int kind;
    public int folderId;
    public String title = "Chats";
    public DialogFilterDefinition filter;
    public Dialog[] dialogs = new Dialog[0];
    public int total;
    public int above;
    public boolean totalKnown;
    public boolean loading;
    public boolean exhausted;
    public int scanned;
    public boolean explicitLoaded;
    /** Durable full-scan generation, zero until recovery starts. */
    public int indexGeneration;
    /** Live-update epoch captured when {@link #indexGeneration} started. */
    public int indexGenerationEpoch;
    /** Rows currently shown came from RMS rather than a complete scan. */
    public boolean cached;
    /** RMS failed for this opening; use the server-scan fallback only. */
    public boolean indexFailed;
    public FolderScanCursor mainCursor;
    public FolderScanCursor archiveCursor;

    public static DialogListState archive()
    {
        DialogListState out = new DialogListState();
        out.kind = ARCHIVED;
        out.folderId = 1;
        out.title = "Archived";
        return out;
    }

    public static DialogListState custom(DialogFilterDefinition value)
    {
        DialogListState out = new DialogListState();
        out.kind = CUSTOM;
        out.filter = value;
        out.title = value == null ? "Folder" : value.title;
        out.mainCursor = new FolderScanCursor(0);
        out.archiveCursor = new FolderScanCursor(1);
        return out;
    }
}
