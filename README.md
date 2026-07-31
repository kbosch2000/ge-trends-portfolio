# GE Trends

GE Trends is an opt-in, read-only RuneLite companion for the
[GE Trends investment platform](https://ge-trends.vercel.app).

The sidebar contains:

- **Portfolio** — a compact view of the connected user's own portfolio value,
  realized and unrealized P/L, liquid GP, GE-held GP, open investments, and
  calculated trade-cycle performance with partial fills compressed.
- **Market** — item search, live Wiki high/buy and low/sell prices, spread, GE
  buy limit, and a compact 30-day chart usable from anywhere in game.

The plugin also listens for RuneLite's `GrandExchangeOfferChanged` event and
sends cumulative offer snapshots to the user's private portfolio. GE Trends
compares each snapshot with the previous snapshot for the same slot, so partial
fills, completed offers, cancellations, and fills observed after a relog can be
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

1. Install **GE Trends** from RuneLite Plugin Hub.
2. Sign in to <https://ge-trends.vercel.app>.
3. Open **Account**, generate a RuneLite connection token, and copy it.
4. Open the plugin settings and paste the token into **Connection token**.
5. Enable **Cloud portfolio sync** and accept RuneLite's third-party-server
   warning.
6. Leave **Full portfolio mode** off to track investments only, or enable it,
   accept its warning, and open the bank once so the plugin can establish a
   complete inventory-plus-bank balance.
7. Optionally enable **Market lookup** and accept its separate Wiki-network
   warning to use item search and charts inside RuneLite.

All networked features are disabled by default.

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

When the connected user opens or refreshes the Portfolio panel, the token is
sent to the fixed GE Trends portfolio endpoint. The response contains only that
token owner's summarized totals, current holdings, and calculated closed-sale
results. It does not contain an
email address, password, MFA data, raw token, other member data, or account
administration controls.

When **Market lookup** is enabled and used, the item search catalog, latest
price, and 30-day time series are requested from the public OSRS Wiki price API.
The requested item ID and the user's IP address are visible to the Wiki hosting
provider. No GE Trends token or RuneLite identity is included in Wiki requests.

The plugin does **not** send a RuneScape character name, Jagex credentials,
RuneLite account identity, any inventory or bank item other than coins and
platinum tokens, equipment, location, chat, or information about other players.

See [PRIVACY.md](PRIVACY.md) for retention and control details.

## Security design

- The only network destinations are the fixed HTTPS endpoints
  `https://ge-trends.vercel.app/api/portfolio/snapshot` and
  `https://ge-trends.vercel.app/api/portfolio/coins`,
  `https://ge-trends.vercel.app/api/portfolio/plugin`, and the fixed public API
  prefix `https://prices.runescape.wiki/api/v1/osrs/`.
- The destination cannot be changed in plugin settings, preventing a connection
  token from being redirected to another host.
- The token can submit GE snapshots and read only its owner's summarized
  portfolio and holdings. It cannot sign in, read another account, change
  account settings, place offers, or perform any game action.
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
