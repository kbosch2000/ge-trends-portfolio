package com.getrends;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.events.GrandExchangeOfferChanged;
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
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private Gson gson;

	@Inject
	private GeTrendsConfig config;

	@Provides
	GeTrendsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GeTrendsConfig.class);
	}

	@Override
	protected void startUp()
	{
		log.info("GE Trends Portfolio tracker started");
	}

	@Override
	protected void shutDown()
	{
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

		Request request = createRequest(token, gson.toJson(snapshot));
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
						log.debug("GE Trends rejected an offer update with HTTP {}", response.code());
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

	static Request createRequest(String token, String snapshotJson)
	{
		return new Request.Builder()
			.url(SNAPSHOT_URL)
			.header("Authorization", "Bearer " + token)
			.header("Cache-Control", "no-store")
			.post(RequestBody.create(JSON, snapshotJson))
			.build();
	}
}
