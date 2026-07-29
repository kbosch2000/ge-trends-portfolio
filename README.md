# GE Trends Portfolio

GE Trends Portfolio is an opt-in, read-only RuneLite integration for the
[GE Trends investment platform](https://ge-trends.vercel.app).

It listens for RuneLite's `GrandExchangeOfferChanged` event and sends cumulative
offer snapshots to the user's private portfolio. GE Trends compares
each snapshot with the previous snapshot for the same slot, so partial fills,
completed offers, cancellations, and fills observed after a relog can be
recorded without interacting with the game.

The plugin has two tracking modes. The default **Investments only** mode tracks
GE purchases, sales, holdings, and open offers without including the player's
general cash stack. The separately opt-in **Full portfolio** mode listens for
changes to coins (item ID
995) and platinum tokens (item ID 13204), converts tokens at 1,000 GP each, and
sends one combined liquid-GP value. No other inventory or bank item is read or
transmitted, and the two component quantities are not sent. The first balance is
sent only after the bank has been opened during that RuneLite session. Switching
back to Investments only removes the previously stored liquid-GP snapshot from
portfolio and leaderboard totals.

## Install and connect

While the plugin is awaiting Plugin Hub review, use the development instructions
below. After acceptance:

1. Install **GE Trends Portfolio** from RuneLite Plugin Hub.
2. Sign in to <https://ge-trends.vercel.app>.
3. Open **Account**, generate a RuneLite connection token, and copy it.
4. Open the plugin settings and paste the token into **Connection token**.
5. Enable **Cloud portfolio sync** and accept RuneLite's third-party-server
   warning.
6. Leave **Full portfolio mode** off to track investments only, or enable it,
   accept its warning, and open the bank once so the plugin can establish a
   complete inventory-plus-bank balance.

Both settings are disabled by default.

## Exactly what is transmitted

Each HTTPS request contains only:

- GE slot number;
- item ID;
- offer state;
- filled and requested quantities;
- cumulative coins spent or received;
- offer price; and
- observation time.

If **Full portfolio mode** is separately enabled, a coin-balance request contains
only:

- the combined GP value of inventory and bank coins plus platinum tokens; and
- observation time.

The request also contains the revocable GE Trends connection token in its
`Authorization` header. Like every HTTPS request, the user's IP address is
necessarily visible to the hosting provider.

The plugin does **not** send a RuneScape character name, Jagex credentials,
RuneLite account identity, any inventory or bank item other than coins and
platinum tokens, equipment, location, chat, or information about other players.

See [PRIVACY.md](PRIVACY.md) for retention and control details.

## Security design

- The only network destinations are the fixed HTTPS endpoints
  `https://ge-trends.vercel.app/api/portfolio/snapshot` and
  `https://ge-trends.vercel.app/api/portfolio/coins`.
- The destination cannot be changed in plugin settings, preventing a connection
  token from being redirected to another host.
- The token can only submit GE snapshots. It cannot read a portfolio, sign in,
  place offers, or perform any game action.
- Tokens are stored hashed by GE Trends and can be replaced or revoked from the
  user's account page.
- The plugin uses RuneLite's injected `OkHttpClient` and `Gson`.
- It contains no reflection, native code, subprocess execution, dynamic code,
  input injection, menu changes, overlays, or additional dependencies.

Security reports are handled according to [SECURITY.md](SECURITY.md).

## Development

Requirements: Java 11+ and a normal RuneLite development setup.

```text
gradlew.bat test
gradlew.bat run
```

Users with a Jagex Account must follow RuneLite's
[development-client login guide](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

Recommended in-game verification:

1. Enable the plugin but leave cloud sync disabled; no request should be sent.
2. Paste a valid token and enable cloud sync.
3. Create a small GE offer and allow it to fill in more than one part.
4. Confirm each fill is added once to the private portfolio.
5. Relog and confirm unchanged cumulative values are not duplicated.
6. Cancel a partially filled offer and confirm the final amount is retained.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
