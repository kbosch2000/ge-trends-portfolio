package com.getrends;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

final class MarketChartPanel extends JPanel
{
	private static final Color GRID = new Color(61, 71, 80);
	private static final Color HIGH = new Color(243, 182, 31);
	private static final Color LOW = new Color(32, 197, 141);
	private List<GeTrendsApiClient.PricePoint> points = new ArrayList<>();

	MarketChartPanel()
	{
		setOpaque(false);
		setPreferredSize(new Dimension(220, 150));
	}

	void setPoints(List<GeTrendsApiClient.PricePoint> points)
	{
		this.points = new ArrayList<>(points);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int left = 8;
			int top = 10;
			int width = Math.max(1, getWidth() - 16);
			int height = Math.max(1, getHeight() - 22);
			g.setColor(GRID);
			for (int i = 0; i <= 3; i++)
			{
				int y = top + (height * i / 3);
				g.drawLine(left, y, left + width, y);
			}

			long min = Long.MAX_VALUE;
			long max = Long.MIN_VALUE;
			for (GeTrendsApiClient.PricePoint point : points)
			{
				if (point.high != null)
				{
					min = Math.min(min, point.high);
					max = Math.max(max, point.high);
				}
				if (point.low != null)
				{
					min = Math.min(min, point.low);
					max = Math.max(max, point.low);
				}
			}
			if (min == Long.MAX_VALUE || points.size() < 2)
			{
				g.setColor(new Color(140, 150, 160));
				g.drawString("No 30-day chart data", left + 38, top + height / 2);
				return;
			}
			if (max == min)
			{
				max++;
			}

			drawSeries(g, left, top, width, height, min, max, true, HIGH);
			drawSeries(g, left, top, width, height, min, max, false, LOW);
		}
		finally
		{
			g.dispose();
		}
	}

	private void drawSeries(
		Graphics2D g,
		int left,
		int top,
		int width,
		int height,
		long min,
		long max,
		boolean highSeries,
		Color color)
	{
		g.setColor(color);
		g.setStroke(new BasicStroke(2f));
		int previousX = -1;
		int previousY = -1;
		for (int i = 0; i < points.size(); i++)
		{
			Long value = highSeries ? points.get(i).high : points.get(i).low;
			if (value == null)
			{
				continue;
			}
			int x = left + (points.size() == 1 ? 0 : width * i / (points.size() - 1));
			int y = top + height - (int) ((value - min) * height / (double) (max - min));
			if (previousX >= 0)
			{
				g.drawLine(previousX, previousY, x, y);
			}
			previousX = x;
			previousY = y;
		}
	}
}
