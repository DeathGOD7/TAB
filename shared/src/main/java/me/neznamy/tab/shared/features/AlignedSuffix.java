package me.neznamy.tab.shared.features;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.cpu.TabFeature;
import me.neznamy.tab.shared.features.types.event.QuitEventListener;
import me.neznamy.tab.shared.features.types.event.WorldChangeListener;
import me.neznamy.tab.shared.packets.IChatBaseComponent;

/**
 * Additional code for Playerlist class to secure alignment
 */
public class AlignedSuffix implements QuitEventListener, WorldChangeListener {

	private TAB tab;
	private int maxWidth;
	private TabPlayer maxPlayer;
	private Map<Character, Byte> widths = new HashMap<Character, Byte>();
	private Playerlist playerlist;

	public AlignedSuffix(Playerlist playerlist, TAB tab) {
		this.tab = tab;
		this.playerlist = playerlist;
		loadWidthsFromFile();
	}
	
	/**
	 * Loads all predefined widths from included widths.txt file
	 */
	private void loadWidthsFromFile() {
		try {
			InputStream input = getClass().getClassLoader().getResourceAsStream("widths.txt");
			BufferedReader br = new BufferedReader(new InputStreamReader(input));
			String line;
			while ((line = br.readLine()) != null) {
				String[] arr = line.split(":");
				widths.put((char)Integer.parseInt(arr[0]), (byte)Float.parseFloat(arr[1]));
			}
			br.close();
		} catch (Exception ex) {
			tab.getErrorManager().criticalError("Failed to read character widths from file", ex);
		}
	}
	
	public String formatNameAndUpdateLeader(TabPlayer player, String prefixAndName, String suffix) {
		int playerNameWidth = getTextWidth(IChatBaseComponent.fromColoredText(prefixAndName + suffix));
		if (player == maxPlayer && playerNameWidth < maxWidth) {
			maxWidth = playerNameWidth;
			for (TabPlayer all : tab.getPlayers()) {
				int localWidth = getPlayerNameWidth(all);
				if (localWidth > maxWidth) {
					maxWidth = localWidth;
					maxPlayer = all;
				}
			}
			updateAllNames(null);
		} else if (playerNameWidth > maxWidth) {
			maxWidth = playerNameWidth;
			maxPlayer = player;
			updateAllNames(player);
		}
		return formatName(prefixAndName, suffix);
	}
	
	public String formatName(String prefixAndName, String suffix) {
		int playerNameWidth = getTextWidth(IChatBaseComponent.fromColoredText(prefixAndName + suffix));
		String newFormat = prefixAndName + "\u00a7r";
		try {
			newFormat += buildSpaces(maxWidth + 12 - playerNameWidth);
		} catch (IllegalArgumentException e) {
			//will investigate later
			newFormat += buildSpaces(12);
		}
		newFormat += tab.getPlaceholderManager().getLastColors(prefixAndName) + suffix;
		return newFormat;
	}
	
	/**
	 * Returns text width of characters in given component
	 * @param component - component to get width of
	 * @return text width of characters in given component
	 */
	private int getTextWidth(IChatBaseComponent component) {
		int width = 0;
		if (component.getText() != null) {
			for (Character c : component.getText().toCharArray()) {
				if (widths.containsKey(c)) {
					int localWidth = widths.get(c) + 1;
					if (component.isBold()) {
						width += localWidth+1;
					} else {
						width += localWidth;
					}
				} else {
					tab.getErrorManager().oneTimeConsoleError("Unknown character " + c + " (" + ((int)c) + ") found when aligning tabsuffix. Configure it using /tab width <character|ID>.");
				}
			}
		}
		for (IChatBaseComponent extra : component.getExtra()) {
			width += getTextWidth(extra);
		}
		return width;
	}
	
	/**
	 * Returns width of player's tablist name format
	 * @param p - player to get width for
	 * @return width of player's tablist name format
	 */
	private int getPlayerNameWidth(TabPlayer p) {
		String format = p.getProperty("tabprefix").get() + p.getProperty("customtabname").get() + p.getProperty("tabsuffix").get();
		return getTextWidth(IChatBaseComponent.fromColoredText(format));
	}
	
	/**
	 * Returns a combination of normal and bold spaces to build exactly the requested amount of pixels.
	 * Must be at least 12 as lower numbers cannot always be built using numbers 4 (normal space + 1 pixel) and 5 (bold space + 1 pixel)
	 * Returns the result string with normal then bold spaces, such as "   &l   &r"
	 * @param pixelWidth - amount of pixels to be built
	 * @return string consisting of spaces and &l &r
	 * @throws IllegalArgumentException if pixelWidth is < 12
	 */
	private String buildSpaces(int pixelWidth) {
		if (pixelWidth < 12) throw new IllegalArgumentException("Cannot build space lower than 12 pixels wide");
		int pixelsLeft = pixelWidth;
		StringBuilder output = new StringBuilder();
		while (pixelsLeft % 5 != 0) {
			pixelsLeft -= 4;
			output.append(" ");
		}
		output.append("\u00a7l");
		while (pixelsLeft > 0) {
			pixelsLeft -= 5;
			output.append(" ");
		}
		output.append("\u00a7r");
		return output.toString();
	}
	
	@Override
	public void onQuit(TabPlayer p) {
		if (maxPlayer == p && recalculateMaxWidth(p)) {
			updateAllNames(p);
		}
	}
	
	@Override
	public void onWorldChange(TabPlayer p, String from, String to) {
		if (maxPlayer == p && recalculateMaxWidth(null)) {
			updateAllNames(null);
		}
	}

	private void updateAllNames(TabPlayer exception) {
		for (TabPlayer all : tab.getPlayers()) {
			if (all == exception) continue;
			playerlist.refresh(all, true);
		}
	}

	// returns true if max changed, false if not
	private boolean recalculateMaxWidth(TabPlayer ignoredPlayer) {
		int oldMaxWidth = maxWidth;
		maxWidth = 0;
		maxPlayer = null;
		for (TabPlayer all : tab.getPlayers()) {
			if (all == ignoredPlayer) continue;
			int localWidth = getPlayerNameWidth(all);
			if (localWidth > maxWidth) {
				maxWidth = localWidth;
				maxPlayer = all;
			}
		}
		return oldMaxWidth != maxWidth;
	}

	@Override
	public TabFeature getFeatureType() {
		return TabFeature.ALIGNED_TABSUFFIX;
	}
}