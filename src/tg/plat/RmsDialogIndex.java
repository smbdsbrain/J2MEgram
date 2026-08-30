package tg.plat;

import java.io.IOException;
import java.util.Vector;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreNotFoundException;

import tg.api.AccountStore;
import tg.api.Dialog;
import tg.api.DialogFilterDefinition;
import tg.api.DialogFolderMatcher;
import tg.api.DialogIndexSnapshot;
import tg.api.Peer;
import tg.api.RecordEnvelope;
import tg.diag.Diag;
import tg.mem.MemoryBudget;
import tg.tl.TlReader;
import tg.tl.TlWriter;

/**
 * Account/environment-bound durable index used by custom dialog folders.
 *
 * Rows are independent RMS records.  Replacements are add-before-delete, so a
 * power cut leaves either the old row, the new row, or both; readers resolve a
 * duplicate by serial and record id.  A generation scan similarly commits its
 * exact meta record before stale rows are removed.  Therefore interruption can
 * make a snapshot partial, but cannot make a partial snapshot claim exactness.
 */
public final class RmsDialogIndex implements AccountStore
{
    public static final String STORE_NAME = "tgdialogindex";

    private static final int MAGIC = 0x54474449;       // TGDI
    private static final int VERSION = 1;
    private static final int META = 1;
    private static final int ROW = 2;
    private static final int FILTERS = 3;

    private static final int FLAG_DIRTY = 1;
    private static final int FLAG_PARTIAL = 2;
    private static final int MAX_ROWS = 1024;
    private static final int MAX_TOTAL_BYTES = 256 * 1024;
    private static final int MAX_RECORD = 2048;
    private static final int MAX_FILTER_RECORD = 64 * 1024;
    private static final int MAX_FILTERS = 64;
    private static final int MAX_FILTER_PEERS = 200;
    private static final int TITLE_MAX = 128;

    private long owner;
    private boolean testEnvironment;
    private long lastSerial;

    public synchronized void bindAccount(long accountId, boolean test)
    {
        if (accountId > 0)
        {
            owner = accountId;
            testEnvironment = test;
        }
    }

    private static final class Meta
    {
        long serial;
        int recordId;
        int generation;
        int completedGeneration;
        boolean dirty = true;
        boolean partial = true;
    }

    private static final class Row
    {
        long serial;
        int recordId;
        int seenGeneration;
        Dialog dialog;
    }

    public synchronized int beginGeneration(long accountId, boolean test)
            throws IOException
    {
        bind(accountId, test);
        RecordStore rs = null;
        try
        {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            Meta meta = loadMeta(rs);
            meta.generation = meta.generation == Integer.MAX_VALUE
                    ? 1 : meta.generation + 1;
            if (meta.generation <= 0) { meta.generation = 1; }
            meta.dirty = true;
            meta.partial = true;
            saveMeta(rs, meta);
            return meta.generation;
        }
        catch (Throwable t) { throw io("RMS dialog index generation", t); }
        finally { close(rs); }
    }

    public synchronized void upsertPage(long accountId, boolean test,
            Dialog[] dialogs, int generation) throws IOException
    {
        bind(accountId, test);
        if (dialogs == null) { return; }
        RecordStore rs = null;
        try
        {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            Meta meta = loadMeta(rs);
            int seen = generation > 0 ? generation : meta.completedGeneration;
            for (int i = 0; i < dialogs.length; i++)
            {
                if (dialogs[i] != null && dialogs[i].peer != null)
                {
                    replaceRow(rs, dialogs[i], seen);
                }
            }
            enforceCaps(rs, meta);
        }
        catch (Throwable t) { throw io("RMS dialog index upsert", t); }
        finally { close(rs); }
    }

    /** A complete main+archive scan is now authoritative. */
    public synchronized void completeGeneration(long accountId, boolean test,
            int generation) throws IOException
    {
        bind(accountId, test);
        RecordStore rs = null;
        try
        {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            Meta meta = loadMeta(rs);
            if (generation <= 0 || generation != meta.generation)
            {
                throw new IOException("stale dialog index generation");
            }

            // Commit exactness first. A power cut after this line leaves old
            // rows, so query treats rows from another generation as absent.
            meta.completedGeneration = generation;
            meta.dirty = false;
            meta.partial = false;
            saveMeta(rs, meta);
            deleteRowsOutsideGeneration(rs, generation);
            enforceCaps(rs, meta);
        }
        catch (Throwable t) { throw io("RMS dialog index commit", t); }
        finally { close(rs); }
    }

