package com.getrends;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

final class GeTrendsPanel extends PluginPanel
{
	private static final Color GOLD = new Color(243, 182, 31);
	private static final Color GREEN = new Color(32, 197, 141);
	private static final Color RED = new Color(242, 85, 96);
	private static final Color MUTED = new Color(145, 157, 168);
	private static final DecimalFormat WHOLE_GP = new DecimalFormat("#,##0");
	private static final DecimalFormat PERCENT = new DecimalFormat("+#,##0.00;-#,##0.00;0.00");

	private final GeTrendsApiClient api;
	private final GeTrendsConfig config;
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JButton portfolioTab = tabButton("Portfolio");
	private final JButton marketTab = tabButton("Market");
	private final JPanel portfolioContent = verticalPanel();
	private final JPanel searchResults = verticalPanel();
	private final JPanel marketDetails = verticalPanel();
	private final JTextField searchField = new JTextField();

	@Inject
	GeTrendsPanel(GeTrendsApiClient api, GeTrendsConfig config)
	{
		super(false);
		this.api = api;
		this.config = config;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(buildHeader(), BorderLayout.NORTH);
		content.setOpaque(false);
		content.add(buildPortfolio(), "portfolio");
		content.add(buildMarket(), "market");
		add(content, BorderLayout.CENTER);

		portfolioTab.addActionListener(event -> showPortfolio());
		marketTab.addActionListener(event -> showMarket());
		showPortfolio();
	}

	void refreshPortfolio()
	{
		renderMessage(portfolioContent, "Loading your portfolio...");
		if (!config.cloudSync())
		{
			renderMessage(
				portfolioContent,
				"Enable Cloud portfolio sync in the plugin settings to connect this panel."
			);
			return;
		}
		api.loadPortfolio(config.apiToken(), new GeTrendsApiClient.ResultCallback<GeTrendsApiClient.PortfolioSnapshot>()
		{
			@Override
			public void onSuccess(GeTrendsApiClient.PortfolioSnapshot value)
			{
				renderPortfolio(value);
			}

			@Override
			public void onError(String message)
			{
				renderMessage(portfolioContent, message);
			}
		});
	}

	private JPanel buildHeader()
	{
		JPanel header = verticalPanel();
		header.setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		JLabel title = new JLabel("<html><b>GE <font color='#f3b61f'>Trends</font></b></html>");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
		titleRow.add(title, BorderLayout.WEST);
		JButton website = secondaryButton("Website");
		website.addActionListener(event -> LinkBrowser.browse("https://ge-trends.vercel.app"));
		titleRow.add(website, BorderLayout.EAST);
		header.add(titleRow);
		header.add(Box.createVerticalStrut(8));

		JPanel tabs = new JPanel(new GridLayout(1, 2, 4, 0));
		tabs.setOpaque(false);
		tabs.add(portfolioTab);
		tabs.add(marketTab);
		header.add(tabs);
		return header;
	}

	private JPanel buildPortfolio()
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
		actions.setOpaque(false);
		JButton refresh = primaryButton("Refresh");
		refresh.addActionListener(event -> refreshPortfolio());
		actions.add(refresh);
		wrapper.add(actions, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(portfolioContent);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		wrapper.add(scroll, BorderLayout.CENTER);
		return wrapper;
	}

	private JPanel buildMarket()
	{
		JPanel wrapper = new JPanel(new BorderLayout(0, 8));
		wrapper.setOpaque(false);
		wrapper.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

		JPanel search = new JPanel(new BorderLayout(5, 0));
		search.setOpaque(false);
		searchField.setToolTipText("Search tradeable OSRS items");
		searchField.addActionListener(event -> search());
		JButton button = primaryButton("Search");
		button.addActionListener(event -> search());
		search.add(searchField, BorderLayout.CENTER);
		search.add(button, BorderLayout.EAST);
		wrapper.add(search, BorderLayout.NORTH);

		JPanel body = verticalPanel();
		JLabel explanation = mutedLabel(
			"<html>Look up live Wiki prices and a 30-day chart from anywhere in game.</html>");
		explanation.setBorder(BorderFactory.createEmptyBorder(0, 2, 8, 2));
		body.add(explanation);
		body.add(searchResults);
		body.add(marketDetails);
		JScrollPane scroll = new JScrollPane(body);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		wrapper.add(scroll, BorderLayout.CENTER);
		return wrapper;
	}

