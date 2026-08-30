package tgtest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import tg.api.ChatAdminRightsDef;
import tg.api.ChatDefaultPermissions;
import tg.api.ChatInfo;
import tg.api.ChatParticipant;
import tg.api.ChatParticipantPage;
import tg.api.Dialog;
import tg.api.DialogFilterDefinition;
import tg.api.ExportedInviteLink;
import tg.api.ExportedInviteLinkPage;
import tg.api.ForumTopic;
import tg.api.ForumTopicPage;
import tg.api.InviteResult;
import tg.api.JoinRequest;
import tg.api.JoinRequestPage;
import tg.api.Peer;
import tg.api.Telegram;
import tg.crypto.Rng;
import tg.mt.RpcError;

/**
 * Opt-in production probe for the community-administration fixture.
 *
 * Fixture names are command-line arguments and output is deliberately
 * sanitized: real titles, usernames and peer ids never enter a public log.
 */
public final class LiveCommunityAdministrationTest
{
    public static void main(String[] args) throws Exception
    {
        if (args.length < 3)
        {
            System.out.println("usage: community <mode> <group title>"
                    + " <username> [private state dir]");
            System.exit(2);
        }
        String mode = args[0];
        FileAuthKeyStore store = new FileAuthKeyStore();
        SeTransport transport = new SeTransport();
        transport.setReadTimeoutMs(60000);
        Telegram tg = new Telegram(transport, new Rng(), store);
        try
        {
            tg.connect();
            if (tg.checkAuthorization() == null)
            {
                throw new Exception("stored production session is not authorized");
            }
            Fixture fixture = new Fixture();
            fixture.community = exactCommunity(tg, args[1]);
            String username = args[2];
            if (username.startsWith("@")) { username = username.substring(1); }
            fixture.target = tg.resolveUsername(username);
            if (fixture.target == null || fixture.target.kind != Peer.USER)
            {
                throw new Exception("target username did not resolve to a user");
            }
            fixture.info = tg.getChatInfo(fixture.community);
            if ("probe".equals(mode)) { probe(tg, fixture); }
            else if ("full".equals(mode)) { full(tg, fixture); }
            else if ("audit".equals(mode)) { audit(tg, fixture); }
            else
            {
                if (args.length < 4) { throw new Exception("state dir required"); }
                File state = new File(args[3]);
                if (!state.isDirectory()) { throw new Exception("invalid state dir"); }
                if ("prepare".equals(mode)) { prepare(tg, fixture, state); }
                else if ("reject".equals(mode))
                {
                    decide(tg, fixture, false);
                }
                else if ("approve".equals(mode))
                {
                    decide(tg, fixture, true);
                }
                else if ("ban-cycle".equals(mode))
                {
                    banCycle(tg, fixture);
                }
                else if ("recover".equals(mode))
                {
                    recover(tg, fixture, state);
                }
                else if ("request-recover".equals(mode))
                {
                    write(new File(state, "target-action"), "request-1");
                    System.out.println("LIVE COMMUNITY REQUEST RECOVERY READY");
                }
                else if ("verify-member".equals(mode))
                {
                    awaitParticipant(tg, fixture, ChatParticipant.MEMBER,
                            ChatParticipant.ACTIVE);
                    System.out.println("LIVE COMMUNITY MEMBER VERIFIED");
                }
                else if ("ensure-member".equals(mode))
                {
                    ensureMember(tg, fixture);
                }
                else if ("cleanup".equals(mode))
                {
                    cleanupWithFloodRetry(tg, fixture, state);
                }
                else { throw new Exception("unknown community E2E mode"); }
            }
        }
        finally { tg.close(); }
    }

    private static final class Fixture
    {
        Peer community;
        Peer target;
        ChatInfo info;
    }

    private static void probe(Telegram tg, Fixture fixture) throws Exception
    {
            ChatParticipant participant = participant(tg, fixture,
                    ChatParticipantPage.MEMBERS);
            if (participant == null && fixture.community.kind == Peer.CHANNEL)
            {
                participant = participant(tg, fixture,
                        ChatParticipantPage.REMOVED);
            }
            ChatInfo info = fixture.info;
            System.out.println("LIVE COMMUNITY PROBE");
            System.out.println("  group=resolved,addressable");
            System.out.println("  type=" + info.typeLabel());
            System.out.println("  public-address="
                    + (fixture.community.username != null
                            && fixture.community.username.length() > 0));
            System.out.println("  target=" + status(participant));
            System.out.println("  view-participants="
                    + info.capabilities.canViewParticipants);
            System.out.println("  invite=" + info.capabilities.canInvite);
            System.out.println("  kick=" + info.capabilities.canKick);
            System.out.println("  ban=" + info.capabilities.canBan);
            System.out.println("  promote=" + info.capabilities.canPromote);
            System.out.println("  default-permissions="
                    + info.capabilities.canEditDefaultPermissions);
            System.out.println("  invite-links="
                    + info.capabilities.canManageInviteLinks);
            System.out.println("  join-requests="
                    + info.capabilities.canManageJoinRequests);
            System.out.println("  create-topics="
                    + info.capabilities.canCreateTopics);
            System.out.println("  manage-topics="
                    + info.capabilities.canManageTopics);
    }

