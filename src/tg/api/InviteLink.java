package tg.api;

import java.io.IOException;

/** Parser for the invite URL forms the J2ME UI accepts. */
public final class InviteLink
{
    private InviteLink() { }

    public static String hash(String value) throws IOException
    {
        if (value == null) { throw new IOException("invite link is empty"); }
        String s = value.trim();
        String lower = s.toLowerCase();
        String hash = null;
        if (lower.startsWith("tg://join?"))
        {
            int at = lower.indexOf("invite=");
            if (at >= 0) { hash = s.substring(at + 7); }
        }
        else
        {
            int scheme = lower.indexOf("://");
            if (scheme >= 0) { lower = lower.substring(scheme + 3); s = s.substring(scheme + 3); }
            if (lower.startsWith("www.")) { lower = lower.substring(4); s = s.substring(4); }
            String[] hosts = { "t.me/", "telegram.me/", "telegram.dog/" };
            for (int i = 0; i < hosts.length && hash == null; i++)
            {
                if (!lower.startsWith(hosts[i])) { continue; }
                String path = s.substring(hosts[i].length());
                String pathLower = lower.substring(hosts[i].length());
                if (pathLower.startsWith("+")) { hash = path.substring(1); }
                else if (pathLower.startsWith("joinchat/"))
                {
                    hash = path.substring(9);
                }
            }
        }
        if (hash == null) { throw new IOException("unsupported invite link"); }
        int cut = hash.length();
        int amp = hash.indexOf('&');
        int query = hash.indexOf('?');
        int fragment = hash.indexOf('#');
        if (amp >= 0 && amp < cut) { cut = amp; }
        if (query >= 0 && query < cut) { cut = query; }
        if (fragment >= 0 && fragment < cut) { cut = fragment; }
        hash = hash.substring(0, cut);
        if (hash.length() < 1 || hash.length() > 256)
        {
            throw new IOException("invalid invite hash length");
        }
        for (int i = 0; i < hash.length(); i++)
        {
            char c = hash.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-'))
            {
                throw new IOException("invalid invite hash");
            }
        }
        return hash;
    }
}
