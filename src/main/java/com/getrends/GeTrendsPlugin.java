package com.getrends;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@PluginDescriptor(
	name = "GE Trends Portfolio",
	description = "Sends read-only Grand Exchange offer updates to your private GE Trends portfolio",
	tags = {"grand exchange", "investment", "portfolio", "profit"}
)
public class GeTrendsPlugin extends Plugin
{
	static final String SNAPSHOT_URL = "https://ge-trends.vercel.app/api/portfolio/snapshot";
	static final String COINS_URL = "https://ge-trends.vercel.app/api/portfolio/coins";
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	@Inject
	private Client client;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	@Inject
	private GeTrendsConfig config;

	private static final long PLATINUM_TOKEN_GP = 1_000L;

	private long bankGp = -1;
	private long lastSentGp = -1;

	@Provides
	GeTrendsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GeTrendsConfig.class);
	}

	@Override
	protected void startUp()
	{
		bankGp = -1;
		lastSentGp = -1;
		log.info("GE Trends Portfolio tracker started");
	}

	@Override
	protected void shutDown()
	{
		bankGp = -1;
		lastSentGp = -1;
		// Enqueued requests are short-lived and owned by RuneLite's shared client.
		log.info("GE Trends Portfolio tracker stopped");
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		if (!config.cloudSync())
		{
			return;
		}

		String token = config.apiToken().trim();
		if (token.isEmpty())
		{
			log.debug("GE Trends cloud sync is enabled but the connection token is missing");
			return;
		}

		GrandExchangeOffer offer = event.getOffer();
		JsonObject snapshot = createSnapshot(
			event.getSlot(),
			offer.getItemId(),
			offer.getState().name(),
			offer.getQuantitySold(),
			offer.getSpent(),
			offer.getTotalQuantity(),
			offer.getPrice(),
			Instant.now().toString()
		);

		Request request = createRequest(SNAPSHOT_URL, token, gson.toJson(snapshot));
		sendRequest(request, "offer update");
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!config.cloudSync() || !config.coinBalanceSync())
		{
			return;
		}

		int containerId = event.getContainerId();
		if (containerId != InventoryID.INV && containerId != InventoryID.BANK)
		{
			return;
		}

		if (containerId == InventoryID.BANK)
		{
			bankGp = liquidGpValue(event.getItemContainer());
		}

		// A bank count is deliberately required before sending a total. This avoids
		// treating inventory-only GP as the complete portfolio cash balance.
		if (bankGp < 0)
		{
			return;
		}

		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null)
		{
			return;
		}

		long totalGp = liquidGpValue(inventory) + bankGp;
		if (totalGp == lastSentGp)
		{
			return;
		}

		String token = config.apiToken().trim();
		if (token.isEmpty())
		{
			return;
		}

		lastSentGp = totalGp;
		JsonObject snapshot = createCoinSnapshot(totalGp, Instant.now().toString());
		Request request = createRequest(COINS_URL, token, gson.toJson(snapshot));
		sendRequest(request, "coin balance");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN
			|| event.getGameState() == GameState.HOPPING
			|| event.getGameState() == GameState.CONNECTION_LOST)
		{
			// Never carry a cached bank balance into another character or session.
			bankGp = -1;
			lastSentGp = -1;
		}
	}

	static long liquidGpValue(ItemContainer container)
	{
		return liquidGpValue(
			container.count(ItemID.COINS),
			container.count(ItemID.PLATINUM)
		);
	}

	static long liquidGpValue(int coins, int platinumTokens)
	{
		return (long) coins + (long) platinumTokens * PLATINUM_TOKEN_GP;
	}

	private void sendRequest(Request request, String updateType)
	{
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				log.debug("GE Trends is not available", exception);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful())
					{
						log.debug("GE Trends rejected a {} update with HTTP {}", updateType, response.code());
					}
				}
			}
		});
	}

	static JsonObject createSnapshot(
		int slot,
		int itemId,
		String state,
		int quantityFilled,
		long spent,
		int totalQuantity,
		int offerPrice,
		String observedAt)
	{
		JsonObject snapshot = new JsonObject();
		snapshot.addProperty("slot", slot);
		snapshot.addProperty("itemId", itemId);
		snapshot.addProperty("state", state);
		snapshot.addProperty("quantityFilled", quantityFilled);
		snapshot.addProperty("spent", spent);
		snapshot.addProperty("totalQuantity", totalQuantity);
		snapshot.addProperty("offerPrice", offerPrice);
		snapshot.addProperty("observedAt", observedAt);
		return snapshot;
	}

	static JsonObject createCoinSnapshot(long totalGp, String observedAt)
	{
		JsonObject snapshot = new JsonObject();
		snapshot.addProperty("totalGp", totalGp);
		snapshot.addProperty("observedAt", observedAt);
		return snapshot;
	}

	static Request createRequest(String url, String token, String snapshotJson)
	{
		return new Request.Builder()
			.url(url)
			.header("Authorization", "Bearer " + token)
			.header("Cache-Control", "no-store")
			.post(RequestBody.create(JSON, snapshotJson))
			.build();
	}
}
