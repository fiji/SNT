/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2019 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package sc.fiji.snt.gui;

import com.jidesoft.swing.event.SearchableEvent;
import com.jidesoft.swing.event.SearchableListener;

import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;

import sc.fiji.snt.gui.cmds.SWCTypeFilterCmd;
import sc.fiji.snt.Path;
import sc.fiji.snt.PathManagerUI;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.TreeStatistics;

/**
 * Implements the customized SearchableBar used by {@link PathManagerUI},
 * including GUI commands for selection and morphological filtering of Paths.
 *
 * @author Tiago Ferreira
 */
public class PathManagerUISearchableBar extends SNTSearchableBar {

	private static final long serialVersionUID = 1L;
	private final PathManagerUI pmui;
	private final GuiUtils guiUtils;

	static {
		buttonHeight = (int) new JComboBox<String>().getPreferredSize().getHeight();
		iconHeight = UIManager.getFont("Label.font").getSize();
	}

	/**
	 * Creates PathManagerUI's SearchableBar
	 *
	 * @param pmui the PathManagerUI instance
	 */
	public PathManagerUISearchableBar(final PathManagerUI pmui) {
		super(pmui.getSearchable());
		this.pmui = pmui;
		guiUtils = new GuiUtils(pmui);
		_extraButton = createMenuButton();
		setVisibleButtons(SHOW_NAVIGATION | SHOW_STATUS | SHOW_HIGHLIGHTS);
		setStatusLabelPlaceholder(String.format("%d Path(s) listed", pmui
			.getPathAndFillManager().size()));
		setShowMatchCount(true); // will slow things down slightly
	}