    private static void audit(Telegram tg, Fixture fixture) throws Exception
    {
        ChatParticipant member = participant(tg, fixture,
                ChatParticipantPage.MEMBERS);
        require(member != null && member.role == ChatParticipant.MEMBER
                        && member.status == ChatParticipant.ACTIVE,
                "target membership was not restored");
        require(findJoinRequest(tg, fixture) == null,
                "target still has a pending join request");
        if (fixture.info.type == ChatInfo.FORUM)
        {
            require(!hasE2eTopics(tg, fixture.community),
                    "temporary forum topics remain");
        }
        require(!hasE2eInviteLinks(tg, fixture.community),
                "temporary invite links remain active");
        DialogFilterDefinition[] filters = tg.getDialogFilters();
        for (int i = 0; i < filters.length; i++)
        {
            String title = filters[i] == null ? null : filters[i].title;
            require(!"J2ME e2e".equals(title) && !"J2ME done".equals(title),
                    "temporary dialog folder remains");
        }
        System.out.println("LIVE COMMUNITY RESTORE AUDIT PASS");
    }

    /** All single-session P1/P2/P3 mutations, each restored before return. */
    private static void full(Telegram tg, Fixture fixture) throws Exception
    {
        requireCapabilities(fixture.info);
        ChatParticipant original = participant(tg, fixture,
                ChatParticipantPage.MEMBERS);
        if (original == null || original.role != ChatParticipant.MEMBER
                || !original.canEdit)
        {
            throw new Exception("target must start as an editable member");
        }
        ChatDefaultPermissions defaults = fixture.info.defaultPermissions.copy();
        boolean permissionsDirty = false;
        boolean adminDirty = false;
        ExportedInviteLink link = null;
        ForumTopic created = null;
        boolean generalHidden = false;
        boolean generalKnown = false;
        try
        {
            checkParticipantPaging(tg, fixture);
            folderLifecycle(tg, fixture.community);
            System.out.println("  folder lifecycle=pass");
            phaseCooldown();

            ChatAdminRightsDef rights = new ChatAdminRightsDef();
            rights.inviteUsers = true;
            tg.editChatAdmin(fixture.community, original, rights);
            cooldown();
            adminDirty = true;
            ChatParticipant promoted = awaitParticipant(tg, fixture,
                    ChatParticipant.ADMIN, ChatParticipant.ACTIVE);
            require(promoted.adminRights.inviteUsers,
                    "promoted right did not round-trip");
            tg.demoteChatAdmin(fixture.community, promoted);
            cooldown();
            adminDirty = false;
            awaitParticipant(tg, fixture, ChatParticipant.MEMBER,
                    ChatParticipant.ACTIVE);
            System.out.println("  admin rights=pass");
            phaseCooldown();

            ChatDefaultPermissions changed = defaults.copy();
            changed.sendPolls = !defaults.sendPolls;
            tg.editDefaultPermissions(fixture.community, changed);
            cooldown();
            permissionsDirty = true;
            require(tg.getChatInfo(fixture.community).defaultPermissions.sendPolls
                            == changed.sendPolls,
                    "default permissions did not round-trip");
            tg.editDefaultPermissions(fixture.community, defaults);
            cooldown();
            permissionsDirty = false;
            System.out.println("  default permissions=pass");
            phaseCooldown();

            String stamp = shortStamp();
            link = tg.createInviteLink(fixture.community,
                    "J2ME e2e " + stamp, false);
            cooldown();
            require(activeLink(tg, fixture.community, link.link),
                    "created invite link was not listed");
            tg.revokeInviteLink(fixture.community, link);
            cooldown();
            require(!activeLink(tg, fixture.community, link.link),
                    "revoked invite link remained active");
            link = null;
            System.out.println("  invite links=pass");
            phaseCooldown();

            if (fixture.info.type == ChatInfo.FORUM)
            {
                cleanupOrphanTopics(tg, fixture.community);
                ForumTopicPage before = tg.getForumTopics(fixture.community,
                        null, 100);
                ForumTopic general = topicById(before, ForumTopic.GENERAL_ID);
                require(general != null, "General topic is missing");
                generalKnown = true;
                generalHidden = general.hidden;
                String title = "J2ME e2e " + stamp;
                String renamed = "J2ME done " + stamp;
                tg.createForumTopic(fixture.community, title);
                cooldown();
                created = awaitTopic(tg, fixture.community, title, true);
                tg.renameForumTopic(fixture.community, created.id, renamed);
                cooldown();
                created = awaitTopic(tg, fixture.community, renamed, true);
                tg.setForumTopicClosed(fixture.community, created.id, true);
                cooldown();
                awaitTopicClosed(tg, fixture.community, created.id, true);
                tg.setForumTopicClosed(fixture.community, created.id, false);
                cooldown();
                awaitTopicClosed(tg, fixture.community, created.id, false);
                tg.setForumTopicPinned(fixture.community, created.id, true);
                cooldown();
                awaitTopicPinned(tg, fixture.community, created.id, true);
                tg.setForumTopicPinned(fixture.community, created.id, false);
                cooldown();
                awaitTopicPinned(tg, fixture.community, created.id, false);
                tg.setGeneralTopicHidden(fixture.community, !generalHidden);
                cooldown();
                awaitTopicHidden(tg, fixture.community,
                        ForumTopic.GENERAL_ID, !generalHidden);
                tg.setGeneralTopicHidden(fixture.community, generalHidden);
                cooldown();
                awaitTopicHidden(tg, fixture.community,
                        ForumTopic.GENERAL_ID, generalHidden);
                tg.deleteForumTopic(fixture.community, created.id);
                cooldown();
                awaitTopic(tg, fixture.community, renamed, false);
                created = null;
                require(!hasE2eTopics(tg, fixture.community),
                        "temporary forum topic cleanup failed");
                expectGeneralRestriction(tg, fixture.community);
                System.out.println("  forum topics=pass");
                phaseCooldown();
            }

            System.out.println("LIVE COMMUNITY FULL PASS");
        }
        finally
        {
            try { phaseCooldown(); }
            catch (Throwable ignored) { }
            if (created != null)
            {
                try
                {
                    tg.deleteForumTopic(fixture.community, created.id);
                    cooldown();
                }
                catch (Throwable ignored) { }
            }
            if (generalKnown)
            {
                try
                {
                    ForumTopic current = awaitTopicId(tg, fixture.community,
                            ForumTopic.GENERAL_ID);
                    if (current.hidden != generalHidden)
                    {
                        tg.setGeneralTopicHidden(fixture.community, generalHidden);
                        cooldown();
                        awaitTopicHidden(tg, fixture.community,
                                ForumTopic.GENERAL_ID, generalHidden);
                    }
                }
                catch (Throwable ignored) { }
            }
            if (link != null)
            {
                try { tg.revokeInviteLink(fixture.community, link); }
                catch (Throwable ignored) { }
            }
            if (permissionsDirty)
            {
                try
                {
                    tg.editDefaultPermissions(fixture.community, defaults);
                    cooldown();
                }
                catch (Throwable ignored) { }
            }
            if (adminDirty)
            {
                try
                {
                    ChatParticipant current = participant(tg, fixture,
                            ChatParticipantPage.MEMBERS);
                    if (current != null && current.role == ChatParticipant.ADMIN)
                    {
                        tg.demoteChatAdmin(fixture.community, current);
                        cooldown();
                    }
                }
                catch (Throwable ignored) { }
            }
            restoreMember(tg, fixture);
        }
    }

