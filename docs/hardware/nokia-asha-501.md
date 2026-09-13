# Nokia Asha 501 Dual SIM — the first screen survives, the heap probe may not

This page records the physical-device report in [issue #28](https://github.com/smbdsbrain/J2MEgram/issues/28),
submitted on 2026-09-09. The phone showed J2MEgram's initial screen for a split
second and then terminated with the platform error:

```text
Not enough memory available
```

The exact firmware and J2MEgram version were not reported, and no Probe output
or application diagnostics were captured. The cause below is therefore a
working hypothesis, not a finding from the handset.

| | |
|---|---|
| Handset | Nokia Asha 501 Dual SIM |
| Build | Minified J2MEgram build; exact version not reported |
| Installation | JAR copied over Bluetooth |
| Selected network | GPRS / EDGE (2G) |
| Telegram route | Not reached |
| Observed result | Initial screen briefly visible, then platform memory error |

## The published limits should be sufficient

[Nokia's 2013 material for Asha developers](https://www.slideshare.net/slideshow/developing-for-nokiaashasmartdevcon2013katowice/26189794)
describes Asha Software Platform 1.0 as CLDC 1.1 / MIDP 2.1, with a maximum
5 MB JAR and a maximum 3 MB Java heap. J2MEgram requires CLDC 1.1 / MIDP 2.0,
and the published minified 1.6.0 release JAR is 526,771 bytes. A separate physical
Nokia C3-00 run has exercised the client in a measured 2 MB heap.

Those comparisons do not prove that this individual Asha firmware makes the
full advertised heap available to an unsigned MIDlet. They do rule out the
simple explanation that the declared Java level or package-size limit is
obviously too small. The handset reaches the application's first screen, so
installation, class loading and the initial UI construction have already
succeeded before the reported failure.

## Working hypothesis: the measurement causes the failure

Immediately after displaying the first screen on a fresh install, J2MEgram
starts its automatic heap measurement. `HeapProbe` deliberately fills the Java
heap in 64 KiB chunks until an `OutOfMemoryError`, catches that error, releases
its reserve, and then tests progressively larger single allocations to estimate
the largest contiguous block. Those failed allocations are expected on the
devices used to design the probe.

The Asha VM or application manager may treat deliberate heap exhaustion as a
fatal process condition even though the Java code catches the error. The timing
in issue #28 — the first screen appears and is immediately replaced by the
platform's memory message — matches that exact boundary. No network connection
has been opened at that point, so GPRS capacity and Telegram transport are not
credible causes of this particular failure.

This explanation remains unconfirmed until the sequence is reproduced with
diagnostics or with an otherwise identical build that skips the destructive
measurement.

## Follow-up on a project-owned handset

A project-owned Asha 501 has been ordered for follow-up testing. Once it
arrives, the investigation will:

1. reproduce the untouched minified release launch and record the exact
   firmware, artifact and platform message;
2. compare the first, second and third launches to the persisted probe-attempt
   guard, which is designed to stop retrying after two probes that do not
   return;
3. run a diagnostic build that reports the initial `totalMemory()` and
   `freeMemory()` values without exhausting the heap;
4. run the same build with the automatic startup measurement disabled to
   isolate the probe from the rest of startup;
5. only then decide whether the general production probe needs a non-exhaustive
   mode or an Asha-specific fallback.

Until that run, the honest compatibility result is **startup failure under
investigation**, not “unsupported because the phone has too little memory.”

## Still unknown

- Exact firmware and `microedition.platform` value.
- Whether the probe-attempt counter survives the platform termination.
- Actual Java heap and largest contiguous allocation on this handset.
- Whether a build that skips automatic heap measurement can reach Settings,
  configure a route and connect to Telegram.
