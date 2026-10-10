

\# ResQ-Mesh Lite — Packet Specification v1.0



\## Header



| Field | Width | Value |

|---|---:|---|

| Magic | 2 bytes | ASCII RQ |

| Version | 1 byte | 1 |

| Message type | 1 byte | 1 = SOS |



The header is followed by a CBOR-encoded array containing

exactly 12 elements.



\## CBOR array fields



| Index | Field | Logical type |

|---:|---|---|

| 0 | packetId | Unsigned 64-bit |

| 1 | senderId | Unsigned 32-bit |

| 2 | latitude | IEEE 754 float32 |

| 3 | longitude | IEEE 754 float32 |

| 4 | accuracyM | Unsigned 8-bit |

| 5 | headcount | Unsigned 8-bit |

| 6 | medicalFlags | Unsigned 8-bit bitmask |

| 7 | waterLevel | Unsigned 8-bit |

| 8 | urgency | Unsigned 8-bit |

| 9 | timestamp | Unsigned 32-bit Unix seconds |

| 10 | ttl | Unsigned 8-bit |

| 11 | hopCount | Unsigned 8-bit |



\## Valid ranges



\- Latitude: -90 to +90

\- Longitude: -180 to +180

\- Accuracy: 0 to 255 metres

\- Headcount: 1 to 255

\- Medical flags: 0 to 31

\- Water level: 1 to 5

\- Urgency: 1 to 5

\- Timestamp: 0 to 4,294,967,295

\- TTL: 0 to 7

\- Hop count: 0 to 7



\## Medical flags(edit it later)



\- Bit 0: Injured

\- Bit 1: Elderly person

\- Bit 2: Infant

\- Bit 3: Medication needed

\- Bit 4: Pregnant person



\## Relay rules



1\. The origin creates a packet with TTL 7 and hop count 0.

2\. Every new packet is validated and stored before forwarding.

3\. Duplicate packet IDs are not processed or forwarded again.

4\. A packet with TTL 0 must not be forwarded.

5\. Forwarding decrements TTL and increments hop count.

6\. Packet ID, sender ID, coordinates and creation timestamp

&#x20;  remain unchanged during forwarding.



\## Compatibility



\- Unsupported versions and message types are rejected.

\- Reserved medical flag bits must be zero.

\- The CBOR array must contain exactly 12 elements.

\- The application packet size must not exceed 256 bytes.



