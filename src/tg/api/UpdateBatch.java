package tg.api;

/**
 * A small immutable delivery from the update worker to the UI.
 *
 * A fullRefresh request is conservative: it is used when a server update
 * changes message data outside Phase 3's text/read subset.
 */
public final class UpdateBatch
{
    public Message[] messages = new Message[0];
    public Message[] edits = new Message[0];
    public ReadState[] reads = new ReadState[0];
    public ReactionUpdate[] reactions = new ReactionUpdate[0];
    public PollUpdate[] polls = new PollUpdate[0];
    public boolean fullRefresh;
    /** Main/archive ordering or membership changed. */
    public boolean dialogListsChanged;
    /** Custom folder definitions or their order changed. */
    public boolean folderDefinitionsChanged;
    /** Peers whose dialog metadata/order may have changed. */
    public Peer[] dialogPeers = new Peer[0];
    /** Peers whose basic/full chat information must be reloaded. */
    public Peer[] chatPeers = new Peer[0];
    /** Peers whose participant window must be reloaded. */
    public Peer[] participantPeers = new Peer[0];
    /** Peers whose exported links or pending requests changed. */
    public Peer[] invitePeers = new Peer[0];
    /** Forum peers whose topic page must be reloaded. */
    public Peer[] topicPeers = new Peer[0];
    public boolean chatInfoChanged;
    public boolean participantsChanged;
    public boolean inviteLinksChanged;
    public boolean joinRequestsChanged;
    public boolean forumTopicsChanged;
    /** Update payload was insufficient to reconcile the durable dialog row. */
    public boolean dialogIndexDirty;
    public String syncState;
    public String detail;
    /** Seconds until the next automatic sync attempt, or -1 when none. */
    public int retrySeconds = -1;
}