	private void showPortfolio()
	{
		cards.show(content, "portfolio");
		selectTab(portfolioTab, marketTab);
		refreshPortfolio();
	}

	private void showMarket()
	{
		cards.show(content, "market");
		selectTab(marketTab, portfolioTab);
		if (!config.marketLookup())
		{
			renderMessage(
				searchResults,
				"Enable Market lookup in the plugin settings, then search for an item."
			);
		}
	}

	private void search()
	{
		if (!config.marketLookup())
		{
			renderMessage(searchResults, "Enable Market lookup in the plugin settings first.");
			return;
		}
		renderMessage(searchResults, "Searching the Wiki item catalog...");
		clear(marketDetails);
		api.searchItems(searchField.getText(), new GeTrendsApiClient.ResultCallback<List<GeTrendsApiClient.ItemEntry>>()
		{
			@Override
			public void onSuccess(List<GeTrendsApiClient.ItemEntry> items)
			{
				renderSearchResults(items);
			}

			@Override
			public void onError(String message)
			{
				renderMessage(searchResults, message);
			}
		});
	}

	private void renderSearchResults(List<GeTrendsApiClient.ItemEntry> items)
	{
		clear(searchResults);
		if (items.isEmpty())
		{
			searchResults.add(mutedLabel("No matching tradeable items found."));
		}
		for (GeTrendsApiClient.ItemEntry item : items)
		{
			JButton result = secondaryButton(item.name);
			result.setHorizontalAlignment(SwingConstants.LEFT);
			result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
			result.addActionListener(event -> loadMarket(item));
			searchResults.add(result);
			searchResults.add(Box.createVerticalStrut(3));
		}
		refresh(searchResults);
	}

	private void loadMarket(GeTrendsApiClient.ItemEntry item)
	{
		clear(searchResults);
		renderMessage(marketDetails, "Loading " + item.name + "...");
		api.loadMarket(item, new GeTrendsApiClient.ResultCallback<GeTrendsApiClient.MarketSnapshot>()
		{
			@Override
			public void onSuccess(GeTrendsApiClient.MarketSnapshot value)
			{
				renderMarket(value);
			}

			@Override
			public void onError(String message)
			{
				renderMessage(marketDetails, message);
			}
		});
	}

	private void renderMarket(GeTrendsApiClient.MarketSnapshot market)
	{
		clear(marketDetails);
		JPanel heading = card();
		JLabel name = new JLabel("<html><b>" + html(market.item.name) + "</b></html>");
		name.setFont(name.getFont().deriveFont(Font.BOLD, 15f));
		heading.add(name);
		heading.add(Box.createVerticalStrut(8));
		heading.add(statRow("Wiki buy / high", gp(market.high), GOLD));
		heading.add(statRow("Wiki sell / low", gp(market.low), GREEN));
		if (market.high != null && market.low != null && market.high > 0)
		{
			long margin = market.high - market.low;
			heading.add(statRow("Live spread", gp(margin), margin >= 0 ? GREEN : RED));
		}
		if (market.item.buyLimit != null)
		{
			heading.add(statRow("GE buy limit", WHOLE_GP.format(market.item.buyLimit), Color.WHITE));
		}
		heading.add(Box.createVerticalStrut(5));
		heading.add(mutedLabel(
			"<html>High: " + age(market.highTime) + " &nbsp; Low: " + age(market.lowTime) + "</html>"));
		marketDetails.add(heading);
		marketDetails.add(Box.createVerticalStrut(8));

		JPanel chartCard = card();
		JLabel chartTitle = new JLabel("<html><b>30-day market</b></html>");
		chartCard.add(chartTitle);
		chartCard.add(Box.createVerticalStrut(5));
		MarketChartPanel chart = new MarketChartPanel();
		chart.setPoints(market.points);
		chartCard.add(chart);
		chartCard.add(mutedLabel("<html><font color='#f3b61f'>— Buy/high</font> &nbsp; "
			+ "<font color='#20c58d'>— Sell/low</font></html>"));
		marketDetails.add(chartCard);
		marketDetails.add(Box.createVerticalStrut(6));
		marketDetails.add(mutedLabel(
			"<html>Source: prices.runescape.wiki. Prices are observations, not guarantees.</html>"));

		JButton another = secondaryButton("Search another item");
		another.addActionListener(event ->
		{
			clear(marketDetails);
			searchField.requestFocusInWindow();
		});
		marketDetails.add(Box.createVerticalStrut(8));
		marketDetails.add(another);
		refresh(marketDetails);
	}

