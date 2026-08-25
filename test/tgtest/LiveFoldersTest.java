package tgtest;

import tg.api.Dialog;
import tg.api.DialogFilterDefinition;
import tg.api.DialogPage;
import tg.api.Peer;
import tg.api.Telegram;
import tg.crypto.Rng;
import tg.mt.Dc;

/**
 * Reversible production smoke test for pin, archive and personal folders.
 * Every mutation has a finally-path back to the state observed at entry.
 */
public final class LiveFoldersTest
{
    public static void main(String[] args) throws Exception
    {
        FileAuthKeyStore store = new FileAuthKeyStore();
        SeTransport transport = new SeTransport();
        transport.setReadTimeoutMs(60000);
        Telegram tg = new Telegram(transport, new Rng(), store);

        Peer target = null;
        boolean originalPinned = false;
        int originalFolder = 0;
        boolean pinDirty = false;
        boolean folderDirty = false;
        int disposableId = -1;
        boolean disposableExists = false;
        int[] originalOrder = null;

        try
        {
            tg.connect();
            Peer me = tg.checkAuthorization();
            if (me == null)
            {
                throw new Exception("stored production session is not authorized");
            }
            System.out.println("authorized on dc" + tg.dcId() + " ("
                    + (Dc.isTest() ? "test" : "production") + ")");

            DialogPage main = tg.getDialogsInFolder(0, 30);
            DialogPage archived = tg.getDialogsInFolder(1, 30);
            for (int i = 0; i < archived.dialogs.length; i++)
            {
                if (archived.dialogs[i] == null
                        || archived.dialogs[i].folderId != 1)
                {
                    throw new Exception("archive reply leaked a non-archive dialog");
                }
            }
            System.out.println("archive folder isolation OK");
            for (int i = main.dialogs.length - 1; i >= 0; i--)
            {
                Dialog row = main.dialogs[i];
                if (row != null && row.peer != null && !row.pinned
                        && !row.peer.self)
                {
                    target = row.peer;
                    originalPinned = row.pinned;
                    originalFolder = row.folderId;
                    break;
                }
            }
            if (target == null) { throw new Exception("no safe unpinned chat found"); }
            System.out.println("selected reversible target " + target.key());

            tg.toggleDialogPin(target, true);
            pinDirty = true;
            assertDialogState(tg, target, true, originalFolder, "pin");
            tg.toggleDialogPin(target, originalPinned);
            pinDirty = false;
            assertDialogState(tg, target, originalPinned, originalFolder,
                    "pin restore");
            System.out.println("pin/unpin OK and restored");

            tg.editPeerFolder(target, 1);
            folderDirty = true;
            assertDialogState(tg, target, originalPinned, 1, "archive");
            tg.editPeerFolder(target, originalFolder);
            folderDirty = false;
            assertDialogState(tg, target, originalPinned, originalFolder,
                    "archive restore");
            System.out.println("archive/unarchive OK and restored");

            DialogFilterDefinition[] before = tg.getDialogFilters();
            originalOrder = ids(before);
            disposableId = firstFreeId(before);
            DialogFilterDefinition filter = new DialogFilterDefinition();
            filter.id = disposableId;
            filter.title = "J2ME test";
            filter.includePeers = new Peer[] { target };
            tg.updateDialogFilter(filter);
            disposableExists = true;
            DialogFilterDefinition created = find(tg.getDialogFilters(),
                    disposableId);
            if (created == null) { throw new Exception("created folder missing"); }

            filter.title = "J2ME smoke";
            filter.setPinned(target, true);
            tg.updateDialogFilter(filter);
            DialogFilterDefinition updated = find(tg.getDialogFilters(),
                    disposableId);
            if (updated == null || !"J2ME smoke".equals(updated.title)
                    || !updated.containsPinned(target))
            {
                throw new Exception("folder update was not reflected");
            }

            DialogFilterDefinition[] withDisposable = tg.getDialogFilters();
            int[] reordered = moveDisposable(withDisposable, disposableId);
            tg.updateDialogFiltersOrder(reordered);
            assertOrder(tg.getDialogFilters(), reordered);

            tg.deleteDialogFilter(disposableId);
            disposableExists = false;
            if (find(tg.getDialogFilters(), disposableId) != null)
            {
                throw new Exception("deleted folder is still present");
            }
            tg.updateDialogFiltersOrder(originalOrder);
            assertOrder(tg.getDialogFilters(), originalOrder);
            System.out.println("folder create/update/reorder/delete OK and restored");
            System.out.println("LIVE FOLDERS OK");
        }
        finally
        {
            if (disposableExists && disposableId >= 0)
            {
                try { tg.deleteDialogFilter(disposableId); }
                catch (Throwable cleanup)
                {
                    System.out.println("cleanup: disposable folder: "
                            + cleanup.getMessage());
                }
            }
            if (originalOrder != null)
            {
                try { tg.updateDialogFiltersOrder(originalOrder); }
                catch (Throwable cleanup)
                {
                    System.out.println("cleanup: folder order: "
                            + cleanup.getMessage());
                }
            }
            if (folderDirty && target != null)
            {
                try { tg.editPeerFolder(target, originalFolder); }
                catch (Throwable cleanup)
                {
                    System.out.println("cleanup: archive state: "
                            + cleanup.getMessage());
                }
            }
            if (pinDirty && target != null)
            {
                try { tg.toggleDialogPin(target, originalPinned); }
                catch (Throwable cleanup)
                {
                    System.out.println("cleanup: pin state: "
                            + cleanup.getMessage());
                }
            }
            tg.close();
        }
    }