    /** Leave the target absent with three active links for the target UI role. */
    private static void prepare(Telegram tg, Fixture fixture, File state)
            throws Exception
    {
        ChatParticipant member = participant(tg, fixture,
                ChatParticipantPage.MEMBERS);
        if (member == null || member.role != ChatParticipant.MEMBER)
        {
            throw new Exception("target is not a member before request flow");
        }
        ExportedInviteLink request1 = null;
        ExportedInviteLink request2 = null;
        ExportedInviteLink direct = null;
        boolean success = false;
        try
        {
            String stamp = shortStamp();
            request1 = tg.createInviteLink(fixture.community,
                    "J2ME req1 " + stamp, true);
            cooldown();
            write(new File(state, "request-link-1"), request1.link);
            request2 = tg.createInviteLink(fixture.community,
                    "J2ME req2 " + stamp, true);
            cooldown();
            write(new File(state, "request-link-2"), request2.link);
            direct = tg.createInviteLink(fixture.community,
                    "J2ME join " + stamp, false);
            cooldown();
            write(new File(state, "direct-link"), direct.link);
            write(new File(state, "chat-title"), fixture.community.title);
            write(new File(state, "target-id"),
                    String.valueOf(fixture.community.id));
            write(new File(state, "public-address"),
                    fixture.community.username == null
                            || fixture.community.username.length() == 0
                            ? "false" : "true");
            tg.kickChatUser(fixture.community, fixture.target);
            cooldown();
            awaitAbsent(tg, fixture);
            success = true;
            System.out.println("LIVE COMMUNITY REQUEST PREPARED");
        }
        finally
        {
            if (!success)
            {
                revoke(tg, fixture.community, request1);
                revoke(tg, fixture.community, request2);
                revoke(tg, fixture.community, direct);
                restoreMember(tg, fixture);
            }
        }
    }

    private static void decide(Telegram tg, Fixture fixture, boolean approved)
            throws Exception
    {
        JoinRequest request = awaitJoinRequest(tg, fixture, true);
        tg.decideJoinRequest(fixture.community, request.user, approved);
        cooldown();
        awaitJoinRequest(tg, fixture, false);
        if (approved)
        {
            awaitParticipant(tg, fixture, ChatParticipant.MEMBER,
                    ChatParticipant.ACTIVE);
        }
        else { awaitAbsent(tg, fixture); }
        System.out.println(approved ? "LIVE COMMUNITY REQUEST APPROVED"
                : "LIVE COMMUNITY REQUEST REJECTED");
    }

    /** Ban and unban leave the target absent for a packaged private rejoin. */
    private static void banCycle(Telegram tg, Fixture fixture)
            throws Exception
    {
        phaseCooldown();
        ChatParticipant member = participant(tg, fixture,
                ChatParticipantPage.MEMBERS);
        require(member != null && member.role == ChatParticipant.MEMBER,
                "target must be a member before ban cycle");
        boolean banned = false;
        try
        {
            tg.banChatUser(fixture.community, fixture.target);
            banned = true;
            cooldown();
            awaitParticipant(tg, fixture, ChatParticipant.MEMBER,
                    ChatParticipant.REMOVED);
            tg.unbanChatUser(fixture.community, fixture.target);
            banned = false;
            cooldown();
            awaitAbsent(tg, fixture);
            System.out.println("LIVE COMMUNITY BAN CYCLE PASS");
        }
        finally
        {
            if (banned)
            {
                try
                {
                    phaseCooldown();
                    tg.unbanChatUser(fixture.community, fixture.target);
                    cooldown();
                }
                catch (Throwable ignored) { }
            }
        }
    }