	private void renderPortfolio(GeTrendsApiClient.PortfolioSnapshot portfolio)
	{
		clear(portfolioContent);
		GeTrendsApiClient.PortfolioSummary summary = portfolio.summary;
		if (summary == null)
		{
			renderMessage(portfolioContent, "Portfolio summary is unavailable.");
			return;
		}

		JPanel total = card();
		total.add(mutedLabel("TOTAL PORTFOLIO VALUE"));
		JLabel value = new JLabel(gp(summary.totalMarketValue));
		value.setFont(value.getFont().deriveFont(Font.BOLD, 21f));
		total.add(value);
		total.add(Box.createVerticalStrut(8));
		total.add(statRow("Net P/L", signedGp(summary.totalProfit),
			summary.totalProfit >= 0 ? GREEN : RED));
		total.add(statRow("Unrealized", signedGp(summary.unrealizedProfit),
			summary.unrealizedProfit >= 0 ? GREEN : RED));
		total.add(statRow("Realized", signedGp(summary.realizedProfit),
			summary.realizedProfit >= 0 ? GREEN : RED));
		total.add(statRow("Investments", gp(summary.investedMarketValue), Color.WHITE));
		if (portfolio.includeLiquidGp)
		{
			total.add(statRow("Liquid GP", gp(summary.cashBalance), Color.WHITE));
			total.add(statRow("Held in GE", gp(summary.geHeldGp), Color.WHITE));
		}
		total.add(Box.createVerticalStrut(5));
		total.add(mutedLabel("Last RuneLite sync: " + syncAge(portfolio.lastRuneLiteSync)));
		portfolioContent.add(total);
		portfolioContent.add(Box.createVerticalStrut(10));

		JLabel holdingsTitle = new JLabel("<html><b>Open investments</b></html>");
		holdingsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		portfolioContent.add(holdingsTitle);
		portfolioContent.add(Box.createVerticalStrut(6));
		if (portfolio.holdings == null || portfolio.holdings.isEmpty())
		{
			portfolioContent.add(mutedLabel("No open investments yet."));
		}
		else
		{
			for (GeTrendsApiClient.Holding holding : portfolio.holdings)
			{
				JPanel row = card();
				row.add(new JLabel("<html><b>" + html(holding.name) + "</b></html>"));
				row.add(mutedLabel(WHOLE_GP.format(holding.quantity) + " units · "
					+ gp(holding.marketPrice)));
				double profit = holding.unrealizedProfit == null ? 0 : holding.unrealizedProfit;
				String percent = holding.unrealizedPercent == null
					? "—" : PERCENT.format(holding.unrealizedPercent) + "%";
				row.add(statRow("Unrealized", signedGp(profit) + "  " + percent,
					profit >= 0 ? GREEN : RED));
				portfolioContent.add(row);
				portfolioContent.add(Box.createVerticalStrut(5));
			}
		}
		portfolioContent.add(Box.createVerticalStrut(10));
		renderClosedTrades(portfolio);
		refresh(portfolioContent);
	}