    private static void assertDialogState(Telegram tg, Peer peer,
            boolean pinned, int folder, String step) throws Exception
    {
        Dialog[] rows = tg.getPeerDialogs(new Peer[] { peer }).dialogs;
        if (rows.length != 1 || rows[0].pinned != pinned
                || rows[0].folderId != folder)
        {
            throw new Exception(step + " state mismatch");
        }
    }

    private static DialogFilterDefinition find(DialogFilterDefinition[] filters,
            int id)
    {
        for (int i = 0; i < filters.length; i++)
        {
            if (filters[i] != null && filters[i].id == id) { return filters[i]; }
        }
        return null;
    }

    private static int firstFreeId(DialogFilterDefinition[] filters)
    {
        int id = 2;
        while (find(filters, id) != null) { id++; }
        return id;
    }

    private static int[] ids(DialogFilterDefinition[] filters)
    {
        int[] out = new int[filters.length];
        for (int i = 0; i < filters.length; i++) { out[i] = filters[i].id; }
        return out;
    }

    private static int[] moveDisposable(DialogFilterDefinition[] filters,
            int disposableId)
    {
        int[] current = ids(filters);
        int at = -1;
        int destination = -1;
        for (int i = 0; i < current.length; i++)
        {
            if (current[i] == disposableId) { at = i; }
            else if (current[i] != 0 && destination < 0) { destination = i; }
        }
        if (at < 0) { return current; }
        if (destination < 0) { destination = at; }
        int value = current[at];
        if (at > destination)
        {
            System.arraycopy(current, destination, current, destination + 1,
                    at - destination);
        }
        else if (at < destination)
        {
            System.arraycopy(current, at + 1, current, at, destination - at);
        }
        current[destination] = value;
        return current;
    }

    private static void assertOrder(DialogFilterDefinition[] filters,
            int[] expected) throws Exception
    {
        int[] actual = ids(filters);
        if (actual.length != expected.length)
        {
            throw new Exception("folder order length mismatch");
        }
        for (int i = 0; i < actual.length; i++)
        {
            if (actual[i] != expected[i])
            {
                throw new Exception("folder order mismatch at " + i);
            }
        }
    }
}
