# Security policy

## Supported versions

Security fixes are made for the latest published J2MEgram release and the
current `main` branch. Older releases may be useful on specific phones but do
not receive separate security maintenance.

## Report a vulnerability privately

Do not open a public issue for a vulnerability or suspected credential leak.
Use GitHub's private
[security advisory form](https://github.com/smbdsbrain/J2MEgram/security/advisories/new)
instead.

Include, when available:

- the J2MEgram version or commit;
- the affected handset, emulator or host platform;
- a concise reproduction path;
- the expected impact;
- logs with phone numbers, `api_id`, `api_hash`, authorization keys and message
  content removed.

You may submit a report before you have a complete exploit or fix. The project
will use the private advisory to clarify the report and coordinate disclosure.
No response-time or release-time guarantee is currently offered.

## Security boundaries

J2MEgram is experimental and has not received a security audit. MTProto
cryptography and authorization-key generation run on the handset, but Java ME
RMS storage is not encrypted at rest. Secret chats are not implemented, and
compatibility or entropy behaviour can differ between vendor VMs.

Read the detailed
[security posture](docs/architecture.md#security-posture-stated-honestly) and
[feature limitations](docs/features.md#security-boundaries) before using the
client for sensitive conversations.