	private void renderClosedTrades(GeTrendsApiClient.PortfolioSnapshot portfolio)
	{
		JLabel title = new JLabel("<html><b>Closed trade performance</b></html>");
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		portfolioContent.add(title);
		portfolioContent.add(Box.createVerticalStrut(6));
		if (portfolio.closedTrades == null || portfolio.closedTrades.isEmpty())
		{
			portfolioContent.add(mutedLabel("Completed matched sales will appear here."));
			return;
		}

		GeTrendsApiClient.ClosedTrade best = null;
		int profitable = 0;
		for (GeTrendsApiClient.ClosedTrade trade : portfolio.closedTrades)
		{
			if (trade.realizedProfit > 0)
			{
				profitable++;
			}
			if (best == null || trade.realizedProfit > best.realizedProfit)
			{
				best = trade;
			}
		}
		JPanel summary = card();
		summary.add(statRow(
			"Win rate",
			String.format(Locale.ENGLISH, "%.1f%%", profitable * 100d / portfolio.closedTrades.size()),
			profitable * 2 >= portfolio.closedTrades.size() ? GREEN : GOLD
		));
		summary.add(statRow(
			"Best sale",
			best == null ? "—" : signedGp(best.realizedProfit),
			best != null && best.realizedProfit >= 0 ? GREEN : RED
		));
		if (best != null)
		{
			summary.add(mutedLabel(best.name + " · " + PERCENT.format(best.roi) + "% ROI"));
		}
		portfolioContent.add(summary);
		portfolioContent.add(Box.createVerticalStrut(6));

		int displayed = Math.min(10, portfolio.closedTrades.size());
		for (int index = 0; index < displayed; index++)
		{
			GeTrendsApiClient.ClosedTrade trade = portfolio.closedTrades.get(index);
			JPanel row = card();
			row.add(new JLabel("<html><b>" + html(trade.name) + "</b></html>"));
			row.add(mutedLabel(WHOLE_GP.format(trade.quantity) + " sold · "
				+ gp(trade.sellPrice)));
			row.add(statRow(
				"Realized",
				signedGp(trade.realizedProfit) + "  " + PERCENT.format(trade.roi) + "%",
				trade.realizedProfit >= 0 ? GREEN : RED
			));
			portfolioContent.add(row);
			portfolioContent.add(Box.createVerticalStrut(5));
		}
	}

	private static JPanel statRow(String label, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		row.add(mutedLabel(label), BorderLayout.WEST);
		JLabel right = new JLabel(value);
		right.setForeground(valueColor);
		row.add(right, BorderLayout.EAST);
		return row;
	}

	private static JPanel card()
	{
		JPanel card = verticalPanel();
		card.setBackground(new Color(25, 34, 43));
		card.setOpaque(true);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(48, 59, 69)),
			BorderFactory.createEmptyBorder(10, 10, 10, 10)
		));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		return card;
	}

	private static JPanel verticalPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		return panel;
	}

	private static JButton tabButton(String text)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		return button;
	}

	private static JButton primaryButton(String text)
	{
		JButton button = new JButton(text);
		button.setBackground(GOLD);
		button.setForeground(Color.BLACK);
		button.setFocusPainted(false);
		return button;
	}

	private static JButton secondaryButton(String text)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		return button;
	}

	private static JLabel mutedLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(MUTED);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static void selectTab(JButton selected, JButton other)
	{
		selected.setBackground(GOLD);
		selected.setForeground(Color.BLACK);
		other.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		other.setForeground(Color.WHITE);
	}

	private static void renderMessage(JPanel panel, String message)
	{
		clear(panel);
		JLabel label = mutedLabel("<html><div style='text-align:center;width:200px'>"
			+ html(message) + "</div></html>");
		label.setBorder(BorderFactory.createEmptyBorder(18, 10, 18, 10));
		panel.add(label);
		refresh(panel);
	}

	private static void clear(JPanel panel)
	{
		panel.removeAll();
	}

	private static void refresh(JPanel panel)
	{
		panel.revalidate();
		panel.repaint();
	}

	private static String gp(Number value)
	{
		return value == null ? "—" : WHOLE_GP.format(value.doubleValue()) + " GP";
	}

	private static String signedGp(double value)
	{
		return (value >= 0 ? "+" : "") + WHOLE_GP.format(value) + " GP";
	}

	private static String age(Long epochSeconds)
	{
		if (epochSeconds == null)
		{
			return "not available";
		}
		long seconds = Math.max(0, Duration.between(
			Instant.ofEpochSecond(epochSeconds), Instant.now()).getSeconds());
		if (seconds < 60)
		{
			return seconds + "s ago";
		}
		if (seconds < 3_600)
		{
			return seconds / 60 + "m ago";
		}
		return seconds / 3_600 + "h ago";
	}

	private static String syncAge(String timestamp)
	{
		try
		{
			return age(Instant.parse(timestamp).getEpochSecond());
		}
		catch (Exception ignored)
		{
			return "not synced yet";
		}
	}

	private static String html(String value)
	{
		return value == null ? "" : value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;");
	}
}
