package tg.api;

/** Bounded resume point for one source of a lazy custom-folder scan. */
public final class FolderScanCursor
{
    public int folderId;
    public Dialog offset;
    public int scanned;
    public boolean exhausted;

    public FolderScanCursor(int folderId) { this.folderId = folderId; }
}
