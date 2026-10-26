# Watchdog 2020 Remake

A Minecraft 1.8.8 reconstruction of the NCP portion of roughly 2020 Hypixel. This is
not Hypixel's proprietary source or a complete Watchdog implementation.

The entire modern NCP Java base was replaced with upstream **2013-12-31,
`d51da2cbe013a19657c1d774d6aa2df95af1b080`**. Gradle packaging was retained.
The native Spigot 1.8_R3 bridge comes from upstream **2015-05-25, `83660b0c`**;
1.8 block definitions and Bukkit API signatures were adapted to the old core.

## Build and run

Java 17+ to build. The plugin targets Java 8 bytecode. The local test server runs
PaperSpigot 1.8.8 on Java 17 with ViaVersion 5.12.1. Put an existing 1.8.8 Spigot/Paper
server JAR in `gradle/ncp-libs/spigot-1.8.8.jar` (ignored), or supply its path:

```sh
./gradlew :NoCheatPlus:shadowJar -Pncp.serverJar=/path/to/spigot-1.8.8.jar
```

Install `target/NoCheatPlus.jar` in `plugins/` and copy the included
[`config.yml`](config.yml) to `plugins/NoCheatPlus/config.yml`, then restart the
server. The plugin/config directory retain the NoCheatPlus name for compatibility.
No ProtocolLib is required. Use a compatible current ViaVersion build for newer
clients; the test setup uses ViaVersion 5.12.1 and Java 17.
Inventory checks, including FastClick and Open, are disabled by default.
When explicitly enabled, FastConsume and InstantEat skip consumption timing checks for Via-reported
protocol 107+ (1.9 and newer); 1.8/unknown clients retain normal timing. This does not force food
completion by itself; the legacy profile separately provides native early-use
completion on a timed slot change. A modern
client already translated to handshake 47 is still identified as 1.8.
On the local PaperSpigot server, set `moved-too-quickly-threshold: 400.0` for the
requested 20-block displacement limit: the native comparison is squared distance
minus squared velocity. This does not exempt movement from NCP.

## Source map

All core paths below are relative to `NCPCore/src/main/java/fr/neatmonster/nocheatplus/`.

| Area | Entry and execution |
| --- | --- |
| Startup | `NCPPlugin/.../NoCheatPlus.java` registers block interact, break, place, chat, combined, fight, inventory, moving, then optional Bukkit consumption support. `MCAccessFactory` selects native collision/health access. |
| Shared pipeline | `checks/Check.java` evaluates config/permissions/exemptions; `ViolationData.java` selects and rate-limits cancel/log/command actions. Async actions are queued through `TickTask`. |
| Movement | `checks/moving/MovingListener.java`: illegal coordinates → Passable → SurvivalFly or CreativeFly → optional NoFall → MorePackets → final event result. MONITOR commits accepted positions/fall state. Tick processing handles hover and repeated airborne fall damage. |
| Horizontal | `SurvivalFly.getAllowedhDist` chooses walk/sprint/sneak/block/swim/web, then ice/potions. `hDistAfterFailure`: bunny → permissions → actual server velocity → bunny retry → horizontal buffer → speed violation. |
| Vertical | `SurvivalFly.check` evaluates web/climb/liquid or jump phase/height; `inAirChecks` evaluates direction reversals and vertical accounting. `MovingData` owns phase, setback, velocity and recovery state. |
| Collision | `moving/Passable.java`, `utilities/PlayerLocation.java`, `BlockProperties.java`, native `BlockCacheCBSpigot1_8_R3` resolve shapes and ray tracing before fly acceptance. |
| Block break | `blockbreak/BlockBreakListener`: wrong block → frequency → fast break → no swing → reach → direction. Material/tool break durations come from BlockProperties; damage/interact events supply start time and clicked block. |
| Block interact | `blockinteract/BlockInteractListener`: speed → reach → direction → visibility; failed actions deny item/block use. |
| Block place | `blockplace/BlockPlaceListener`: material restrictions → fast place → no swing → reach → direction; sign changes run AutoSign, projectile launch runs speed/yaw-rate/Improbable/pearl checks. |
| Fight | `fight/FightListener`: self-hit/dead handling → critical → sprint-timing Knockback → no swing → reach → direction. GodMode and FastHeal use damage/health events. Accepted sprint hits update movement sprint grace. |
| Inventory | `inventory/InventoryListener`: bow timing, food timing, click frequency, item validity/drop, and open-inventory lifecycle. Optional `NCPCompatBukkit` FastConsume replaces InstantEat when supported. |
| Chat | `chat/ChatListener`: color/text/command scoring; login runs Relog/Logins; optional captcha. These are separate from movement. |
| Combined | `combined/CombinedListener`, `Combined`, `Improbable`: shared action frequency, yaw-rate penalties, join damage handling, optional MunchHausen. BedLeave is dispatched by MovingListener. |
| Configuration/data | `config/DefaultConfig`, per-check Config/Data classes, `players/DataManager`, and world/player lifecycle hooks. |

## Movement profile

