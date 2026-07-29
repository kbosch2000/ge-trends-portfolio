package com.getrends;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import okhttp3.Request;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeTrendsSecurityTest
{
	@Test
	public void snapshotContainsOnlyDocumentedOfferFields()
	{
		JsonObject snapshot = GeTrendsPlugin.createSnapshot(
			3,
			4151,
			"BUYING",
			25,
			2_500_000L,
			100,
			100_000,
			"2026-07-29T12:00:00Z"
		);

		Set<String> expected = new HashSet<>(Arrays.asList(
			"slot",
			"itemId",
			"state",
			"quantityFilled",
			"spent",
			"totalQuantity",
			"offerPrice",
			"observedAt"
		));

		assertEquals(expected, snapshot.keySet());
		assertFalse(snapshot.has("account"));
		assertFalse(snapshot.has("username"));
		assertFalse(snapshot.has("character"));
	}

	@Test
	public void requestUsesFixedHttpsEndpointAndAuthorizationHeader()
	{
		String token = "test-token-that-is-never-logged";
		Request request = GeTrendsPlugin.createRequest(GeTrendsPlugin.SNAPSHOT_URL, token, "{}");

		assertTrue(request.url().isHttps());
		assertEquals("ge-trends.vercel.app", request.url().host());
		assertEquals("/api/portfolio/snapshot", request.url().encodedPath());
		assertEquals("Bearer " + token, request.header("Authorization"));
		assertFalse(request.url().toString().contains(token));
		assertEquals("no-store", request.header("Cache-Control"));
	}

	@Test
	public void coinSnapshotContainsOnlyAggregateBalanceAndTime()
	{
		JsonObject snapshot = GeTrendsPlugin.createCoinSnapshot(
			123_456_789L,
			"2026-07-29T12:00:00Z"
		);

		Set<String> expected = new HashSet<>(Arrays.asList("totalGp", "observedAt"));
		assertEquals(expected, snapshot.keySet());
		assertFalse(snapshot.has("items"));
		assertFalse(snapshot.has("inventory"));
		assertFalse(snapshot.has("bank"));

		Request request = GeTrendsPlugin.createRequest(GeTrendsPlugin.COINS_URL, "token", "{}");
		assertTrue(request.url().isHttps());
		assertEquals("ge-trends.vercel.app", request.url().host());
		assertEquals("/api/portfolio/coins", request.url().encodedPath());

		Request disableRequest = GeTrendsPlugin.createDeleteRequest(GeTrendsPlugin.COINS_URL, "token");
		assertEquals("DELETE", disableRequest.method());
		assertEquals("Bearer token", disableRequest.header("Authorization"));
		assertEquals("ge-trends.vercel.app", disableRequest.url().host());
	}

	@Test
	public void platinumTokensAreConvertedWithoutIntegerOverflow()
	{
		assertEquals(2_147_484_000L, GeTrendsPlugin.liquidGpValue(1_000, 2_147_483));
	}

	@Test
	public void cloudSynchronizationIsOptIn()
	{
		GeTrendsConfig config = new GeTrendsConfig()
		{
		};

		assertFalse(config.cloudSync());
		assertFalse(config.coinBalanceSync());
		assertTrue(config.apiToken().isEmpty());
	}
}
