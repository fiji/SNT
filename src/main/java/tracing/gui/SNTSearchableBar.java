/**
 * 
 */
package tracing.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.scijava.command.CommandService;

import com.jidesoft.icons.JideIconsFactory;
import com.jidesoft.swing.SearchableBar;
import com.jidesoft.swing.event.SearchableEvent;
import com.jidesoft.swing.event.SearchableListener;

import tracing.Path;
import tracing.PathManagerUI;
import tracing.SNT;
import tracing.Tree;
import tracing.analysis.TreeAnalyzer;
import tracing.analysis.TreeStatistics;

/**
 * Implements the customized SearchableBar used by {@link PathManagerUI},
 * including GUI commands for morphological filtering of Paths.
 *
 * @author Tiago Ferreira
 */
public class SNTSearchableBar extends SearchableBar {
	private static final long serialVersionUID = 1L;
	private final PathManagerUI pmui;
	private final GuiUtils guiUtils;
	private JButton _menuButton;

	public SNTSearchableBar(final PathManagerUI pmui) {
		super(pmui.getSearchable(), true);
		this.pmui = pmui;
		this.createComboBox();
		guiUtils = new GuiUtils(pmui);
		setFloatable(true);
		setBorderPainted(false);
		setBorder(BorderFactory.createEmptyBorder());
		setMismatchForeground(Color.RED);
		setVisibleButtons(-1);
		setMaxHistoryLength(10);
		setShowMatchCount(true);
		setHighlightAll(false); // TODO: update to 3.6.19 see bugfix
								// https://github.com/jidesoft/jide-oss/commit/149bd6a53846a973dfbb589fffcc82abbc49610b
	}

	@Override
	public void setVisibleButtons(final int ignored) {
		super.setVisibleButtons(SHOW_NAVIGATION | SHOW_HIGHLIGHTS | SHOW_STATUS);
	}

	@Override
	protected void installComponents() {

		{ // This code is called in the constructor: These
			// initializations are required to avoid NPE
			_leadingLabel = new JLabel();
			if (getMaxHistoryLength() == 0) {
				_leadingLabel.setLabelFor(_textField);
				_textField.setVisible(true);
				_comboBox.setVisible(false);
			} else {
				_leadingLabel.setLabelFor(_comboBox);
				_comboBox.setVisible(true);
				_textField.setVisible(false);
			}
		}
		final JPanel buttonPanel = new JPanel(new GridLayout(1, 4));
		_menuButton = createMenuButton();
		adjustButtonHeight(_menuButton);
		buttonPanel.add(_menuButton);
		adjustButtonHeight(_findNextButton);
		buttonPanel.add(_findNextButton);
		adjustButtonHeight(_findPrevButton);
		buttonPanel.add(_findPrevButton);
		adjustButtonHeight(_highlightsButton);
		buttonPanel.add(_highlightsButton);

		final JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		topPanel.add(_comboBox);
		_comboBox.setToolTipText("Filter & Select Paths by Text Search");
		topPanel.add(buttonPanel);
		final JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		_statusLabel = new JLabel(SNT.getReadableVersion());
		_statusLabel.addPropertyChangeListener("text", evt -> {
			final String text = _statusLabel.getText();
			if (text == null || text.isEmpty())
				_statusLabel.setText(String.format("%d Path(s) listed", pmui.getPathAndFillManager().size()));
		});
		bottomPanel.add(_statusLabel);
		bottomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		topPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		add(topPanel);
		add(bottomPanel);
	}


