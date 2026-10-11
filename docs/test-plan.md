# ResQ-Mesh Lite — Test Plan

## Phase 0: Setup

- [x] Android project builds successfully.
- [x] App installs and launches on demo phones.
- [x] Git repository is initialized.
- [x] Packet specification is shared with the team.
- [x] Gemma feasibility result is documented.
- [x] Responder screen displays simulated reports.
- [x] Synthetic data generator produces 200 records.

## Phase 1: Core Pipeline

- [x] Encode and decode an SOS packet.
- [x] Reject malformed packets.
- [ ] Connect two phones using Nearby Connections.
- [ ] Send an SOS from Phone A to Phone B.
- [ ] Persist received packets locally.
- [ ] Suppress duplicate packet IDs.
- [ ] Display received packets on the responder screen.

## Phase 2: Relay

- [ ] Relay SOS packets across three phones.
- [ ] Verify TTL and hop-count behavior.
- [ ] Queue messages when no peer is available.
- [ ] Retry delivery after a peer connects.

## Performance measurements

- Packet size in bytes: 32 bytes
- Delivery latency per hop
- Peer connection range
- Battery consumption
- SOS extraction accuracy