The movement core starts with December 2013 vertical, liquid and lost-ground checks,
with the March/April 2015 phase, velocity-jump accounting and velocity-decay fixes below.
Bunny handling follows the August 1, 2015 `c5574b59` implementation: .66 initial
slope, /160 friction and the historical relative-spike OR grouping. Its small
head-margin query admits grounded bunny hops; it does not exempt clip flight.
`legacy.groundjumpmodifier` caps the peak of a buffer-funded ground takeoff at
2.5 times the current normal allowance. The original free absolute/relative bunny
branches remain intact; larger supported takeoffs spend the shared buffer before
receiving the same .66 slope and /160 friction continuation. A fully buffer-paid
first-airborne boost after a normal jump retains that continuation during a long
fall. Arbitrary later airborne acceleration does not receive it. Each accepted movement with an airborne endpoint, including the landing
transition, banks `legacy.airbufferrefill` (.03) credit for the next ground move,
capped by the same horizontal buffer. That ground
move does not also receive the ordinary clean-move refill. Longer airtime therefore
supports more subsequent ground boost. Teleports, setbacks and exempt recovery do
not bank this credit. Standing/look-only updates do not count.
Creative flight includes the December 2014 `79394c4e` sprint-flight fix for 1.8:
its 1.92 multiplier applies only to actual authorized flight while sprinting.
Ordinary ground movement uses the .29 sprint allowance without a sprint-state or
blocking-speed restriction. Potion, ice, sneak and liquid modifiers remain active.

There is one original horizontal buffer, capped at 1 by default. Accepted under-limit
movement replenishes .05–.1 credit per event; standing still does not refill it.
Boosts spend that same buffer. Damage does not refill it or grant general recovery.
`legacy.actions` cancels above 40 VL; `legacy.vlfreeze` is 2000 ms.
Clean moves apply NCP's existing .95 VL decay only after that freeze. The original non-cancel
handler clears accounting and jump phase. There are no custom mini-glide,
Funcraft-decay, water-arc, liquid-bob or block-contact waivers.

Damage retains actual server velocity accounting. Zero vertical velocity does
not create upward credit (`3c8b2558`, December 2014). The March 2015 velocity-jump
flag suppresses vertical accounting until ground/reset contact (`1510f011`);
the April 2015 counter/decay correction prevents vertical freedom persisting
after its velocity expires (`393787e2`). Falling is left to accounting, as in
March 2015 `dc1f5ece`.

The reconstructed Hypixel level-flight patch keeps maxphase checking active for
level/ascent movement once actual vertical velocity expires, even when the
historical velocity-jump flag remains. This is an explicit reconstruction of the
requested distinction, not recovered private server code. Server-confirmed hurt
can start that same accounting state while the native hurt timer is active,
including fall damage without positive knockback; cancelled/zero damage cannot.
It grants no new buffer or vertical freedom. Damaged downward glide
keeps the original accounting bug; undamaged acceleration still spends finite
horizontal credit. There is no mode-name whitelist. The pre-`e24ccc73`
equal-bucket accounting result is
restored for qualifying short takeoffs from actual ground. The observed takeoff
is retained until ground/reset/setback, so switching to a constant glide during
a normal full jump cannot acquire it. This allows the old tiny downward glide without
a fixed minimum vertical violation. Unadmitted undamaged airborne acceleration
invalidates inherited bunny-friction state and spends the existing horizontal
buffer; ordinary ground hops retain their qualified continuation. Rounding-only
scores up to .01 VL still count but do not refresh the freeze; clean events can
decay that residue instead of accumulating it indefinitely. Substantive violations
retain the configured 2000 ms freeze.

The separate immediate 1.4 horizontal speed cap is removed. NCP's normal speed,
friction, velocity and shared horizontal-buffer checks remain active.

| Recovery | Scope |
| --- | --- |
| Hover | Stationary airborne movement reaches the 100-tick hover correction. A successful correction grants 100 ticks of recovery. Look packets do not restart the timer. |
| External teleport | Known-cause server teleports, including pearls, world changes and respawn, grant the same recovery. NCP setbacks and UNKNOWN native corrections do not. The old native coordinate `/tp` implementation reports UNKNOWN; entity-target `/tp` reports COMMAND. |
| Drop Fly | An accepted net fall over three blocks followed by arrested descent arms repeated native FALL damage below the original fall height. Verified movement recovery is retained. One gravity-valid high-speed falling entry packet may precede the arrest; continued fast falling cannot reuse it. |
| Vertical AntiVoid / High Jump | A same-X/Z upward return is limited to the tracked fall and native displacement limit. An impulse up to 5 after a fall greater than `3 + 4 * impulse` admits its gravity-decaying arc, including coalesced apex updates and return clipping. Repeat arcs require returning to the same floor and may not increase the qualified impulse. |

Drop Fly recovery admits level and descending movement (positive jitter at most
.0001); ascent must pass bounded return or a qualified High Jump arc. Once armed,
descent retains recovery and repeated damage. Damage stops on ascent, actual
ground/reset contact, or above the original fall height. Each hit is fixed at two
health points (one heart), independent of fall height. The first hit is immediate,
followed by a default interval of 10 ticks. Damage
deadlines are checked each tick; hover accounting keeps its 5-tick cadence.
Hover/teleport recovery suppresses both
this damage and fall accumulation, so it is not deferred until recovery ends.
Recovery retains the original full-position setback until actual ground/reset.
A setback clears the old fall proof so it cannot immediately re-arm Drop Fly.
Passable, illegal-coordinate checks and MorePackets still run.