	private JPopupMenu getPopupMenu() {
		final JPopupMenu popup = new JPopupMenu();
		final JMenu optionsMenu = new JMenu("Text Filtering");
		optionsMenu.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.TEXT));
		final JMenuItem jcbmi1 = new JCheckBoxMenuItem("Case Sensitive Matching",
			getSearchable().isCaseSensitive());
		jcbmi1.addItemListener(e -> {
			getSearchable().setCaseSensitive(jcbmi1.isSelected());
			updateSearch();
		});
		optionsMenu.add(jcbmi1);
		final JMenuItem jcbmi2 = new JCheckBoxMenuItem("Enable Wildcards (?*)",
			getSearchable().isWildcardEnabled());
		jcbmi2.addItemListener(e -> {
			getSearchable().setWildcardEnabled(jcbmi2.isSelected());
			updateSearch();
		});
		optionsMenu.add(jcbmi2);
		final JMenuItem jcbmi3 = new JCheckBoxMenuItem("Loop After First/Last Hit",
			getSearchable().isRepeats());
		jcbmi3.addItemListener(e -> getSearchable().setRepeats(jcbmi3
			.isSelected()));
		optionsMenu.add(jcbmi3);
		final JMenuItem jcbmi4 = new JCheckBoxMenuItem("Display No. of Matches", getSearchable().isCountMatch());
		jcbmi3.addItemListener(e -> getSearchable().setCountMatch(jcbmi4.isSelected()));
		optionsMenu.add(jcbmi4);
		optionsMenu.addSeparator();

		JMenuItem mi = new JMenuItem("Replace...");
		mi.addActionListener(e -> {
			String findText = getSearchingText();
			if (findText == null || findText.isEmpty()) {
				guiUtils.error("No filtering string exists.", "No Filter String");
				return;
			}
			final boolean clickOnHighlightAllNeeded = !isHighlightAll();
			if (clickOnHighlightAllNeeded) _highlightsButton.doClick();
			final Collection<Path> selectedPath = pmui.getSelectedPaths(false);
			if (clickOnHighlightAllNeeded) _highlightsButton.doClick(); // restore
																																	// status
			if (selectedPath.isEmpty()) {
				guiUtils.error("No Paths matching '" + findText + "'.",
					"No Paths Selected");
				return;
			}
			final String replaceText = guiUtils.getString(
				"Please specify the text to replace all ocurrences of\n" + "\"" +
					findText + "\" in the " + selectedPath.size() +
					" Path(s) currently selected:", "Replace Filtering Pattern", null);
			if (replaceText == null) {
				return; // user pressed cancel
			}
			if (getSearchable().isWildcardEnabled()) {
				findText = findText.replaceAll("\\?", ".?");
				findText = findText.replaceAll("\\*", ".*");
			}
			if (!getSearchable().isCaseSensitive()) {
				findText = "(?i)" + findText;
			}
			try {
				final Pattern pattern = Pattern.compile(findText);
				for (final Path p : selectedPath) {
					p.setName(pattern.matcher(p.getName()).replaceAll(replaceText));
				}
			}
			catch (final IllegalArgumentException ex) {
				guiUtils.error("Replacement pattern not valid: " + ex.getMessage());
				return;
			}
			pmui.update();
		});
		optionsMenu.add(mi);
		mi = new JMenuItem("Clear History");
		mi.addActionListener(e -> setSearchHistory(null));
		optionsMenu.add(mi);
		optionsMenu.addSeparator();
		final JMenuItem mi2 = new JMenuItem("Tips & Shortcuts...");
		mi2.addActionListener(e -> filterHelpMsg());
		optionsMenu.add(mi2);
		popup.add(optionsMenu);
		popup.addSeparator();
		popup.add(getColorFilterMenu());
		popup.add(getMorphoFilterMenu());
		return popup;
	}

	public JMenu getMorphoFilterMenu() {
		final JMenu morphoFilteringMenu = new JMenu("Morphology Filters");
		morphoFilteringMenu.setIcon(IconFactory.getMenuIcon(
			IconFactory.GLYPH.RULER));
		JMenuItem mi1 = new JMenuItem("Length...");
		mi1.addActionListener(e -> {
			final String unit = pmui.getPathAndFillManager().getBoundingBox(false)
				.getUnit();
			doMorphoFiltering(TreeStatistics.LENGTH, unit);
		});
		morphoFilteringMenu.add(mi1);
		mi1 = new JMenuItem("Mean Radius...");
		mi1.addActionListener(e -> doMorphoFiltering(TreeStatistics.MEAN_RADIUS, ""));
		morphoFilteringMenu.add(mi1);
		mi1 = new JMenuItem("No. of Nodes...");
		mi1.addActionListener(e -> doMorphoFiltering(TreeStatistics.N_NODES, ""));
		morphoFilteringMenu.add(mi1);
		mi1 = new JMenuItem("Path Order...");
		mi1.addActionListener(e -> doMorphoFiltering(TreeStatistics.PATH_ORDER,
			""));
		morphoFilteringMenu.add(mi1);
		mi1 = new JMenuItem("SWC Type...");
		mi1.addActionListener(e -> {
			final List<Path> paths = pmui.getPathAndFillManager().getPathsFiltered();
			if (paths.size() == 0) {
				guiUtils.error("There are no traced paths.");
				return;
			}
			class GetFilteredTypes extends SwingWorker<Object, Object> {

				CommandModule cmdModule;

				@Override
				public Object doInBackground() {
					final CommandService cmdService = pmui.getSNT()
						.getContext().getService(CommandService.class);
					try {
						cmdModule = cmdService.run(SWCTypeFilterCmd.class, true).get();
					}
					catch (InterruptedException | ExecutionException ignored) {
						return null;
					}
					return null;
				}

				@Override
				protected void done() {
					final Set<Integer> types = SWCTypeFilterCmd.getChosenTypes(pmui
						.getSNT().getContext());
					if ((cmdModule != null && cmdModule.isCanceled()) || types == null ||
						types.isEmpty())
					{
						return; // user pressed cancel or chose nothing
					}
					paths.removeIf(path -> !types.contains(path.getSWCType()));
					if (paths.isEmpty()) {
						guiUtils.error("No Path matches the specified type(s).");
						return;
					}
					pmui.setSelectedPaths(new HashSet<>(paths), this);
					guiUtils.tempMsg(paths.size() + " Path(s) selected");
				}
			}
			(new GetFilteredTypes()).execute();
		});
		morphoFilteringMenu.add(mi1);
		return morphoFilteringMenu;
	}

	public ColorMenu getColorFilterMenu() {
		final ColorMenu colorFilterMenu = new ColorMenu("Color Filters");
		colorFilterMenu.setIcon(IconFactory.getMenuIcon(IconFactory.GLYPH.COLOR));
		colorFilterMenu.addActionListener(e -> {
			final List<Path> filteredPaths = pmui.getPathAndFillManager()
				.getPathsFiltered();
			if (filteredPaths.isEmpty()) {
				guiUtils.error("There are no traced paths.");
				return;
			}
			final Color filteredColor = colorFilterMenu.getSelectedSWCColor().color();
			for (final Iterator<Path> iterator = filteredPaths.iterator(); iterator
				.hasNext();)
			{
				final Color color = iterator.next().getColor();
				if ((filteredColor != null && color != null && !filteredColor.equals(
					color)) || (filteredColor == null && color != null) ||
					(filteredColor != null && color == null))
				{
					iterator.remove();
				}
			}
			if (filteredPaths.isEmpty()) {
				guiUtils.error("No Path matches the specified color tag.");
				return;
			}
			pmui.setSelectedPaths(new HashSet<>(filteredPaths), this);
			guiUtils.tempMsg(filteredPaths.size() + " Path(s) selected");
			// refreshManager(true, true);
		});
		return colorFilterMenu;
	}

	private void updateSearch() {
		final SearchableListener[] listeners = getSearchable()
			.getSearchableListeners();
		for (final SearchableListener l : listeners)
			l.searchableEventFired(new SearchableEvent(getSearchable(),
				SearchableEvent.SEARCHABLE_MODEL_CHANGE));

	}

	private void doMorphoFiltering(final String property, final String unit) {
		final List<Path> filteredPaths = pmui.getPathAndFillManager()
			.getPathsFiltered();
		if (filteredPaths.isEmpty()) {
			guiUtils.error("There are no traced paths.");
			return;
		}
		String msg = "Please specify the " + property.toLowerCase() + " range";
		if (!unit.isEmpty()) msg += " (in " + unit + ")";
		msg += "\n(e.g., 10-50, min-10, 10-max, max-max):";
		String s = guiUtils.getString(msg, property + " Filtering", "10-100");
		if (s == null) return; // user pressed cancel
		s = s.toLowerCase();

		double min = Double.MIN_VALUE;
		double max = Double.MAX_VALUE;
		if (s.contains("min") || s.contains("max")) {
			final TreeStatistics treeStats = new TreeStatistics(new Tree(
				filteredPaths));
			final SummaryStatistics summary = treeStats.getSummaryStats(property);
			min = summary.getMin();
			max = summary.getMax();
		}
		final double[] values = new double[] { min, max };
		try {
			final String[] stringValues = s.toLowerCase().split("-");
			for (int i = 0; i < values.length; i++) {
				if (stringValues[i].contains("min")) values[i] = min;
				else if (stringValues[i].contains("max")) values[i] = max;
				else values[i] = Double.parseDouble(stringValues[i]);
			}
		}
		catch (final Exception ignored) {
			guiUtils.error(
				"Invalid range. Example of valid inputs: 10-100, min-10, 100-max, max-max");
			return;
		}

		for (final Iterator<Path> iterator = filteredPaths.iterator(); iterator
			.hasNext();)
		{
			final Path p = iterator.next();
			double value;
			switch (property) {
				case TreeStatistics.LENGTH:
					value = p.getLength();
					break;
				case TreeStatistics.N_NODES:
					value = p.size();
					break;
				case TreeStatistics.MEAN_RADIUS:
					value = p.getMeanRadius();
					break;
				case TreeStatistics.PATH_ORDER:
					value = p.getOrder();
					break;
				default:
					throw new IllegalArgumentException("Unrecognized parameter");
			}
			if (value < values[0] || value > values[1]) iterator.remove();
		}
		if (filteredPaths.isEmpty()) {
			guiUtils.error("No Path matches the specified range.");
			return;
		}
		pmui.setSelectedPaths(new HashSet<>(filteredPaths), this);
		guiUtils.tempMsg(filteredPaths.size() + " Path(s) selected");
		// refreshManager(true, true);
	}

	protected JButton createMenuButton() {
		final JButton button = new JButton();
		formatButton(button, IconFactory.GLYPH.FILTER);
		button.setToolTipText("Advanced Filtering Menu");
		final JPopupMenu pMenu = getPopupMenu();
		button.addActionListener(e -> pMenu.show(button, button.getWidth() / 2,
			button.getHeight() / 2));
		return button;
	}

	private void filterHelpMsg() {
		final String key = GuiUtils.ctrlKey();
		final String msg = "<HTML><body><div style='width:500;'><ol>" +
			"<li>Search is case-insensitive by default. Wildcards " +
			"<b>?</b> (any character), and <b>*</b> (any string) can also be used</li>" +
			"<li>Press the <i>Highlight All</i> button or " + key +
			"+A to select all the paths filtered by the search string</li>" +
			"<li>Press and hold " + key +
			" while pressing the up/down keys to select multiple filtered paths</li>" +
			"<li>Press the up/down keys to find the next/previous occurrence of the filtering string</li>" +
			"<li>Uncheck <i>Display No. of Matches</i> to improve search performance</li>" +
			"</ol></div></html>";
		guiUtils.centeredMsg(msg, "Text-based Filtering");
	}

	/* IDE Debug method */
	public static void main(final String[] args) {
		GuiUtils.setSystemLookAndFeel();
		PathManagerUI.main(args);
	}
}