    /** Create a private recovery link without assuming direct-invite privacy. */
    private static void recover(Telegram tg, Fixture fixture, File state)
            throws Exception
    {
        ChatParticipant member = participant(tg, fixture,
                ChatParticipantPage.MEMBERS);
        if (member != null)
        {
            System.out.println("LIVE COMMUNITY RECOVERY NOT NEEDED");
            return;
        }
        if (fixture.community.kind == Peer.CHANNEL)
        {
            ChatParticipant removed = participant(tg, fixture,
                    ChatParticipantPage.REMOVED);
            if (removed != null)
            {
                tg.unbanChatUser(fixture.community, fixture.target);
                cooldown();
                awaitAbsent(tg, fixture);
            }
        }
        ExportedInviteLink direct = tg.createInviteLink(fixture.community,
                "J2ME recover " + shortStamp(), false);
        cooldown();
        write(new File(state, "direct-link"), direct.link);
        write(new File(state, "chat-title"), fixture.community.title);
        write(new File(state, "target-id"),
                String.valueOf(fixture.community.id));
        write(new File(state, "public-address"), "false");
        write(new File(state, "target-action"), "direct-join");
        System.out.println("LIVE COMMUNITY RECOVERY PREPARED");
    }

    private static void ensureMember(Telegram tg, Fixture fixture)
            throws Exception
    {
        ChatParticipant member = participant(tg, fixture,
                ChatParticipantPage.MEMBERS);
        if (member == null)
        {
            JoinRequest pending = awaitJoinRequest(tg, fixture, true);
            tg.decideJoinRequest(fixture.community, pending.user, true);
            cooldown();
        }
        awaitParticipant(tg, fixture, ChatParticipant.MEMBER,
                ChatParticipant.ACTIVE);
        System.out.println("LIVE COMMUNITY MEMBER ENSURED");
    }

    private static void cleanup(Telegram tg, Fixture fixture, File state)
            throws Exception
    {
        phaseCooldown();
        try
        {
            JoinRequest pending = findJoinRequest(tg, fixture);
            if (pending != null)
            {
                tg.decideJoinRequest(fixture.community, pending.user, false);
            }
        }
        catch (Throwable ignored) { }
        restoreMember(tg, fixture);
        ExportedInviteLink request1 = readLink(state, "request-link-1");
        ExportedInviteLink request2 = readLink(state, "request-link-2");
        ExportedInviteLink direct = readLink(state, "direct-link");
        ExportedInviteLink[] links = new ExportedInviteLink[] {
                request1, request2, direct };
        boolean[] active = activeLinkStates(tg, fixture.community, links);
        for (int i = 0; i < links.length; i++)
        {
            if (active[i])
            {
                tg.revokeInviteLink(fixture.community, links[i]);
                Thread.sleep(20000);
            }
        }
        Thread.sleep(20000);
        active = activeLinkStates(tg, fixture.community, links);
        require(request1 == null || !active[0],
                "first request link cleanup failed");
        require(request2 == null || !active[1],
                "second request link cleanup failed");
        require(direct == null || !active[2],
                "direct link cleanup failed");
        delete(new File(state, "request-link-1"));
        delete(new File(state, "request-link-2"));
        delete(new File(state, "direct-link"));
        awaitParticipant(tg, fixture, ChatParticipant.MEMBER,
                ChatParticipant.ACTIVE);
        write(new File(state, "cleanup-complete"), "ok");
        System.out.println("LIVE COMMUNITY CLEANUP PASS");
    }

    private static void cleanupWithFloodRetry(Telegram tg, Fixture fixture,
            File state) throws Exception
    {
        for (int attempt = 0; attempt < 8; attempt++)
        {
            try
            {
                cleanup(tg, fixture, state);
                return;
            }
            catch (RpcError error)
            {
                if (!error.isFloodWait() || attempt == 7) { throw error; }
                Thread.sleep((error.floodWaitSeconds() + 15) * 1000L);
            }
        }
    }

    private static void requireCapabilities(ChatInfo info) throws Exception
    {
        require(info != null && info.capabilities.canViewParticipants,
                "participant list is unavailable");
        require(info.capabilities.canInvite, "invite capability is unavailable");
        require(info.capabilities.canKick, "kick capability is unavailable");
        require(info.capabilities.canBan && info.capabilities.canUnban,
                "ban capability is unavailable");
        require(info.capabilities.canPromote,
                "admin editing capability is unavailable");
        require(info.capabilities.canEditDefaultPermissions,
                "default permissions capability is unavailable");
        require(info.capabilities.canManageInviteLinks,
                "invite-link capability is unavailable");
        require(info.capabilities.canManageJoinRequests,
                "join-request capability is unavailable");
        if (info.type == ChatInfo.FORUM)
        {
            require(info.capabilities.canCreateTopics
                            && info.capabilities.canManageTopics,
                    "forum administration capability is unavailable");
        }
    }

