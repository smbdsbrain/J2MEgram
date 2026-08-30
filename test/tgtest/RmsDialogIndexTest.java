package tgtest;

import tg.api.Dialog;
import tg.api.DialogFilterDefinition;
import tg.api.DialogIndexSnapshot;
import tg.api.Peer;
import tg.plat.RmsDialogIndex;

/** Durable folder index generations, isolation and damaged-row recovery. */
public final class RmsDialogIndexTest implements Test
{
    private static final long ACCOUNT = 9001;

    public String name() { return "folders/rms-dialog-index"; }

    public void run() throws Exception
    {
        cachedFiltersAndGenerationConverge();
        ownersAndEnvironmentsAreIsolated();
        oneDamagedRowDoesNotHideTheRest();
        interruptedReplaceAndDuplicatesResolveNewest();
    }

    private static void cachedFiltersAndGenerationConverge() throws Exception
    {
        FaultyRecords rms = new FaultyRecords();
        EmulatorRecords.swapIn(rms);
        try
        {
            RmsDialogIndex index = new RmsDialogIndex();
            DialogFilterDefinition filter = new DialogFilterDefinition();
            filter.id = 7;
            filter.title = "Unread groups";
            filter.groups = true;
            filter.excludeRead = true;
            Peer pinned = peer(Peer.CHAT, 2, "Pinned");
            filter.pinnedPeers = new Peer[] { pinned };
            index.saveFilters(ACCOUNT, false,
                    new DialogFilterDefinition[] { filter });

            DialogFilterDefinition[] cached = index.loadFilters(ACCOUNT, false);
            Assert.equal("one cached filter", 1, cached.length);
            Assert.equal("cached title", "Unread groups", cached[0].title);
            Assert.equal("cached pin", 2L, cached[0].pinnedPeers[0].id);

            int generation = index.beginGeneration(ACCOUNT, false);
            index.upsertPage(ACCOUNT, false, new Dialog[] {
                dialog(Peer.CHAT, 1, "Older", 10, 1),
                dialog(Peer.CHAT, 2, "Pinned", 5, 0),
                dialog(Peer.USER, 3, "Person", 30, 1)
            }, generation);
            DialogIndexSnapshot partial = index.query(ACCOUNT, false,
                    filter, 100);
            Assert.isTrue("scan is partial before commit", partial.partial);
            Assert.isFalse("partial is not exact", partial.exact);
            Assert.equal("pin bypasses unread rule", 2, partial.total);
            Assert.equal("pinned ordering", 2L, partial.dialogs[0].peer.id);

            index.completeGeneration(ACCOUNT, false, generation);
            DialogIndexSnapshot exact = index.query(ACCOUNT, false, filter, 100);
            Assert.isTrue("completed scan exact", exact.exact);
            Assert.isFalse("completed scan clean", exact.dirty);
            Assert.equal("exact total", 2, exact.total);
            DialogIndexSnapshot afterPin = index.queryAfter(ACCOUNT, false,
                    filter, 100, exact.dialogs[0]);
            Assert.equal("paged query keeps global total", 2,
                    afterPin.total);
            Assert.equal("paged query excludes its anchor", 1,
                    afterPin.dialogs.length);
            Assert.equal("paged query follows pinned order", 1L,
                    afterPin.dialogs[0].peer.id);

            int next = index.beginGeneration(ACCOUNT, false);
            index.upsertPage(ACCOUNT, false, new Dialog[] {
                dialog(Peer.CHAT, 2, "Pinned new", 50, 0)
            }, next);
            index.completeGeneration(ACCOUNT, false, next);
            exact = index.query(ACCOUNT, false, filter, 100);
            Assert.equal("absent generation row removed", 1, exact.total);
            Assert.equal("new row retained", "Pinned new",
                    exact.dialogs[0].peer.title);
        }
        finally { EmulatorRecords.restore(); }
    }

    private static void ownersAndEnvironmentsAreIsolated() throws Exception
    {
        FaultyRecords rms = new FaultyRecords();
        EmulatorRecords.swapIn(rms);
        try
        {
            RmsDialogIndex one = new RmsDialogIndex();
            int generation = one.beginGeneration(1, false);
            one.upsertPage(1, false,
                    new Dialog[] { dialog(Peer.CHAT, 11, "One", 1, 1) },
                    generation);
            one.completeGeneration(1, false, generation);

            RmsDialogIndex two = new RmsDialogIndex();
            DialogIndexSnapshot other = two.query(2, false, null, 100);
            Assert.equal("other account sees no rows", 0, other.total);
            Assert.isTrue("missing owner snapshot is partial", other.partial);

            generation = two.beginGeneration(2, true);
            two.upsertPage(2, true,
                    new Dialog[] { dialog(Peer.CHAT, 22, "Two", 2, 1) },
                    generation);
            two.completeGeneration(2, true, generation);
            two.clear();

            DialogIndexSnapshot original = one.query(1, false, null, 100);
            Assert.equal("clearing other owner preserves first", 1,
                    original.total);
            Assert.equal("first owner row", 11L, original.dialogs[0].peer.id);
        }
        finally { EmulatorRecords.restore(); }
    }

