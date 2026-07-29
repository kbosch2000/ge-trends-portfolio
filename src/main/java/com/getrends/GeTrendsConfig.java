package com.getrends;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ge-trends-portfolio")
public interface GeTrendsConfig extends Config
{
	@ConfigItem(
		keyName = "cloudSync",
		name = "Cloud portfolio sync",
		description = "Send your own Grand Exchange offer updates to your private GE Trends portfolio.",
		position = 0,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean cloudSync()
	{
		return false;
	}

	@ConfigItem(
		keyName = "apiToken",
		name = "Connection token",
		description = "The revocable portfolio-only token generated at ge-trends.vercel.app/account.",
		position = 1,
		secret = true
	)
	default String apiToken()
	{
		return "";
	}

	@ConfigItem(
		keyName = "coinBalanceSync",
		name = "Full portfolio mode",
		description = "Off: track GE investments only. On: also include inventory and bank coins and platinum tokens in total wealth.",
		position = 2,
		warning = "This feature submits the GP value of your inventory and bank coins and platinum tokens to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean coinBalanceSync()
	{
		return false;
	}
}
