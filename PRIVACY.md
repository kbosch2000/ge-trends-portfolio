# Privacy

GE Trends Portfolio synchronizes a user's own Grand Exchange offer state with
the GE Trends account connected by a revocable token.

## Data sent

The plugin sends the GE slot, item ID, offer state, filled and requested
quantities, cumulative coins spent or received, offer price, and observation
time. If the separate **Sync GP balance** option is enabled, it also reads coins
(item ID 995) and platinum tokens (item ID 13204) held in inventory and bank,
converts both to GP, and sends one combined liquid-GP value. It never sends the
contents of other inventory or bank slots or the two component quantities.

It does not send a character name, Jagex credentials, RuneLite account identity,
equipment, location, chat, or other-player data.

The user's IP address is necessarily processed by the HTTPS hosting provider
when a request is made. Cloud synchronization is disabled by default and
RuneLite displays a third-party-server warning before it is enabled.

## Purpose and retention

Offer snapshots are used only to calculate the connected user's private
portfolio transactions. Optional aggregate coin balances are used only to keep
the connected account's portfolio cash accurate. Imported transactions, the
latest state of each GE slot, and the latest submitted coin balance are retained
in that account so the portfolio remains accurate across sessions and devices.

## User control

The connection token can be replaced or revoked at
<https://ge-trends.vercel.app/account>. Revocation immediately prevents further
plugin submissions. Individual imported transactions can be removed from the
portfolio.

## Network destination

The plugin sends data only to:

`https://ge-trends.vercel.app/api/portfolio/snapshot`

`https://ge-trends.vercel.app/api/portfolio/coins`

The destination is fixed in source code and cannot be configured by the user.