    public synchronized void markDirty(long accountId, boolean test)
            throws IOException
    {
        bind(accountId, test);
        RecordStore rs = null;
        try
        {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            Meta meta = loadMeta(rs);
            meta.dirty = true;
            meta.partial = true;
            saveMeta(rs, meta);
        }
        catch (Throwable t) { throw io("RMS dialog index dirty", t); }
        finally { close(rs); }
    }

    public synchronized DialogIndexSnapshot query(long accountId, boolean test,
            DialogFilterDefinition filter, int unixNow) throws IOException
    {
        return queryPage(accountId, test, filter, unixNow, null,
                Math.max(1, MemoryBudget.maxDialogs()));
    }

    /**
     * Return the next bounded run after {@code after} in folder order.
     *
     * The total still describes the complete matching index.  Only the retained
     * rows are paged, so callers can slide their UI window without materialising
     * all (up to 1024) logical records at once.
     */
    public synchronized DialogIndexSnapshot queryAfter(long accountId,
            boolean test, DialogFilterDefinition filter, int unixNow,
            Dialog after) throws IOException
    {
        return queryPage(accountId, test, filter, unixNow, after,
                Math.max(1, MemoryBudget.dialogPageSize()));
    }

    private DialogIndexSnapshot queryPage(long accountId, boolean test,
            DialogFilterDefinition filter, int unixNow, Dialog after,
            int limit) throws IOException
    {
        bind(accountId, test);
        DialogIndexSnapshot out = new DialogIndexSnapshot();
        RecordStore rs = null;
        RecordEnumeration en = null;
        try
        {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            Meta meta = loadMeta(rs);
            out.generation = meta.completedGeneration;
            out.dirty = meta.dirty;
            out.partial = meta.partial || meta.dirty;
            out.exact = !out.partial && meta.completedGeneration > 0;

            Vector rows = new Vector();
            Vector doomed = new Vector();
            en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement())
            {
                int id = en.nextRecordId();
                Row row = readRow(rs, id, doomed);
                if (row == null || !isLatestRow(rs, row)) { continue; }
                if (out.exact && row.seenGeneration != meta.completedGeneration)
                {
                    continue;
                }
                Dialog dialog = row.dialog;
                if (filter != null
                        && !DialogFolderMatcher.matches(dialog, filter, unixNow))
                {
                    continue;
                }
                dialog.pinned = filter != null
                        && filter.containsPinned(dialog.peer);
                out.total++;
                if (after != null && compare(dialog, after, filter) <= 0)
                {
                    continue;
                }
                insert(rows, dialog, filter, limit);
            }
            purge(rs, doomed);
            out.dialogs = new Dialog[rows.size()];
            rows.copyInto(out.dialogs);
            return out;
        }
        catch (Throwable t) { throw io("RMS dialog index query", t); }
        finally { destroy(en); close(rs); }
    }

    public synchronized void saveFilters(long accountId, boolean test,
            DialogFilterDefinition[] filters) throws IOException
    {
        bind(accountId, test);
        if (filters == null) { filters = new DialogFilterDefinition[0]; }
        int count = Math.min(filters.length, MAX_FILTERS);
        TlWriter w = new TlWriter(512);
        w.writeInt(FILTERS);
        long serial = serial();
        w.writeLong(serial);
        w.writeInt(count);
        for (int i = 0; i < count; i++) { writeFilter(w, filters[i]); }
        byte[] raw = envelope(w);
        if (raw.length > MAX_FILTER_RECORD)
        {
            throw new IOException("cached dialog filters are too large");
        }
        RecordStore rs = null;
        try
        {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            int fresh = rs.addRecord(raw, 0, raw.length);
            deleteTypeExcept(rs, FILTERS, fresh);
            enforceCaps(rs, loadMeta(rs));
        }
        catch (Throwable t) { throw io("RMS dialog filters save", t); }
        finally { close(rs); }
    }

    public synchronized DialogFilterDefinition[] loadFilters(long accountId,
            boolean test) throws IOException
    {
        bind(accountId, test);
        RecordStore rs = null;
        RecordEnumeration en = null;
        try
        {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            en = rs.enumerateRecords(null, null, false);
            long newest = Long.MIN_VALUE;
            int newestId = -1;
            DialogFilterDefinition[] found = new DialogFilterDefinition[0];
            Vector doomed = new Vector();
            while (en.hasNextElement())
            {
                int id = en.nextRecordId();
                byte[] payload = payload(rs, id, doomed);
                if (payload == null) { continue; }
                try
                {
                    TlReader r = new TlReader(payload);
                    if (r.readInt() != FILTERS) { continue; }
                    long serial = r.readLong();
                    int count = bounded(r.readInt(), MAX_FILTERS);
                    DialogFilterDefinition[] values =
                            new DialogFilterDefinition[count];
                    for (int i = 0; i < count; i++)
                    {
                        values[i] = readFilter(r);
                    }
                    if (serial > newest || serial == newest && id > newestId)
                    {
                        newest = serial;
                        newestId = id;
                        found = values;
                    }
                }
                catch (Throwable t) { doomed.addElement(new Integer(id)); }
            }
            purge(rs, doomed);
            return found;
        }
        catch (Throwable t) { throw io("RMS dialog filters load", t); }
        finally { destroy(en); close(rs); }
    }

    /** Remove this bound account/environment without touching another owner. */
    public synchronized void clear() throws IOException
    {
        if (owner == 0) { return; }
        RecordStore rs = null;
        RecordEnumeration en = null;
        try
        {
            try { rs = RecordStore.openRecordStore(STORE_NAME, false); }
            catch (RecordStoreNotFoundException absent) { return; }
            en = rs.enumerateRecords(null, null, false);
            Vector ids = new Vector();
            while (en.hasNextElement())
            {
                int id = en.nextRecordId();
                byte[] raw;
                try { raw = rs.getRecord(id); }
                catch (Throwable ignored) { continue; }
                RecordEnvelope envelope = RecordEnvelope.unwrap(raw, MAGIC,
                        VERSION, VERSION, owner, testEnvironment);
                if (envelope.isOk())
                {
                    ids.addElement(new Integer(id));
                }
            }
            purge(rs, ids);
        }
        catch (Throwable t) { throw io("RMS dialog index clear", t); }
        finally { destroy(en); close(rs); }
    }

    private void replaceRow(RecordStore rs, Dialog dialog, int generation)
            throws Exception
    {
        TlWriter w = new TlWriter(384);
        w.writeInt(ROW);
        w.writeLong(serial());
        w.writeInt(generation);
        writeDialog(w, dialog);
        byte[] raw = envelope(w);
        if (raw.length > MAX_RECORD) { throw new IOException("dialog row too large"); }
        int fresh = rs.addRecord(raw, 0, raw.length);
        deletePeerExcept(rs, dialog.peer, fresh);
    }

    private byte[] envelope(TlWriter writer) throws IOException
    {
        return RecordEnvelope.wrap(MAGIC, VERSION, owner, testEnvironment,
                writer.toByteArray());
    }

    private Meta loadMeta(RecordStore rs) throws Exception
    {
        Meta best = new Meta();
        RecordEnumeration en = null;
        Vector doomed = new Vector();
        try
        {
            en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement())
            {
                int id = en.nextRecordId();
                byte[] payload = payload(rs, id, doomed);
                if (payload == null) { continue; }
                try
                {
                    TlReader r = new TlReader(payload);
                    if (r.readInt() != META) { continue; }
                    Meta value = new Meta();
                    value.serial = r.readLong();
                    value.recordId = id;
                    value.generation = r.readInt();
                    value.completedGeneration = r.readInt();
                    int flags = r.readInt();
                    value.dirty = (flags & FLAG_DIRTY) != 0;
                    value.partial = (flags & FLAG_PARTIAL) != 0;
                    if (value.serial > best.serial
                            || value.serial == best.serial && id > best.recordId)
                    {
                        best = value;
                    }
                }
                catch (Throwable t) { doomed.addElement(new Integer(id)); }
            }
            purge(rs, doomed);
            return best;
        }
        finally { destroy(en); }
    }

    private void saveMeta(RecordStore rs, Meta meta) throws Exception
    {
        TlWriter w = new TlWriter(40);
        w.writeInt(META);
        meta.serial = serial();
        w.writeLong(meta.serial);
        w.writeInt(meta.generation);
        w.writeInt(meta.completedGeneration);
        w.writeInt((meta.dirty ? FLAG_DIRTY : 0)
                | (meta.partial ? FLAG_PARTIAL : 0));
        byte[] raw = envelope(w);
        int fresh = rs.addRecord(raw, 0, raw.length);
        meta.recordId = fresh;
        deleteTypeExcept(rs, META, fresh);
    }

    private Row readRow(RecordStore rs, int id, Vector doomed)
    {
        byte[] payload = payload(rs, id, doomed);
        if (payload == null) { return null; }
        try
        {
            TlReader r = new TlReader(payload);
            if (r.readInt() != ROW) { return null; }
            Row row = new Row();
            row.serial = r.readLong();
            row.recordId = id;
            row.seenGeneration = r.readInt();
            row.dialog = readDialog(r);
            if (row.dialog.peer == null) { throw new IOException("row without peer"); }
            return row;
        }
        catch (Throwable t)
        {
            doomed.addElement(new Integer(id));
            return null;
        }
    }

    private byte[] payload(RecordStore rs, int id, Vector doomed)
    {
        try
        {
            byte[] raw = rs.getRecord(id);
            RecordEnvelope envelope = RecordEnvelope.unwrap(raw, MAGIC,
                    VERSION, VERSION, owner, testEnvironment);
            if (envelope.isOk()) { return envelope.payload; }
            if (envelope.isOurs()) { doomed.addElement(new Integer(id)); }
        }
        catch (Throwable ignored) { }
        return null;
    }

    private boolean isLatestRow(RecordStore rs, Row wanted) throws Exception
    {
        RecordEnumeration en = null;
        try
        {
            en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement())
            {
                int id = en.nextRecordId();
                if (id == wanted.recordId) { continue; }
                Row other = readRow(rs, id, new Vector());
                if (other == null || !same(other.dialog.peer, wanted.dialog.peer))
                {
                    continue;
                }
                if (other.serial > wanted.serial
                        || other.serial == wanted.serial
                        && other.recordId > wanted.recordId)
                {
                    return false;
                }
            }
            return true;
        }
        finally { destroy(en); }
    }

    private void deletePeerExcept(RecordStore rs, Peer peer, int keep)
            throws Exception
    {
        RecordEnumeration en = null;
        Vector ids = new Vector();
        try
        {
            en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement())
            {
                int id = en.nextRecordId();
                if (id == keep) { continue; }
                Row row = readRow(rs, id, new Vector());
                if (row != null && same(row.dialog.peer, peer))
                {
                    ids.addElement(new Integer(id));
                }
            }
            purge(rs, ids);
        }
        finally { destroy(en); }
    }

    private void deleteTypeExcept(RecordStore rs, int type, int keep)
            throws Exception
    {
        RecordEnumeration en = null;
        Vector ids = new Vector();
        try
        {
            en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement())
            {
                int id = en.nextRecordId();
                if (id == keep) { continue; }
                byte[] payload = payload(rs, id, new Vector());
                if (payload == null) { continue; }
                try
                {
                    if (new TlReader(payload).readInt() == type)
                    {
                        ids.addElement(new Integer(id));
                    }
                }
                catch (Throwable ignored) { }
            }
            purge(rs, ids);
        }
        finally { destroy(en); }
    }

    private void deleteRowsOutsideGeneration(RecordStore rs, int generation)
            throws Exception
    {
        RecordEnumeration en = null;
        Vector ids = new Vector();
        try
        {
            en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement())
            {
                int id = en.nextRecordId();
                Row row = readRow(rs, id, new Vector());
                if (row != null && row.seenGeneration != generation)
                {
                    ids.addElement(new Integer(id));
                }
            }
            purge(rs, ids);
        }
        finally { destroy(en); }
    }

    private void enforceCaps(RecordStore rs, Meta meta) throws Exception
    {
        boolean evicted = false;
        DialogFilterDefinition[] filters = loadFiltersFrom(rs);
        while (logicalRows(rs) > MAX_ROWS || ownedBytes(rs) > MAX_TOTAL_BYTES)
        {
            Row oldest = oldestUnprotected(rs, filters);
            if (oldest == null) { break; }
            deletePeerExcept(rs, oldest.dialog.peer, -1);
            evicted = true;
        }
        if (evicted || logicalRows(rs) > MAX_ROWS
                || ownedBytes(rs) > MAX_TOTAL_BYTES)
        {
            meta.partial = true;
            saveMeta(rs, meta);
        }
    }

    private int logicalRows(RecordStore rs) throws Exception
    {
        int count = 0;
        RecordEnumeration en = null;
        try
        {
            en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement())
            {
                Row row = readRow(rs, en.nextRecordId(), new Vector());
                if (row != null && isLatestRow(rs, row)) { count++; }
            }
            return count;
        }
        finally { destroy(en); }
    }

    private int ownedBytes(RecordStore rs) throws Exception
    {
        int total = 0;
        RecordEnumeration en = null;
        try
        {
            en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement())
            {
                int id = en.nextRecordId();
                byte[] raw = rs.getRecord(id);
                RecordEnvelope envelope = RecordEnvelope.unwrap(raw, MAGIC,
                        VERSION, VERSION, owner, testEnvironment);
                if (envelope.isOk()) { total += raw.length; }
            }
            return total;
        }
        finally { destroy(en); }
    }

    private Row oldestUnprotected(RecordStore rs,
            DialogFilterDefinition[] filters) throws Exception
    {
        Row oldest = null;
        RecordEnumeration en = null;
        try
        {
            en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement())
            {
                Row row = readRow(rs, en.nextRecordId(), new Vector());
                if (row == null || !isLatestRow(rs, row)
                        || protectedPeer(row.dialog.peer, filters)) { continue; }
                if (oldest == null || row.dialog.date < oldest.dialog.date
                        || row.dialog.date == oldest.dialog.date
                        && row.serial < oldest.serial)
                {
                    oldest = row;
                }
            }
            return oldest;
        }
        finally { destroy(en); }
    }

    private DialogFilterDefinition[] loadFiltersFrom(RecordStore rs)
            throws Exception
    {
        RecordEnumeration en = null;
        long newest = Long.MIN_VALUE;
        int newestId = -1;
        DialogFilterDefinition[] found = new DialogFilterDefinition[0];
        try
        {
            en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement())
            {
                int id = en.nextRecordId();
                byte[] payload = payload(rs, id, new Vector());
                if (payload == null) { continue; }
                TlReader r = new TlReader(payload);
                if (r.readInt() != FILTERS) { continue; }
                long serial = r.readLong();
                int count = bounded(r.readInt(), MAX_FILTERS);
                DialogFilterDefinition[] values =
                        new DialogFilterDefinition[count];
                for (int i = 0; i < count; i++) { values[i] = readFilter(r); }
                if (serial > newest || serial == newest && id > newestId)
                {
                    newest = serial;
                    newestId = id;
                    found = values;
                }
            }
            return found;
        }
        finally { destroy(en); }
    }

    private static boolean protectedPeer(Peer peer,
            DialogFilterDefinition[] filters)
    {
        for (int i = 0; filters != null && i < filters.length; i++)
        {
            DialogFilterDefinition filter = filters[i];
            if (filter != null && (filter.containsPinned(peer)
                    || DialogFilterDefinition.indexOf(filter.includePeers, peer)
                            >= 0))
            {
                return true;
            }
        }
        return false;
    }

    private static void insert(Vector rows, Dialog value,
            DialogFilterDefinition filter, int limit)
    {
        int at = 0;
        while (at < rows.size() && compare((Dialog) rows.elementAt(at), value,
                filter) <= 0) { at++; }
        if (at >= limit && rows.size() >= limit) { return; }
        rows.insertElementAt(value, at);
        if (rows.size() > limit) { rows.removeElementAt(rows.size() - 1); }
    }

    private static int compare(Dialog a, Dialog b,
            DialogFilterDefinition filter)
    {
        int ap = filter == null ? -1
                : DialogFilterDefinition.indexOf(filter.pinnedPeers, a.peer);
        int bp = filter == null ? -1
                : DialogFilterDefinition.indexOf(filter.pinnedPeers, b.peer);
        if (ap >= 0 && bp < 0) { return -1; }
        if (ap < 0 && bp >= 0) { return 1; }
        if (ap >= 0 && bp >= 0) { return ap - bp; }
        if (a.date != b.date) { return a.date > b.date ? -1 : 1; }
        if (a.topMessageId != b.topMessageId)
        {
            return a.topMessageId > b.topMessageId ? -1 : 1;
        }
        if (a.peer == null || b.peer == null)
        {
            return a.peer == b.peer ? 0 : (a.peer == null ? 1 : -1);
        }
        if (a.peer.kind != b.peer.kind) { return a.peer.kind - b.peer.kind; }
        if (a.peer.id == b.peer.id) { return 0; }
        return a.peer.id < b.peer.id ? -1 : 1;
    }

    private static void writeDialog(TlWriter w, Dialog d)
    {
        writePeer(w, d.peer);
        w.writeInt(d.topMessageId);
        w.writeInt(d.unreadCount);
        w.writeInt((d.unreadMark ? 1 : 0)
                | (d.lastMessageOutgoing ? 2 : 0));
        w.writeInt(d.readInboxMaxId);
        w.writeInt(d.readOutboxMaxId);
        w.writeInt(d.channelPts);
        w.writeInt(d.folderId);
        w.writeInt(d.muteUntil);
        w.writeInt(d.date);
        w.writeString(clip(d.lastMessage, Dialog.PREVIEW_MAX));
    }

    private static Dialog readDialog(TlReader r) throws IOException
    {
        Dialog d = new Dialog();
        d.peer = readPeer(r);
        d.topMessageId = r.readInt();
        d.unreadCount = r.readInt();
        int flags = r.readInt();
        d.unreadMark = (flags & 1) != 0;
        d.lastMessageOutgoing = (flags & 2) != 0;
        d.readInboxMaxId = r.readInt();
        d.readOutboxMaxId = r.readInt();
        d.channelPts = r.readInt();
        d.folderId = r.readInt();
        d.muteUntil = r.readInt();
        d.date = r.readInt();
        d.lastMessage = clip(r.readString(), Dialog.PREVIEW_MAX);
        return d;
    }

    private static void writePeer(TlWriter w, Peer peer)
    {
        w.writeInt(peer.kind);
        w.writeLong(peer.id);
        w.writeLong(peer.accessHash);
        w.writeString(clip(peer.title, TITLE_MAX));
        w.writeString(clip(peer.username, TITLE_MAX));
        int flags = (peer.self ? 1 : 0) | (peer.contact ? 2 : 0)
                | (peer.bot ? 4 : 0) | (peer.broadcast ? 8 : 0)
                | (peer.megagroup ? 16 : 0) | (peer.forum ? 32 : 0);
        w.writeInt(flags);
    }

    private static Peer readPeer(TlReader r) throws IOException
    {
        int kind = r.readInt();
        if (kind < Peer.USER || kind > Peer.CHANNEL)
        {
            throw new IOException("invalid dialog peer kind");
        }
        Peer peer = new Peer(kind, r.readLong());
        peer.accessHash = r.readLong();
        peer.title = clip(r.readString(), TITLE_MAX);
        peer.username = empty(r.readString());
        int flags = r.readInt();
        peer.self = (flags & 1) != 0;
        peer.contact = (flags & 2) != 0;
        peer.bot = (flags & 4) != 0;
        peer.broadcast = (flags & 8) != 0;
        peer.megagroup = (flags & 16) != 0;
        peer.forum = (flags & 32) != 0;
        return peer;
    }

    private static void writeFilter(TlWriter w, DialogFilterDefinition f)
    {
        if (f == null) { f = new DialogFilterDefinition(); }
        w.writeInt(f.kind);
        w.writeInt(f.id);
        w.writeString(clip(f.title, TITLE_MAX));
        w.writeString(clip(f.emoticon, 32));
        w.writeInt(f.color);
        int flags = (f.titleNoAnimate ? 1 : 0) | (f.hasMyInvites ? 2 : 0)
                | (f.contacts ? 4 : 0) | (f.nonContacts ? 8 : 0)
                | (f.groups ? 16 : 0) | (f.broadcasts ? 32 : 0)
                | (f.bots ? 64 : 0) | (f.excludeMuted ? 128 : 0)
                | (f.excludeRead ? 256 : 0) | (f.excludeArchived ? 512 : 0);
        w.writeInt(flags);
        writePeers(w, f.pinnedPeers);
        writePeers(w, f.includePeers);
        writePeers(w, f.excludePeers);
    }

    private static DialogFilterDefinition readFilter(TlReader r)
            throws IOException
    {
        DialogFilterDefinition f = new DialogFilterDefinition();
        f.kind = r.readInt();
        f.id = r.readInt();
        f.title = clip(r.readString(), TITLE_MAX);
        f.emoticon = empty(r.readString());
        f.color = r.readInt();
        int flags = r.readInt();
        f.titleNoAnimate = (flags & 1) != 0;
        f.hasMyInvites = (flags & 2) != 0;
        f.contacts = (flags & 4) != 0;
        f.nonContacts = (flags & 8) != 0;
        f.groups = (flags & 16) != 0;
        f.broadcasts = (flags & 32) != 0;
        f.bots = (flags & 64) != 0;
        f.excludeMuted = (flags & 128) != 0;
        f.excludeRead = (flags & 256) != 0;
        f.excludeArchived = (flags & 512) != 0;
        f.pinnedPeers = readPeers(r);
        f.includePeers = readPeers(r);
        f.excludePeers = readPeers(r);
        return f;
    }

    private static void writePeers(TlWriter w, Peer[] peers)
    {
        int count = Math.min(peers == null ? 0 : peers.length,
                MAX_FILTER_PEERS);
        w.writeInt(count);
        for (int i = 0; i < count; i++) { writePeer(w, peers[i]); }
    }

    private static Peer[] readPeers(TlReader r) throws IOException
    {
        int count = bounded(r.readInt(), MAX_FILTER_PEERS);
        Peer[] out = new Peer[count];
        for (int i = 0; i < count; i++) { out[i] = readPeer(r); }
        return out;
    }

    private void bind(long accountId, boolean test) throws IOException
    {
        if (accountId <= 0) { throw new IOException("dialog index has no account"); }
        owner = accountId;
        testEnvironment = test;
    }

    private long serial()
    {
        long now = System.currentTimeMillis();
        if (now <= lastSerial) { now = lastSerial + 1; }
        lastSerial = now;
        return now;
    }

    private static boolean same(Peer a, Peer b)
    {
        return a != null && b != null && a.kind == b.kind && a.id == b.id;
    }

    private static String clip(String value, int max)
    {
        if (value == null) { return ""; }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String empty(String value)
    {
        return value == null || value.length() == 0 ? null : value;
    }

    private static int bounded(int value, int max) throws IOException
    {
        if (value < 0 || value > max) { throw new IOException("index count out of bounds"); }
        return value;
    }

    private static void purge(RecordStore rs, Vector ids)
    {
        for (int i = 0; i < ids.size(); i++)
        {
            try { rs.deleteRecord(((Integer) ids.elementAt(i)).intValue()); }
            catch (Throwable t) { Diag.warn("RMS dialog index purge failed"); }
        }
    }

    private static void destroy(RecordEnumeration en)
    {
        if (en != null) { try { en.destroy(); } catch (Throwable ignored) { } }
    }

    private static void close(RecordStore rs)
    {
        if (rs != null) { try { rs.closeRecordStore(); } catch (Throwable ignored) { } }
    }

    private static IOException io(String operation, Throwable t)
    {
        if (t instanceof IOException) { return (IOException) t; }
        return new IOException(operation + ": " + t.toString());
    }
}
