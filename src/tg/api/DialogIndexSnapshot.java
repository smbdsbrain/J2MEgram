package tg.api;

/** Bounded result returned by the durable dialog index. */
public final class DialogIndexSnapshot
{
    public Dialog[] dialogs = new Dialog[0];
    /** Exact only when {@link #exact} is true; otherwise a known lower bound. */
    public int total;
    public int generation;
    public boolean cached = true;
    public boolean partial = true;
    public boolean dirty = true;
    public boolean exact;
}
