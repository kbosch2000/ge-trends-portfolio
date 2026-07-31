package com.getrends;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class GeTrendsApiClient
{
	static final String PORTFOLIO_URL = "https://ge-trends.vercel.app/api/portfolio/plugin";
	static final String WIKI_MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";
	static final String WIKI_LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest?id=";
	static final String WIKI_TIMESERIES_URL =
		"https://prices.runescape.wiki/api/v1/osrs/timeseries?timestep=6h&id=";
	private static final String USER_AGENT =
		"GE Trends RuneLite plugin (https://ge-trends.vercel.app)";
	private static final long MAPPING_TTL_MS = 24L * 60L * 60L * 1_000L;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final Map<Integer, ItemEntry> itemsById = new ConcurrentHashMap<>();
	private volatile long mappingLoadedAt;

	@Inject
	GeTrendsApiClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	interface ResultCallback<T>
	{
		void onSuccess(T value);

		void onError(String message);
	}

	void searchItems(String query, ResultCallback<List<ItemEntry>> callback)
	{
		String normalized = query.trim().toLowerCase(Locale.ENGLISH);
		if (normalized.length() < 2)
		{
			deliverError(callback, "Enter at least two characters.");
			return;
		}

		ensureMapping(new ResultCallback<List<ItemEntry>>()
		{
			@Override
			public void onSuccess(List<ItemEntry> ignored)
			{
				List<ItemEntry> matches = new ArrayList<>();
				for (ItemEntry item : itemsById.values())
				{
					if (item.name.toLowerCase(Locale.ENGLISH).contains(normalized))
					{
						matches.add(item);
					}
				}
				matches.sort(Comparator
					.comparing((ItemEntry item) ->
						!item.name.toLowerCase(Locale.ENGLISH).startsWith(normalized))
					.thenComparing(item -> item.name));
				deliverSuccess(callback, matches.subList(0, Math.min(20, matches.size())));
			}

			@Override
			public void onError(String message)
			{
				deliverError(callback, message);
			}
		});
	}

	void loadMarket(ItemEntry item, ResultCallback<MarketSnapshot> callback)
	{
		Request request = request(WIKI_LATEST_URL + item.id).build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				deliverError(callback, "Wiki prices are unavailable.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						deliverError(callback, "Wiki prices returned HTTP " + response.code() + ".");
						return;
					}
					JsonObject root = gson.fromJson(response.body().charStream(), JsonObject.class);
					JsonObject price = root.getAsJsonObject("data").getAsJsonObject(String.valueOf(item.id));
					if (price == null)
					{
						deliverError(callback, "No live price is available for this item.");
						return;
					}
					Long high = nullableLong(price, "high");
					Long low = nullableLong(price, "low");
					Long highTime = nullableLong(price, "highTime");
					Long lowTime = nullableLong(price, "lowTime");
					loadSeries(item, high, low, highTime, lowTime, callback);
				}
				catch (Exception exception)
				{
					deliverError(callback, "Wiki price data could not be read.");
				}
			}
		});
	}

	void loadPortfolio(String token, ResultCallback<PortfolioSnapshot> callback)
	{
		if (token.trim().isEmpty())
		{
			deliverError(callback, "Paste your connection token in the plugin settings.");
			return;
		}
		Request request = request(PORTFOLIO_URL)
			.header("Authorization", "Bearer " + token.trim())
			.header("Cache-Control", "no-store")
			.build();
		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				deliverError(callback, "GE Trends is unavailable.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						String message = response.code() == 401
							? "Your connection token is missing or invalid."
							: "Portfolio returned HTTP " + response.code() + ".";
						deliverError(callback, message);
						return;
					}
					PortfolioSnapshot snapshot =
						gson.fromJson(response.body().charStream(), PortfolioSnapshot.class);
					deliverSuccess(callback, snapshot);
				}
				catch (Exception exception)
				{
					deliverError(callback, "Portfolio data could not be read.");
				}
			}
		});
	}

	private void ensureMapping(ResultCallback<List<ItemEntry>> callback)
	{
		if (!itemsById.isEmpty() && System.currentTimeMillis() - mappingLoadedAt < MAPPING_TTL_MS)
		{
			deliverSuccess(callback, Collections.emptyList());
			return;
		}

		httpClient.newCall(request(WIKI_MAPPING_URL).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				deliverError(callback, "The Wiki item catalog is unavailable.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful() || response.body() == null)
					{
						deliverError(callback, "The Wiki item catalog returned HTTP " + response.code() + ".");
						return;
					}
					JsonArray mapping = gson.fromJson(response.body().charStream(), JsonArray.class);
					Map<Integer, ItemEntry> fresh = new ConcurrentHashMap<>();
					for (JsonElement element : mapping)
					{
						JsonObject row = element.getAsJsonObject();
						if (!row.has("id") || !row.has("name"))
						{
							continue;
						}
						ItemEntry entry = new ItemEntry(
							row.get("id").getAsInt(),
							row.get("name").getAsString(),
							row.has("limit") && !row.get("limit").isJsonNull()
								? row.get("limit").getAsInt() : null
						);
						fresh.put(entry.id, entry);
					}
					itemsById.clear();
					itemsById.putAll(fresh);
					mappingLoadedAt = System.currentTimeMillis();
					deliverSuccess(callback, Collections.emptyList());
				}
				catch (Exception exception)
				{
					deliverError(callback, "The Wiki item catalog could not be read.");
				}
			}
		});
	}

	private void loadSeries(
		ItemEntry item,
		Long high,
		Long low,
		Long highTime,
		Long lowTime,
		ResultCallback<MarketSnapshot> callback)
	{
		httpClient.newCall(request(WIKI_TIMESERIES_URL + item.id).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException exception)
			{
				// Live prices remain useful if historical data is temporarily unavailable.
				deliverSuccess(callback,
					new MarketSnapshot(item, high, low, highTime, lowTime, Collections.emptyList()));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response ignored = response)
				{
					List<PricePoint> points = new ArrayList<>();
					if (response.isSuccessful() && response.body() != null)
					{
						JsonObject root = gson.fromJson(response.body().charStream(), JsonObject.class);
						JsonArray data = root.getAsJsonArray("data");
						if (data != null)
						{
							int start = Math.max(0, data.size() - 120);
							for (int index = start; index < data.size(); index++)
							{
								JsonElement element = data.get(index);
								JsonObject row = element.getAsJsonObject();
								points.add(new PricePoint(
									row.get("timestamp").getAsLong(),
									nullableLong(row, "avgHighPrice"),
									nullableLong(row, "avgLowPrice")
								));
							}
						}
					}
					deliverSuccess(callback,
						new MarketSnapshot(item, high, low, highTime, lowTime, points));
				}
				catch (Exception exception)
				{
					deliverSuccess(callback,
						new MarketSnapshot(item, high, low, highTime, lowTime, Collections.emptyList()));
				}
			}
		});
	}

	private Request.Builder request(String url)
	{
		return new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.header("Accept", "application/json");
	}

	private static Long nullableLong(JsonObject object, String key)
	{
		return object.has(key) && !object.get(key).isJsonNull()
			? object.get(key).getAsLong()
			: null;
	}

	private static <T> void deliverSuccess(ResultCallback<T> callback, T value)
	{
		SwingUtilities.invokeLater(() -> callback.onSuccess(value));
	}

	private static <T> void deliverError(ResultCallback<T> callback, String message)
	{
		SwingUtilities.invokeLater(() -> callback.onError(message));
	}

	static final class ItemEntry
	{
		final int id;
		final String name;
		final Integer buyLimit;

		ItemEntry(int id, String name, Integer buyLimit)
		{
			this.id = id;
			this.name = name;
			this.buyLimit = buyLimit;
		}
	}

	static final class PricePoint
	{
		final long timestamp;
		final Long high;
		final Long low;

		PricePoint(long timestamp, Long high, Long low)
		{
			this.timestamp = timestamp;
			this.high = high;
			this.low = low;
		}
	}

	static final class MarketSnapshot
	{
		final ItemEntry item;
		final Long high;
		final Long low;
		final Long highTime;
		final Long lowTime;
		final List<PricePoint> points;

		MarketSnapshot(
			ItemEntry item,
			Long high,
			Long low,
			Long highTime,
			Long lowTime,
			List<PricePoint> points)
		{
			this.item = item;
			this.high = high;
			this.low = low;
			this.highTime = highTime;
			this.lowTime = lowTime;
			this.points = points;
		}
	}

	static final class PortfolioSnapshot
	{
		PortfolioSummary summary;
		List<Holding> holdings;
		String lastRuneLiteSync;
		boolean includeLiquidGp;
	}

	static final class PortfolioSummary
	{
		double totalMarketValue;
		double investedMarketValue;
		double cashBalance;
		double geHeldGp;
		double unrealizedProfit;
		double realizedProfit;
		double totalProfit;
	}

	static final class Holding
	{
		int itemId;
		String name;
		double quantity;
		Double averageCost;
		Long marketPrice;
		Double marketValue;
		Double unrealizedProfit;
		Double unrealizedPercent;
	}
}
