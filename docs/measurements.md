# ResQ-Edge Lite Measurements

| Measurement | Result | Note |
|---|---|---|
| Connection time, endpoint found to connected | 0.83 s, 1.76 s, 2.75 s (median 1.76 s) | Your row said “start to connected”, but the app measures from “endpoint found”. Starting from the Start tap, the earlier logs I saw were 6.5 s, 8.4 s and 12.2 s |
| Ping round trip, 10 pings | min 65.5, median 109.8, max 151.7 ms | One-way is about half if the path is symmetric (roughly 33 to 76 ms) |
| Sync of 201 packets | 3,879 ms and 5,185 ms | About 19 to 26 ms per packet. Two runs, so do a third |
| Delivered | 201 of 201, 0 failures, 0 malformed | |
| Range, open space | about 50 to 100 m (65 to 130 paces) | Repeat three times and report the median |
| Range, through walls | about 10 m | One wall and two walls both say 13 paces, so check that these were separate tests |
| Reconnect after loss | none, manual restart needed | Prompt 13 |

## Reconnect after a lost link

Method: "Drop link (test)" button, both phones in range (phones used: ____ and ____).
Total = disconnect until connected again, from the app's "Reconnect timeline" log line.

| Build | Setting | Runs | Median |
|---|---|---|---|
| Before Prompt 13b (10 s retry fallback) | Side by side | 24, 28, 21, 18, 22 s | 22 s |
| Before Prompt 13b | Walls between phones | 19, 34, 55, 48, 34 s | 34 s |
| After Prompt 13b | Side by side | 2.82, 2.66, 2.68, 2.64, 2.49 s | 2.66 s |
| After Prompt 13b | Walls between phones | 2.7, 2.9, 7.4, 13.1, 2.7, 2.7 s | 2.8 s |

Breakdown after 13b, side by side: about 2.1 s waiting before the first attempt, 0.4 to 0.7 s from request
to connected. ("First attempt to endpoint found" is 0 because the peer stayed in the discovery list.)

Out-of-range walk test (one run): total 58.3 s = 3.1 s to first attempt (disconnected cycle) + 51.9 s until
the peer was found again + 3.3 s found to connected. The 51.9 s includes time spent out of range.
Stopwatch "back in range to connected": ___ s, ___ s, ___ s.

Background behaviour (build without a foreground service): the connection drops when the app is not in the
foreground. Fixed by the foreground service task, to be re-tested.

Evidence: screenshots saved in docs/evidence/ (originals, not edited).
