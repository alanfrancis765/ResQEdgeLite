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