    private static void checkParticipantPaging(Telegram tg, Fixture fixture)
            throws Exception
    {
        if (fixture.community.kind != Peer.CHANNEL) { return; }
        ChatParticipantPage first = tg.getChannelParticipants(
                fixture.community, ChatParticipantPage.MEMBERS, 0, 20);
        require(first.participants.length <= 20,
                "participant page exceeded its bound");
        if (!first.exhausted && first.participants.length > 0)
        {
            ChatParticipantPage second = tg.getChannelParticipants(
                    fixture.community, ChatParticipantPage.MEMBERS,
                    first.participants.length, 20);
            for (int i = 0; i < first.participants.length; i++)
            {
                require(find(second.participants,
                        first.participants[i].peer) == null,
                        "participant pages overlapped");
            }
        }
    }

    private static void folderLifecycle(Telegram tg, Peer community)
            throws Exception
    {
        Dialog initial = dialog(tg, community);
        boolean originalPinned = initial.pinned;
        int originalFolder = initial.folderId;
        boolean pinDirty = false;
        boolean folderDirty = false;
        int disposableId = -1;
        boolean disposableExists = false;
        int[] originalOrder = null;
        try
        {
            tg.toggleDialogPin(community, !originalPinned);
            cooldown();
            pinDirty = true;
            awaitDialog(tg, community, !originalPinned, originalFolder);
            tg.toggleDialogPin(community, originalPinned);
            cooldown();
            pinDirty = false;
            awaitDialog(tg, community, originalPinned, originalFolder);

            int movedFolder = originalFolder == 1 ? 0 : 1;
            tg.editPeerFolder(community, movedFolder);
            cooldown();
            folderDirty = true;
            awaitDialog(tg, community, originalPinned, movedFolder);
            tg.editPeerFolder(community, originalFolder);
            cooldown();
            folderDirty = false;
            awaitDialog(tg, community, originalPinned, originalFolder);

            DialogFilterDefinition[] before = tg.getDialogFilters();
            originalOrder = filterIds(before);
            disposableId = firstFreeFilterId(before);
            DialogFilterDefinition filter = new DialogFilterDefinition();
            filter.id = disposableId;
            filter.title = "J2ME e2e";
            filter.setPinned(community, true);
            tg.updateDialogFilter(filter);
            cooldown();
            disposableExists = true;
            DialogFilterDefinition created = findFilter(
                    tg.getDialogFilters(), disposableId);
            require(created != null && created.containsPinned(community),
                    "created folder did not retain pinned membership");
            require(!containsExact(created.includePeers, community),
                    "folder pin leaked into explicit includes");

            filter.title = "J2ME done";
            filter.setPinned(community, false);
            filter.setIncluded(community, true);
            tg.updateDialogFilter(filter);
            cooldown();
            DialogFilterDefinition updated = findFilter(
                    tg.getDialogFilters(), disposableId);
            require(updated != null && !updated.containsPinned(community)
                            && containsExact(updated.includePeers, community),
                    "folder update did not preserve include/pin semantics");

            int[] reordered = moveFilter(tg.getDialogFilters(), disposableId);
            tg.updateDialogFiltersOrder(reordered);
            cooldown();
            require(equalFilterOrder(tg.getDialogFilters(), reordered),
                    "folder reorder did not round-trip");
            tg.deleteDialogFilter(disposableId);
            cooldown();
            disposableExists = false;
            require(findFilter(tg.getDialogFilters(), disposableId) == null,
                    "deleted folder remained present");
            tg.updateDialogFiltersOrder(originalOrder);
            cooldown();
            require(equalFilterOrder(tg.getDialogFilters(), originalOrder),
                    "folder order did not restore");
        }
        finally
        {
            if (disposableExists && disposableId >= 0)
            {
                try { tg.deleteDialogFilter(disposableId); }
                catch (Throwable ignored) { }
            }
            if (originalOrder != null)
            {
                try { tg.updateDialogFiltersOrder(originalOrder); }
                catch (Throwable ignored) { }
            }
            if (folderDirty)
            {
                try { tg.editPeerFolder(community, originalFolder); }
                catch (Throwable ignored) { }
            }
            if (pinDirty)
            {
                try { tg.toggleDialogPin(community, originalPinned); }
                catch (Throwable ignored) { }
            }
            awaitDialog(tg, community, originalPinned, originalFolder);
            if (disposableId >= 0)
            {
                require(findFilter(tg.getDialogFilters(), disposableId) == null,
                        "disposable folder cleanup failed");
            }
            if (originalOrder != null)
            {
                require(equalFilterOrder(tg.getDialogFilters(), originalOrder),
                        "folder-order cleanup failed");
            }
        }
    }

    private static Dialog dialog(Telegram tg, Peer peer) throws Exception
    {
        Dialog[] rows = tg.getPeerDialogs(new Peer[] { peer }).dialogs;
        if (rows.length != 1) { throw new Exception("dialog lookup failed"); }
        return rows[0];
    }

    private static Dialog awaitDialog(Telegram tg, Peer peer, boolean pinned,
            int folder) throws Exception
    {
        for (int i = 0; i < 24; i++)
        {
            Dialog row = dialog(tg, peer);
            if (row.pinned == pinned && row.folderId == folder) { return row; }
            Thread.sleep(500);
        }
        throw new Exception("dialog state did not converge");
    }

    private static DialogFilterDefinition findFilter(
            DialogFilterDefinition[] filters, int id)
    {
        for (int i = 0; i < filters.length; i++)
        {
            if (filters[i] != null && filters[i].id == id) { return filters[i]; }
        }
        return null;
    }

