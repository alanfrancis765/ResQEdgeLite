

# ResQ-Mesh Lite — Test Plan



## Phase 0: Setup



- [ ] Android project builds successfully.

- [ ] App installs and launches on demo phones.

- [ ] Git repository is initialized.

- [ ] Packet specification is shared with the team.

- [ ] Gemma feasibility result is documented.

- [ ] Responder screen displays simulated reports.

- [ ] Synthetic data generator produces 200 records.



## Phase 1: Core Pipeline



- [ ] Encode and decode an SOS packet.

- [ ] Reject malformed packets.

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



- Packet size in bytes

- Delivery latency per hop

- Peer connection range

- Battery consumption

- SOS extraction accuracy