	private JPopupMenu getPopupMenu() {
		final JPopupMenu popup = new JPopupMenu();
		final JMenu optionsMenu = new JMenu("Text Filtering");
		final JMenuItem jcbmi1 = new JCheckBoxMenuItem("Case Sensitive Matching", getSearchable().isCaseSensitive());
		jcbmi1.addItemListener(e -> {
			getSearchable().setCaseSensitive(jcbmi1.isSelected());
			updateSearch();
		});
		optionsMenu.add(jcbmi1);
		final JMenuItem jcbmi2 = new JCheckBoxMenuItem("Enable Wildcards (?*)", getSearchable().isWildcardEnabled());
		jcbmi2.addItemListener(e -> {
			getSearchable().setWildcardEnabled(jcbmi2.isSelected());
			updateSearch();
		});
		optionsMenu.add(jcbmi2);
		final JMenuItem jcbmi3 = new JCheckBoxMenuItem("Loop After First/Last Hit", getSearchable().isRepeats());
		jcbmi3.addItemListener(e -> getSearchable().setRepeats(jcbmi3.isSelected()));
		optionsMenu.add(jcbmi3);
		optionsMenu.addSeparator();

		JMenuItem mi = new JMenuItem("Replace...");
		mi.addActionListener(e -> {
			String findText = getSearchingText();
			if (findText == null || findText.isEmpty()) {
				guiUtils.error("No filtering string exists.", "No Filter String");
				return;
			}
			final Collection<Path> selectedPath = pmui.getSelectedPaths(false);
			if (selectedPath.isEmpty()) {
				guiUtils.error("No Paths matching '" + findText + "'.", "No Paths Selected");
				return;
			}
			final String replaceText = guiUtils
					.getString(
							"Please specify the text to replace all ocurrences of\n" + "\"" + findText + "\" in the "
									+ selectedPath.size() + " Path(s) currently selected:",
							"Replace Filtering Pattern", null);
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
			final Pattern pattern = Pattern.compile(findText);
			for (final Path p : selectedPath) {
				p.setName(pattern.matcher(p.getName()).replaceAll(replaceText));
			}
			pmui.refreshManager(false, false);
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

		final ColorMenu colorFilterMenu = new ColorMenu("Color Tag Filtering");
		popup.add(colorFilterMenu);
		colorFilterMenu.addActionListener(e -> {
			final List<Path> filteredPaths = pmui.getPathAndFillManager().getPathsFiltered();
			if (filteredPaths.isEmpty()) {
				guiUtils.error("There are no traced paths.");
				return;
			}
			final Color filteredColor = colorFilterMenu.getSelectedSWCColor().color();
			for (final Iterator<Path> iterator = filteredPaths.iterator(); iterator.hasNext();) {
				final Color color = iterator.next().getColor();
				if ((filteredColor != null && color != null && !filteredColor.equals(color))
						|| (filteredColor == null && color != null) || (filteredColor != null && color == null)) {
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
		popup.add(colorFilterMenu);

		final JMenu morphoFilteringMenu = new JMenu("Morphology Filtering");
		JMenuItem mi1 = new JMenuItem("Branch Order...");
		mi1.addActionListener(e -> doMorphoFiltering(TreeAnalyzer.BRANCH_ORDER, ""));
		morphoFilteringMenu.add(mi1);
		mi1 = new JMenuItem("Length...");
		mi1.addActionListener(e -> {
			final String unit = pmui.getPathAndFillManager().getBoundingBox(false).getUnit();
			doMorphoFiltering(TreeAnalyzer.LENGTH, unit);
		});
		morphoFilteringMenu.add(mi1);
		mi1 = new JMenuItem("Mean Radius...");
		mi1.addActionListener(e -> doMorphoFiltering(TreeAnalyzer.MEAN_RADIUS, ""));
		morphoFilteringMenu.add(mi1);
		mi1 = new JMenuItem("No. of Nodes...");
		mi1.addActionListener(e -> doMorphoFiltering(TreeAnalyzer.N_NODES, ""));
		morphoFilteringMenu.add(mi1);
		mi1 = new JMenuItem("SWC Type...");
		mi1.addActionListener(e -> {

			final List<Path> paths = pmui.getPathAndFillManager().getPathsFiltered();
			if (paths.size() == 0) {
				guiUtils.error("There are no traced paths.");
				return;
			}
			class GetFilteredTypes extends SwingWorker<Object, Object> {

				@Override
				public Object doInBackground() {
					final CommandService cmdService = pmui.getSimpleNeuriteTracer().getContext()
							.getService(CommandService.class);
					try {
						cmdService.run(SWCTypeFilterCmd.class, true).get();
					} catch (InterruptedException | ExecutionException ignored) {
						return null;
					}
					return null;
				}

				@Override
				protected void done() {
					final Set<Integer> types = SWCTypeFilterCmd
							.getChosenTypes(pmui.getSimpleNeuriteTracer().getContext());
					if (types == null || types.isEmpty()) {
						return; // user pressed cancel or chose nothing
					}
					for (final Iterator<Path> iterator = paths.iterator(); iterator.hasNext();) {
						if (!types.contains(iterator.next().getSWCType())) {
							iterator.remove();
						}
					}
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
		popup.add(morphoFilteringMenu);
		return popup;
	}

	private void updateSearch() {
		final SearchableListener[] listeners = getSearchable().getSearchableListeners();
		for (final SearchableListener l : listeners)
			l.searchableEventFired(new SearchableEvent(getSearchable(), SearchableEvent.SEARCHABLE_MODEL_CHANGE));

	};

	private void doMorphoFiltering(final String property, final String unit) {
		final List<Path> filteredPaths = pmui.getPathAndFillManager().getPathsFiltered();
		if (filteredPaths.isEmpty()) {
			guiUtils.error("There are no traced paths.");
			return;
		}
		String msg = "Please specify the " + property.toLowerCase() + " range";
		if (!unit.isEmpty())
			msg += " (in " + unit + ")";
		msg += "\n(e.g., 10-50, min-10, 10-max, max-max):";
		String s = guiUtils.getString(msg, property + " Filtering", "10-100");
		if (s == null)
			return; // user pressed cancel
		s = s.toLowerCase();

		double min = Double.MIN_VALUE;
		double max = Double.MAX_VALUE;
		if (s.contains("min") || s.contains("max")) {
			final TreeStatistics treeStats = new TreeStatistics(new Tree(filteredPaths));
			final SummaryStatistics summary = treeStats.getSummaryStats(property);
			min = summary.getMin();
			max = summary.getMax();
		}
		final double[] values = new double[] { min, max };
		try {
			final String[] stringValues = s.toLowerCase().split("-");
			for (int i = 0; i < values.length; i++) {
				if (stringValues[i].contains("min"))
					values[i] = min;
				else if (stringValues[i].contains("max"))
					values[i] = max;
				else
					values[i] = Double.parseDouble(stringValues[i]);
			}
		} catch (final Exception ignored) {
			guiUtils.error("Invalid range. Example of valid inputs: 10-100, min-10, 100-max, max-max");
			return;
		}

		for (final Iterator<Path> iterator = filteredPaths.iterator(); iterator.hasNext();) {
			final Path p = iterator.next();
			double value;
			switch (property) {
			case TreeAnalyzer.LENGTH:
				value = p.getLength();
				break;
			case TreeAnalyzer.N_NODES:
				value = p.size();
				break;
			case TreeAnalyzer.MEAN_RADIUS:
				value = p.getMeanRadius();
				break;
			case TreeAnalyzer.BRANCH_ORDER:
				value = p.getOrder();
				break;
			default:
				throw new IllegalArgumentException("Unrecognized parameter");
			}
			if (value < values[0] || value > values[1])
				iterator.remove();
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
		final ImageIcon icon =JideIconsFactory.getImageIcon(JideIconsFactory.Arrow.DOWN);
		final JButton button = new JButton(icon);
		button.setToolTipText("Advanced Filters & Options");
		button.setRequestFocusEnabled(false);
		button.setFocusable(false);
		final JPopupMenu pMenu = getPopupMenu();
		button.addActionListener(e -> pMenu.show(button, 0, 0));
		return button;
	}

	private void adjustButtonHeight(final AbstractButton button) {
		final int height = (int) createComboBox().getPreferredSize().getHeight();
		button.setPreferredSize(new Dimension(button.getPreferredSize().width, height));
//		button.setBorder(null);
//		button.setMargin(new Insets(2,2,2,2));
//		button.setContentAreaFilled(false);

	}

	private void filterHelpMsg() {
		final String key = GuiUtils.ctrlKey();
		final String msg = "<HTML><body><div style='width:500;'><ol>"
				+ "<li>Filtering is case-insensitive by default. Wildcards "
				+ "<b>?</b> (any character), and <b>*</b> (any string) can also be used</li>"
				+ "<li>Press the <i>Highlight All</i> button or " + key
				+ "+A to select all the paths filtered by the search string</li>" + "<li>Press and hold " + key
				+ " while pressing the up/down keys to select multiple filtered paths</li>"
				+ "<li>Press the up/down keys to find the next/previous occurrence of the filtering string</li>"
				+ "</ol></div></html>";
		guiUtils.centeredMsg(msg, "Text-based Filtering");
	}

	@SuppressWarnings("unused")
	private void resizeButton(final AbstractButton button) {
		final double SCALE = .80;
		final Font font = button.getFont();
		button.setFont(font.deriveFont((float) (font.getSize() * SCALE)));
		final Insets insets = button.getMargin();
		button.setMargin(new Insets((int) (insets.top * SCALE), (int) (insets.left * SCALE),
				(int) (insets.bottom * SCALE), (int) (insets.right * SCALE)));
	}

	/** IDE Debug method */
	public static void main(final String[] args) {
		PathManagerUI.main(args);
	}
}