    private static int firstFreeFilterId(DialogFilterDefinition[] filters)
            throws Exception
    {
        for (int id = 2; id < 256; id++)
        {
            if (findFilter(filters, id) == null) { return id; }
        }
        throw new Exception("no disposable folder id is available");
    }

    private static int[] filterIds(DialogFilterDefinition[] filters)
    {
        int[] ids = new int[filters.length];
        for (int i = 0; i < filters.length; i++) { ids[i] = filters[i].id; }
        return ids;
    }

    private static int[] moveFilter(DialogFilterDefinition[] filters, int id)
    {
        int[] current = filterIds(filters);
        int at = -1;
        int destination = -1;
        for (int i = 0; i < current.length; i++)
        {
            if (current[i] == id) { at = i; }
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

    private static boolean equalFilterOrder(DialogFilterDefinition[] filters,
            int[] expected)
    {
        int[] actual = filterIds(filters);
        if (actual.length != expected.length) { return false; }
        for (int i = 0; i < actual.length; i++)
        {
            if (actual[i] != expected[i]) { return false; }
        }
        return true;
    }

    private static boolean containsExact(Peer[] peers, Peer wanted)
    {
        for (int i = 0; peers != null && i < peers.length; i++)
        {
            if (peers[i] != null && peers[i].kind == wanted.kind
                    && peers[i].id == wanted.id) { return true; }
        }
        return false;
    }

    private static ChatParticipant participant(Telegram tg, Fixture fixture,
            int filter) throws Exception
    {
        return findParticipant(tg, fixture.community, fixture.target, filter);
    }

    private static ChatParticipant awaitParticipant(Telegram tg,
            Fixture fixture, int role, int status) throws Exception
    {
        for (int i = 0; i < 24; i++)
        {
            ChatParticipant row = participant(tg, fixture,
                    status == ChatParticipant.REMOVED
                            ? ChatParticipantPage.REMOVED
                            : ChatParticipantPage.MEMBERS);
            if (row != null && row.role == role && row.status == status)
            {
                return row;
            }
            Thread.sleep(500);
        }
        throw new Exception("participant state did not converge");
    }

    private static void awaitAbsent(Telegram tg, Fixture fixture)
            throws Exception
    {
        for (int i = 0; i < 24; i++)
        {
            ChatParticipant member = participant(tg, fixture,
                    ChatParticipantPage.MEMBERS);
            ChatParticipant removed = fixture.community.kind == Peer.CHANNEL
                    ? participant(tg, fixture, ChatParticipantPage.REMOVED)
                    : null;
            if (member == null && removed == null) { return; }
            Thread.sleep(500);
        }
        throw new Exception("participant did not become absent");
    }

    private static void invite(Telegram tg, Fixture fixture) throws Exception
    {
        InviteResult result = tg.inviteUser(fixture.community, fixture.target);
        require(result != null && result.complete(),
                "target privacy settings refused restoration invite");
    }

    private static void restoreMember(Telegram tg, Fixture fixture)
            throws Exception
    {
        ChatParticipant member = participant(tg, fixture,
                ChatParticipantPage.MEMBERS);
        if (member != null)
        {
            if (member.role == ChatParticipant.ADMIN && member.canEdit)
            {
                tg.demoteChatAdmin(fixture.community, member);
                cooldown();
                awaitParticipant(tg, fixture, ChatParticipant.MEMBER,
                        ChatParticipant.ACTIVE);
            }
            return;
        }
        if (fixture.community.kind == Peer.CHANNEL)
        {
            ChatParticipant removed = participant(tg, fixture,
                    ChatParticipantPage.REMOVED);
            if (removed != null)
            {
                tg.unbanChatUser(fixture.community, fixture.target);
                cooldown();
                awaitAbsent(tg, fixture);
            }
        }
        invite(tg, fixture);
        cooldown();
        awaitParticipant(tg, fixture, ChatParticipant.MEMBER,
                ChatParticipant.ACTIVE);
    }

    private static boolean activeLink(Telegram tg, Peer community,
            String wanted) throws Exception
    {
        ExportedInviteLink offset = null;
        for (int pages = 0; pages < 32; pages++)
        {
            ExportedInviteLinkPage page = tg.getInviteLinks(community, offset,
                    100);
            for (int i = 0; i < page.links.length; i++)
            {
                if (wanted.equals(page.links[i].link)) { return true; }
            }
            if (page.exhausted || page.links.length == 0) { return false; }
            ExportedInviteLink next = page.last();
            if (offset != null && offset.link.equals(next.link)) { return false; }
            offset = next;
        }
        throw new Exception("invite-link pages did not converge");
    }

    private static boolean[] activeLinkStates(Telegram tg, Peer community,
            ExportedInviteLink[] wanted) throws Exception
    {
        boolean[] found = new boolean[wanted.length];
        ExportedInviteLink offset = null;
        for (int pages = 0; pages < 32; pages++)
        {
            ExportedInviteLinkPage page = tg.getInviteLinks(community, offset,
                    100);
            for (int i = 0; i < page.links.length; i++)
            {
                for (int j = 0; j < wanted.length; j++)
                {
                    if (wanted[j] != null && wanted[j].link != null
                            && wanted[j].link.equals(page.links[i].link))
                    {
                        found[j] = true;
                    }
                }
            }
            if (page.exhausted || page.links.length == 0) { return found; }
            ExportedInviteLink next = page.last();
            if (offset != null && offset.link.equals(next.link)) { return found; }
            offset = next;
        }
        throw new Exception("invite-link state scan did not converge");
    }

    private static boolean hasE2eInviteLinks(Telegram tg, Peer community)
            throws Exception
    {
        ExportedInviteLink offset = null;
        for (int pages = 0; pages < 32; pages++)
        {
            ExportedInviteLinkPage page = tg.getInviteLinks(community, offset,
                    100);
            for (int i = 0; i < page.links.length; i++)
            {
                String title = page.links[i] == null ? null : page.links[i].title;
                if (title != null && (title.startsWith("J2ME e2e ")
                        || title.startsWith("J2ME req")
                        || title.startsWith("J2ME join ")
                        || title.startsWith("J2ME recover "))) { return true; }
            }
            if (page.exhausted || page.links.length == 0) { return false; }
            ExportedInviteLink next = page.last();
            if (offset != null && offset.link.equals(next.link)) { return false; }
            offset = next;
        }
        throw new Exception("invite-link audit did not converge");
    }

    private static void revoke(Telegram tg, Peer community,
            ExportedInviteLink link) throws Exception
    {
        if (link == null || link.link == null || link.link.length() == 0) { return; }
        if (activeLink(tg, community, link.link))
        {
            tg.revokeInviteLink(community, link);
            cooldown();
        }
    }

    private static ExportedInviteLink readLink(File state, String name)
    {
        try
        {
            String value = read(new File(state, name));
            if (value.length() == 0) { return null; }
            ExportedInviteLink link = new ExportedInviteLink();
            link.link = value;
            return link;
        }
        catch (Throwable ignored) { return null; }
    }

    private static ForumTopic awaitTopic(Telegram tg, Peer forum,
            String title, boolean present) throws Exception
    {
        for (int i = 0; i < 24; i++)
        {
            ForumTopicPage page = tg.getForumTopics(forum, null, 100);
            ForumTopic found = topicByTitle(page, title);
            if (present && found != null) { return found; }
            if (!present && found == null) { return null; }
            Thread.sleep(500);
        }
        throw new Exception("forum topic state did not converge");
    }

    private static ForumTopic awaitTopicId(Telegram tg, Peer forum, int id)
            throws Exception
    {
        for (int i = 0; i < 24; i++)
        {
            ForumTopic found = topicById(tg.getForumTopics(forum, null, 100), id);
            if (found != null) { return found; }
            Thread.sleep(500);
        }
        throw new Exception("forum topic id disappeared");
    }

    private static ForumTopic awaitTopicClosed(Telegram tg, Peer forum,
            int id, boolean closed) throws Exception
    {
        return awaitTopicFlag(tg, forum, id, closed, 0);
    }

    private static ForumTopic awaitTopicPinned(Telegram tg, Peer forum,
            int id, boolean pinned) throws Exception
    {
        return awaitTopicFlag(tg, forum, id, pinned, 1);
    }

    private static ForumTopic awaitTopicHidden(Telegram tg, Peer forum,
            int id, boolean hidden) throws Exception
    {
        return awaitTopicFlag(tg, forum, id, hidden, 2);
    }

    private static ForumTopic awaitTopicFlag(Telegram tg, Peer forum, int id,
            boolean wanted, int flag) throws Exception
    {
        for (int i = 0; i < 24; i++)
        {
            ForumTopic found = topicById(
                    tg.getForumTopics(forum, null, 100), id);
            if (found != null)
            {
                boolean actual = flag == 0 ? found.closed
                        : flag == 1 ? found.pinned : found.hidden;
                if (actual == wanted) { return found; }
            }
            Thread.sleep(500);
        }
        throw new Exception("forum topic flag did not converge");
    }

    private static ForumTopic topicByTitle(ForumTopicPage page, String title)
    {
        for (int i = 0; page != null && i < page.topics.length; i++)
        {
            if (page.topics[i] != null && title.equals(page.topics[i].title))
            {
                return page.topics[i];
            }
        }
        return null;
    }

    private static ForumTopic topicById(ForumTopicPage page, int id)
    {
        for (int i = 0; page != null && i < page.topics.length; i++)
        {
            if (page.topics[i] != null && page.topics[i].id == id)
            {
                return page.topics[i];
            }
        }
        return null;
    }

    private static void cleanupOrphanTopics(Telegram tg, Peer forum)
            throws Exception
    {
        ForumTopicPage page = tg.getForumTopics(forum, null, 100);
        for (int i = 0; i < page.topics.length; i++)
        {
            ForumTopic topic = page.topics[i];
            if (topic != null && topic.id != ForumTopic.GENERAL_ID
                    && isE2eTopic(topic.title))
            {
                tg.deleteForumTopic(forum, topic.id);
                cooldown();
            }
        }
        require(!hasE2eTopics(tg, forum),
                "orphan forum topic cleanup failed");
    }

    private static boolean hasE2eTopics(Telegram tg, Peer forum)
            throws Exception
    {
        ForumTopicPage page = tg.getForumTopics(forum, null, 100);
        for (int i = 0; i < page.topics.length; i++)
        {
            ForumTopic topic = page.topics[i];
            if (topic != null && topic.id != ForumTopic.GENERAL_ID
                    && isE2eTopic(topic.title)) { return true; }
        }
        return false;
    }

    private static boolean isE2eTopic(String title)
    {
        return title != null && (title.startsWith("J2ME e2e ")
                || title.startsWith("J2ME done "));
    }

    private static void expectGeneralRestriction(Telegram tg, Peer forum)
            throws Exception
    {
        boolean closeRejected = false;
        boolean deleteRejected = false;
        try { tg.setForumTopicClosed(forum, ForumTopic.GENERAL_ID, true); }
        catch (Exception expected) { closeRejected = true; }
        try { tg.deleteForumTopic(forum, ForumTopic.GENERAL_ID); }
        catch (Exception expected) { deleteRejected = true; }
        require(closeRejected && deleteRejected,
                "General restrictions were not enforced locally");
    }

    private static JoinRequest awaitJoinRequest(Telegram tg, Fixture fixture,
            boolean present) throws Exception
    {
        for (int i = 0; i < 40; i++)
        {
            JoinRequest found = findJoinRequest(tg, fixture);
            if (present && found != null) { return found; }
            if (!present && found == null) { return null; }
            Thread.sleep(500);
        }
        throw new Exception("join-request state did not converge");
    }

    private static JoinRequest findJoinRequest(Telegram tg, Fixture fixture)
            throws Exception
    {
        JoinRequest offset = null;
        for (int pages = 0; pages < 64; pages++)
        {
            JoinRequestPage page = tg.getJoinRequests(fixture.community,
                    offset, 100);
            for (int i = 0; i < page.requests.length; i++)
            {
                Peer user = page.requests[i].user;
                if (user != null && user.id == fixture.target.id) return page.requests[i];
            }
            if (page.exhausted || page.requests.length == 0) { return null; }
            JoinRequest next = page.last();
            if (offset != null && next.user.id == offset.user.id
                    && next.date == offset.date) { return null; }
            offset = next;
        }
        throw new Exception("join-request pages did not converge");
    }

    private static String shortStamp()
    {
        String value = String.valueOf(System.currentTimeMillis());
        return value.substring(Math.max(0, value.length() - 6));
    }

    private static void cooldown() throws InterruptedException
    {
        Thread.sleep(6000);
    }

    private static void phaseCooldown() throws InterruptedException
    {
        Thread.sleep(8000);
    }

    private static void require(boolean value, String message) throws Exception
    {
        if (!value) { throw new Exception(message); }
    }

    private static void write(File file, String value) throws Exception
    {
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(value.getBytes("UTF-8")); }
        finally { out.close(); }
    }

    private static String read(File file) throws Exception
    {
        if (!file.isFile()) { return ""; }
        FileInputStream in = new FileInputStream(file);
        try
        {
            byte[] bytes = new byte[(int) file.length()];
            int at = 0;
            while (at < bytes.length)
            {
                int got = in.read(bytes, at, bytes.length - at);
                if (got < 0) { break; }
                at += got;
            }
            return new String(bytes, 0, at, "UTF-8").trim();
        }
        finally { in.close(); }
    }

    private static void delete(File file)
    {
        if (file != null && file.isFile()) { file.delete(); }
    }

    private static Peer exactCommunity(Telegram tg, String title)
            throws Exception
    {
        Peer[] found = tg.searchPeers(title, 20);
        Peer exact = null;
        for (int i = 0; i < found.length; i++)
        {
            Peer peer = found[i];
            if (peer != null && (peer.kind == Peer.CHAT
                    || peer.kind == Peer.CHANNEL)
                    && title.equals(peer.title))
            {
                if (exact != null)
                {
                    throw new Exception("group title is not unique");
                }
                exact = peer;
            }
        }
        if (exact == null) { throw new Exception("exact group was not found"); }
        return exact;
    }

    private static ChatParticipant findParticipant(Telegram tg, Peer chat,
            Peer user, int filter) throws Exception
    {
        if (chat.kind == Peer.CHAT)
        {
            ChatParticipant[] rows = tg.getBasicParticipants(chat);
            return find(rows, user);
        }
        int offset = 0;
        for (int pages = 0; pages < 64; pages++)
        {
            ChatParticipantPage page = tg.getChannelParticipants(chat, filter,
                    offset, 100);
            ChatParticipant found = find(page.participants, user);
            if (found != null) { return found; }
            int next = offset + page.participants.length;
            if (page.exhausted || next <= offset) { return null; }
            offset = next;
        }
        throw new Exception("participant scan did not converge");
    }

    private static ChatParticipant find(ChatParticipant[] rows, Peer user)
    {
        for (int i = 0; rows != null && i < rows.length; i++)
        {
            Peer peer = rows[i] == null ? null : rows[i].peer;
            if (peer != null && peer.kind == user.kind && peer.id == user.id)
            {
                return rows[i];
            }
        }
        return null;
    }

    private static String status(ChatParticipant participant)
    {
        if (participant == null) { return "absent"; }
        if (participant.status == ChatParticipant.REMOVED) { return "removed"; }
        if (participant.role == ChatParticipant.CREATOR) { return "creator"; }
        if (participant.role == ChatParticipant.ADMIN)
        {
            return participant.canEdit ? "editable-admin" : "protected-admin";
        }
        return participant.canEdit ? "editable-member" : "protected-member";
    }
}