    private static void oneDamagedRowDoesNotHideTheRest() throws Exception
    {
        FaultyRecords rms = new FaultyRecords();
        EmulatorRecords.swapIn(rms);
        try
        {
            RmsDialogIndex index = new RmsDialogIndex();
            int generation = index.beginGeneration(ACCOUNT, false);
            index.upsertPage(ACCOUNT, false, new Dialog[] {
                dialog(Peer.CHAT, 31, "Good", 2, 1),
                dialog(Peer.CHAT, 32, "Damage me", 1, 1),
                dialog(Peer.CHAT, 33, "Truncate me", 3, 1)
            }, generation);
            index.completeGeneration(ACCOUNT, false, generation);

            int[] ids = rms.recordIds(RmsDialogIndex.STORE_NAME);
            int damaged = -1;
            int truncated = -1;
            for (int i = 0; i < ids.length; i++)
            {
                byte[] raw = rms.peek(RmsDialogIndex.STORE_NAME, ids[i]);
                if (raw.length > 28 && raw[24] == 2)
                {
                    if (damaged < 0) { damaged = ids[i]; }
                    else if (truncated < 0) { truncated = ids[i]; break; }
                }
            }
            Assert.isTrue("found row record", damaged > 0);
            Assert.isTrue("found second row record", truncated > 0);
            rms.flipBit(RmsDialogIndex.STORE_NAME, damaged, 28, 0);
            rms.truncate(RmsDialogIndex.STORE_NAME, truncated, 20);

            DialogIndexSnapshot recovered = index.query(ACCOUNT, false,
                    null, 100);
            Assert.equal("one valid row survives CRC isolation", 1,
                    recovered.total);
        }
        finally { EmulatorRecords.restore(); }
    }

    private static void interruptedReplaceAndDuplicatesResolveNewest()
            throws Exception
    {
        FaultyRecords rms = new FaultyRecords();
        EmulatorRecords.swapIn(rms);
        try
        {
            RmsDialogIndex index = new RmsDialogIndex();
            int generation = index.beginGeneration(ACCOUNT, false);
            index.upsertPage(ACCOUNT, false,
                    new Dialog[] { dialog(Peer.CHAT, 41, "Old", 1, 1) },
                    generation);
            index.completeGeneration(ACCOUNT, false, generation);

            rms.failAt(RmsDialogIndex.STORE_NAME, FaultyRecords.ADD, 1, true);
            try
            {
                index.upsertPage(ACCOUNT, false,
                        new Dialog[] { dialog(Peer.CHAT, 41, "New", 2, 1) }, 0);
                Assert.fail("injected interrupted replacement succeeded");
            }
            catch (java.io.IOException expected) { }
            rms.clearFaults();
            rms.restart();

            DialogIndexSnapshot recovered = new RmsDialogIndex().query(
                    ACCOUNT, false, null, 100);
            Assert.equal("interrupted replace yields one logical row", 1,
                    recovered.total);
            Assert.equal("add-before-delete keeps the new row", "New",
                    recovered.dialogs[0].peer.title);

            int[] ids = rms.recordIds(RmsDialogIndex.STORE_NAME);
            int row = -1;
            for (int i = 0; i < ids.length; i++)
            {
                byte[] raw = rms.peek(RmsDialogIndex.STORE_NAME, ids[i]);
                if (raw.length > 24 && raw[24] == 2) { row = ids[i]; }
            }
            Assert.isTrue("latest row found", row > 0);
            rms.duplicate(RmsDialogIndex.STORE_NAME, row);
            rms.enumerationOrder(FaultyRecords.DESCENDING);
            recovered = new RmsDialogIndex().query(ACCOUNT, false, null, 100);
            Assert.equal("duplicate rows are deduplicated in any RMS order", 1,
                    recovered.total);
            Assert.equal("duplicate retains content", "New",
                    recovered.dialogs[0].peer.title);
        }
        finally { EmulatorRecords.restore(); }
    }

    private static Peer peer(int kind, long id, String title)
    {
        Peer peer = new Peer(kind, id);
        peer.title = title;
        return peer;
    }

    private static Dialog dialog(int kind, long id, String title, int date,
            int unread)
    {
        Dialog dialog = new Dialog();
        dialog.peer = peer(kind, id, title);
        dialog.date = date;
        dialog.topMessageId = date;
        dialog.unreadCount = unread;
        return dialog;
    }
}