FastPlace and sprint-back are disabled. NoFall is off by default. OP users remain
subject to checks; administrative command access does not grant check bypasses.
Downward placements may click a block's underside from above; placement aim,
reach and interaction rate checks remain enabled, with one block of hidden-face
tolerance below the player for expanded scaffolding.
Reach uses a fixed 4.4 center-distance limit with adaptive reduction disabled and
no post-violation penalty. Other fight checks and all inventory checks are off.
The legacy profile completes active food/drink use on a slot change after 600 ms
using native consume logic. Container clicks remain ordered on the main thread,
normalize quick-move predictions to server stacks, and receive an authoritative
post-click inventory update for protocol translation.
No modern custom Watchdog checks, autoclicker checks or count-based lagback waiver
are enabled. This is a source-based behavioral reconstruction, not a verified 1:1
copy of Hypixel's private 2020 server.

## Evidence and history

- [2013 baseline](https://github.com/NoCheatPlus/NoCheatPlus/tree/d51da2cbe013a19657c1d774d6aa2df95af1b080).
- [2013-01-31 phase check](https://github.com/NoCheatPlus/NoCheatPlus/commit/7e4ad21f4ff2e073de81b10793b3992d08adae1c) independently rejects late upward movement.
- [2013-08-10 glide accounting fix](https://github.com/NoCheatPlus/NoCheatPlus/commit/e24ccc734a25a34d86b54007b3dacf4d6ff6f64b): an equal-bucket accounting defect existed, but reverting it alone does not remove phase rejection.
- [2015-05-26 friction160](https://github.com/NoCheatPlus/NoCheatPlus/commit/9aa315360b136ef58409cf2979ef0dc8fba0eb83).
- [2015-07-27 bunny slope refactor](https://github.com/NoCheatPlus/NoCheatPlus/commit/53021bba9867abd73a93d7994be5f0c0829903ed).
- [2015-08-01 Y-port-enabling relative-spike OR grouping](https://github.com/NoCheatPlus/NoCheatPlus/blob/c5574b59786dafe2a27e6979ead5505555a1c05a/NCPCore/src/main/java/fr/neatmonster/nocheatplus/checks/moving/SurvivalFly.java#L1079), paired with [dated Exhibition YPort](https://github.com/ExhibitionSource/exhibition-client/blob/73d2b09f1465d99f95544d1e50e2d410ea5d638f/src/minecraft/exhibition/module/impl/movement/Speed.java#L299). The upstream commit does not itself name YPort.
- [2015-09-15 relative vertical prediction](https://github.com/NoCheatPlus/NoCheatPlus/commit/8230a13fc0dd974b914919a06d3e69ba960000f2) illustrates why later NCP must not be treated as the same vertical base.
- [Exhibition 2020-12-30 snapshot](https://github.com/ExhibitionSource/exhibition-client/tree/0e1a3637971ad40c400280aec77f6641a9fb3e1a): Fly micro-Y/abilities, LongJump damage synthesis, correction-gated Teleport, YPort, and upward AntiFall. This is dated client intent, not private server implementation.
- [2020-12-07 contemporary report](https://hypixel.net/threads/the-huge-problem-with-this-server.3589117/page-9) describes five-second hover/flight behavior; its damage interval differs from the requested cadence.
- [2020-10-18 Astolfo demonstration](https://www.youtube.com/watch?v=df4Zl3Ot1vs), supplied by the user; footage/comments describe Drop Fly. Video does not reveal internal server predicates.
- [March 2021 patch reports](https://hypixel.net/threads/watchdog-explained.3990147/page-2), [April 2021 first-person report](https://hypixel.net/threads/how-do-scaffold-and-fly-even-bypass.4136447/).
- The public Novo mirror was uploaded 2021-11-25 and is damaged decompilation: useful comparison, not proof of 2020 behavior. Exhibition HEAD is 2025 and was excluded from dated conclusions.
- No attributable FunCraft server source was found. Client mode names and unattributed leaks are not evidence of server ancestry or exact check formulas.

The user's March 4, 2019 [seven-method Exhibition video](https://www.youtube.com/watch?v=MmqFckiomWI)
separates hover/correction(0:05), explosions(0:29), rejoin(1:20),
XPross/Drop Fly with recurring damage after a 3–5 block fall(1:52),
a special-client fly(2:13), pearls(2:39), and a rank-related claim(3:07).
The latter is not evidence of an NCP movement algorithm or a reason to waive checks
by username/rank. The video is contemporary evidence of observed methods, not server code.

## License and attribution

GPLv3; see [`LICENSE.txt`](LICENSE.txt). Original NoCheatPlus copyright and
license notices are retained. This snapshot derives from
[Baba-Development/Reverse-Watchdog](https://github.com/Baba-Development/Reverse-Watchdog)
and the upstream NCP revisions linked above.
