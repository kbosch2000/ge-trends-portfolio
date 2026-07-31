package com.getrends;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class BrandIcon
{
	private BrandIcon()
	{
	}

	static BufferedImage create()
	{
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Color navy = new Color(10, 24, 34);
			Color gold = new Color(243, 182, 31);
			Color teal = new Color(32, 197, 141);

			g.setColor(navy);
			g.fillOval(1, 1, 30, 30);
			g.setStroke(new BasicStroke(2.4f));
			g.setColor(gold);
			g.drawOval(2, 2, 28, 28);
			g.drawLine(6, 24, 26, 24);
			g.drawLine(16, 24, 16, 28);

			g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(teal);
			g.drawPolyline(
				new int[]{6, 10, 13, 17, 21, 26},
				new int[]{20, 15, 18, 11, 14, 7},
				6
			);
			g.setColor(gold);
			g.fillPolygon(new int[]{23, 27, 27}, new int[]{7, 6, 10}, 3);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}
}
