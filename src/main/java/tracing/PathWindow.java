/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2017 Fiji developers.
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

package tracing;

import com.jidesoft.swing.SearchableBar;
import com.jidesoft.swing.TreeSearchable;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.swing.ButtonGroup;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import tracing.gui.ColorMenu;
import tracing.gui.GuiUtils;
import tracing.gui.SWCColor;

@SuppressWarnings("serial")
public class PathWindow extends JFrame implements PathAndFillListener,
	TreeSelectionListener
{

	protected HelpfulJTree tree;
	protected DefaultMutableTreeNode root;
	protected SimpleNeuriteTracer plugin;
	protected PathAndFillManager pathAndFillManager;
	private final GuiUtils guiUtils;

	private final JScrollPane scrollPane;
	private final JPopupMenu popup;
	private final JMenuBar menuBar;
	private final JMenu editMenu;
	private final JMenu swcTypeMenu;
	private final ButtonGroup swcTypeButtonGroup;
	private final ColorMenu colorMenu;
	private final JMenu fitMenu;
	private final JMenuItem renameMenuItem;
	private final JMenuItem fitVolumeMenuItem;
	private final JMenuItem fillOutMenuItem;
	private final JMenuItem exportAsSWCMenuItem;
	private final JMenuItem exportAsRoiMenuItem;
	private final JMenuItem downsampleMenuItem;

	public PathWindow(final PathAndFillManager pathAndFillManager,
		final SimpleNeuriteTracer plugin)
	{
		this(pathAndFillManager, plugin, 200, 60);
	}

	public PathWindow(final PathAndFillManager pathAndFillManager,
		final SimpleNeuriteTracer plugin, final int x, final int y)
	{

		super("Path Manager");
		guiUtils = new GuiUtils(this);
		this.pathAndFillManager = pathAndFillManager;
		this.plugin = plugin;

		setBounds(x, y, 300, 400);
		root = new DefaultMutableTreeNode("All Paths");
		tree = new HelpfulJTree(root);
		tree.setRootVisible(false);
		tree.addTreeSelectionListener(this);
		scrollPane = new JScrollPane();
		scrollPane.getViewport().add(tree);
		add(scrollPane, BorderLayout.CENTER);

		// Create all the menu items:
		final AListener listener = new AListener();
		menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		editMenu = new JMenu("Edit");
		JMenuItem jmi = new JMenuItem(AListener.DELETE_CMD);
		jmi.addActionListener(listener);
		editMenu.add(jmi);

		renameMenuItem = new JMenuItem(AListener.RENAME_CMD);
		renameMenuItem.addActionListener(listener);
		editMenu.add(renameMenuItem);

		jmi = new JMenuItem(AListener.MAKE_PRIMARY_CMD);
		jmi.addActionListener(listener);
		editMenu.add(jmi);
		menuBar.add(editMenu);

		swcTypeMenu = new JMenu("Type");
		swcTypeButtonGroup = new ButtonGroup();
		for (final int type : Path.getSWCtypes()) {
			final JRadioButtonMenuItem rbmi = new JRadioButtonMenuItem(Path
				.getSWCtypeName(type));
			swcTypeButtonGroup.add(rbmi);
			rbmi.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(final ActionEvent e) {
					final Set<Path> selectedPaths = getSelectedPaths();
					if (selectedPaths.isEmpty()) {
						noPathsMsg();
						rbmi.setSelected(false);
						return;
					}
					setSWCType(selectedPaths, type);
				}
			});
			swcTypeMenu.add(rbmi);
		}
		menuBar.add(swcTypeMenu);

		colorMenu = new ColorMenu(AListener.COLORS_MENU);
		colorMenu.addActionListener(listener);
		jmi = new JMenuItem(AListener.APPLY_SWC_COLORS_CMD);
		jmi.addActionListener(listener);
		colorMenu.add(jmi);
		jmi = new JMenuItem(AListener.REMOVE_COLOR_CMD);
		jmi.addActionListener(listener);
		colorMenu.add(jmi);
		menuBar.add(colorMenu);

		fitMenu = new JMenu("Fit");
		menuBar.add(fitMenu);
		fitVolumeMenuItem = new JMenuItem("Fit Volume");
		fitVolumeMenuItem.setToolTipText("Shift-click for detailed progress");
		fitVolumeMenuItem.addActionListener(listener);
		fitMenu.add(fitVolumeMenuItem);

		fillOutMenuItem = new JMenuItem("Fill Out");
		fillOutMenuItem.addActionListener(listener);
		fitMenu.add(fillOutMenuItem);

		final JMenu advanced = new JMenu("Advanced");
		menuBar.add(advanced);
		exportAsRoiMenuItem = new JMenuItem("Export as ROI");
		advanced.add(exportAsRoiMenuItem);
		exportAsRoiMenuItem.addActionListener(listener);
		exportAsSWCMenuItem = new JMenuItem("Export as SWC");
		advanced.add(exportAsSWCMenuItem);
		exportAsSWCMenuItem.addActionListener(listener);

		advanced.addSeparator();
		downsampleMenuItem = new JMenuItem("Downsample ...");
		downsampleMenuItem.addActionListener(listener);
		advanced.add(downsampleMenuItem);
		advanced.addSeparator();

		final JMenuItem toggleDnDMenuItem = new JCheckBoxMenuItem(
			"Allow Hierarchy Edits");
		toggleDnDMenuItem.setSelected(tree.getDragEnabled());
		toggleDnDMenuItem.addItemListener(new ItemListener() {

			// TODO: This is not functional: PathAndFillManager is not aware of any
			// of these
			@Override
			public void itemStateChanged(final ItemEvent e) {
				tree.setDragEnabled(toggleDnDMenuItem.isSelected() && confirmDnD());
				if (!tree.getDragEnabled()) displayTmpMsg(
					"Default behavior restored: Hierarchy is now locked.");
			}

		});
		advanced.add(toggleDnDMenuItem);

		popup = new JPopupMenu();
		JMenuItem pjmi = popup.add(AListener.DELETE_CMD);
		pjmi.addActionListener(listener);
		pjmi = popup.add(AListener.RENAME_CMD);
		pjmi.addActionListener(listener);
		pjmi = popup.add(AListener.MAKE_PRIMARY_CMD);
		pjmi.addActionListener(listener);
		popup.addSeparator();
		pjmi = popup.add(AListener.APPLY_SWC_COLORS_CMD);
		pjmi.addActionListener(listener);
		pjmi = popup.add(AListener.REMOVE_COLOR_CMD);
		pjmi.addActionListener(listener);
		popup.addSeparator();
		pjmi = popup.add(AListener.COLLAPSE_ALL_CMD);
		pjmi.addActionListener(listener);
		pjmi = popup.add(AListener.EXPAND_ALL_CMD);
		pjmi.addActionListener(listener);
		final JMenuItem jcbmi = new JCheckBoxMenuItem("Expand Selected Nodes");
		jcbmi.setSelected(tree.getExpandsSelectedPaths());
		jcbmi.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(final ItemEvent e) {
				tree.setExpandsSelectedPaths(jcbmi.isSelected());
				tree.setScrollsOnExpand(jcbmi.isSelected());
			}
		});
		popup.addSeparator();
		popup.add(jcbmi);

		tree.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(final MouseEvent me) {
				if (me.isPopupTrigger()) {
					showPopup(me);
				}
				else if (tree.getRowForLocation(me.getX(), me.getY()) == -1) {
					tree.clearSelection(); // Deselect when clicking on 'empty space'
				}
			}

		});

		add(bottomPanel(), BorderLayout.PAGE_END);
		pack();
	}

	public void setSWCType(final Set<Path> paths, final int swcType) {
		for (final Path p : paths)
			p.setSWCType(swcType);
		pathAndFillManager.resetListeners(null);
	}

	public void setPathsColor(final Set<Path> paths, final Color color) {
		for (final Path p : paths)
			p.setColor(color);
		refreshPluginViewers();
	}

	public void resetPathsColor(final Set<Path> paths,
		final boolean restoreSWCTypeColors)
	{
		for (final Path p : paths) {
			if (restoreSWCTypeColors) p.setColorBySWCtype();
			else p.setColor(null);
		}
		refreshPluginViewers();
	}

	private void refreshPluginViewers() {
		plugin.repaintAllPanes();
		plugin.update3DViewerContents();
	}

	public void deletePaths(final Set<Path> pathsToBeDeleted) {
		for (final Path p : pathsToBeDeleted) {
			p.disconnectFromAll();
			pathAndFillManager.deletePath(p);
		}
	}

//TODO: include children
	public Set<Path> getSelectedPaths() {
		return SwingSafeResult.getResult(new Callable<Set<Path>>() {

			@Override
			public Set<Path> call() {
				final HashSet<Path> result = new HashSet<>();
				final TreePath[] selectedPaths = tree.getSelectionPaths();
				if (selectedPaths == null || selectedPaths.length == 0) return result;
				for (int i = 0; i < selectedPaths.length; ++i) {
					final TreePath tp = selectedPaths[i];
					final DefaultMutableTreeNode node = (DefaultMutableTreeNode) (tp
						.getLastPathComponent());
					if (node != root) {
						final Path p = (Path) node.getUserObject();
						result.add(p);
					}
				}
				return result;
			}
		});
	}

	public void fitPaths(final List<PathFitter> pathsToFit) {

		final int numberOfPathsToFit = pathsToFit.size();

		new Thread(new Runnable() {

			@Override
			public void run() {

				final int preFittingState = plugin.getUIState();
				plugin.changeUIState(NeuriteTracerResultsDialog.FITTING_PATHS);

				try {

					final FittingProgress progress = new FittingProgress(
						numberOfPathsToFit);
					for (int i = 0; i < numberOfPathsToFit; ++i) {
						final PathFitter pf = pathsToFit.get(i);
						pf.setProgressCallback(i, progress);
					}
					final int processors = Runtime.getRuntime().availableProcessors();
					final ExecutorService es = Executors.newFixedThreadPool(processors);
					final List<Future<Path>> futures = es.invokeAll(pathsToFit);
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							try {
								for (final Future<Path> future : futures) {
									final Path result = future.get();
									pathAndFillManager.addPath(result);
								}
							}
							catch (final Exception e) {
								guiUtils.error("The following exception was thrown: " + e);
								e.printStackTrace();
								return;
							}
							pathAndFillManager.resetListeners(null);
							progress.done();
						}
					});
				}
				catch (final InterruptedException ie) {
					/*
					 * We never call interrupt on these threads, so this should
					 * never happen...
					 */
				}
				finally {
					plugin.changeUIState(preFittingState);
				}
			}
		}).start();
	}

	private void exportSelectedPaths(final Set<Path> paths) {

		// plugin.context.uiService.chooseFile(title, file,
		// FileWidget.DIRECTORY_STYLE);
		ArrayList<SWCPoint> swcPoints = null;
		try {
			swcPoints = pathAndFillManager.getSWCFor(paths);
		}
		catch (final SWCExportException see) {
			guiUtils.error("" + see.getMessage());
			return;
		}

		File saveFile = new File(plugin.getImagePlus().getShortTitle(), ".swc");
		saveFile = guiUtils.saveFile("Export SWC file ...", saveFile);

		if (saveFile == null) {
			return; // user pressed cancel
		}

		if (saveFile.exists() && !guiUtils.getConfirmation("The file " + saveFile
			.getAbsolutePath() + " already exists. Replace it?", "Override?")) return;

		plugin.statusService.showStatus("Exporting SWC data to " + saveFile
			.getAbsolutePath());

		try {
			final PrintWriter pw = new PrintWriter(new OutputStreamWriter(
				new FileOutputStream(saveFile), "UTF-8"));
			pathAndFillManager.flushSWCPoints(swcPoints, pw);
			pw.close();
		}
		catch (final IOException ioe) {
			guiUtils.error("Saving to " + saveFile.getAbsolutePath() + " failed");
			return;
		}
	}

	private void enableAllPathRelatedCommands(final boolean b) {
		editMenu.setEnabled(b);
		swcTypeMenu.setEnabled(b);
		colorMenu.setEnabled(b);
		fitMenu.setEnabled(b);
		renameMenuItem.setEnabled(b);
		exportAsSWCMenuItem.setEnabled(b);
		exportAsRoiMenuItem.setEnabled(b);
		downsampleMenuItem.setEnabled(b);
	}

	private void updateCmdsNoneSelected() {
		assert SwingUtilities.isEventDispatchThread();
		enableAllPathRelatedCommands(false);
		colorMenu.selectSWCColor((SWCColor) null);
		selectSWCTypeMenuEntry(-1);
	}

	private void updateCmdsOneSelected(final Path p) {
		assert SwingUtilities.isEventDispatchThread();
		enableAllPathRelatedCommands(true);
		if (p.getUseFitted()) fitVolumeMenuItem.setText("Un-fit Volume");
		else fitVolumeMenuItem.setText("Fit Volume");
		fitVolumeSetEnabled(true);
		colorMenu.selectSWCColor(new SWCColor(p.color, p.getSWCType()));
		selectSWCTypeMenuEntry(p.getSWCType());
	}

	private void updateCmdsManySelected(final Set<Path> selectedPaths) {
		assert SwingUtilities.isEventDispatchThread();
		enableAllPathRelatedCommands(true);
		renameMenuItem.setEnabled(false);
		if (allUsingFittedVersion(selectedPaths)) {
			fitVolumeMenuItem.setText("Un-fit Volumes");
		}
		else {
			fitVolumeMenuItem.setText("Fit Volumes");
			fitVolumeSetEnabled(true);
		}
		final Color c = selectedPaths.iterator().next().getColor();
		if (!allWithColor(selectedPaths, c)) {
			colorMenu.selectNone();
			return;
		}
		final int type = selectedPaths.iterator().next().getSWCType();
		if (allWithSWCType(selectedPaths, type)) {
			colorMenu.selectSWCColor(new SWCColor(c, type));
			selectSWCTypeMenuEntry(type);
		}
		else {
			colorMenu.selectColor(c);
			selectSWCTypeMenuEntry(-1);
		}
	}

	private void selectSWCTypeMenuEntry(final int index) {
		if (index < 0) {
			swcTypeButtonGroup.clearSelection();
			return;
		}
		int idx = 0;
		for (final Component component : swcTypeMenu.getMenuComponents()) {
			if (!(component instanceof JRadioButtonMenuItem)) continue;
			final JRadioButtonMenuItem mi = (JRadioButtonMenuItem) component;
			mi.setSelected(index == idx++);
		}
	}

	private void fitVolumeSetEnabled(final boolean b) {
		fitVolumeMenuItem.setEnabled(b && plugin
			.getUIState() != NeuriteTracerResultsDialog.IMAGE_CLOSED);
	}

	private boolean allWithSWCType(final Set<Path> paths, final int type) {
		if (paths == null || paths.isEmpty()) return false;
		for (final Path p : paths) {
			if (p.getSWCType() != type) return false;
		}
		return true;
	}

	private boolean allWithColor(final Set<Path> paths, final Color color) {
		if (paths == null || paths.isEmpty()) return false;
		for (final Path p : paths) {
			if (p.getColor() != color) return false;
		}
		return true;
	}

	public boolean allSelectedUsingFittedVersion() {
		return allUsingFittedVersion(getSelectedPaths());
	}

	public boolean allUsingFittedVersion(final Set<Path> paths) {
		for (final Path p : paths)
			if (!p.getUseFitted()) {
				return false;
			}
		return true;
	}

	@Override
	public void valueChanged(final TreeSelectionEvent e) {
		assert SwingUtilities.isEventDispatchThread();
		final Set<Path> selectedPaths = getSelectedPaths();
		if (selectedPaths.isEmpty()) {
			pathAndFillManager.setSelected(new Path[] {}, this);
			updateCmdsNoneSelected();
		}
		else {
			final Path paths[] = selectedPaths.toArray(new Path[] {});
			if (selectedPaths.isEmpty()) updateCmdsNoneSelected();
			else if (selectedPaths.size() == 1) {
				updateCmdsOneSelected(paths[0]);
			}
			else updateCmdsManySelected(selectedPaths);
			pathAndFillManager.setSelected(paths, this);
		}
		refreshPluginViewers();
	}

	private void displayTmpMsg(final String msg) {
		guiUtils.tempMsg(msg, true);
	}

	private JPanel bottomPanel() {
		final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEADING));
		panel.setBorder(new EmptyBorder(0, 0, 0, 0));
		final TreeSearchable searchable = new TreeSearchable(tree);
		searchable.setCaseSensitive(false);
		searchable.setFromStart(false);
		searchable.setWildcardEnabled(true);
		searchable.setRepeats(true);
		final SearchableBar sBar = new SearchableBar(searchable, true);
		sBar.setShowMatchCount(true);
		sBar.setHighlightAll(true); //TODO: update to 3.6.19 see bugfix https://github.com/jidesoft/jide-oss/commit/149bd6a53846a973dfbb589fffcc82abbc49610b
		sBar.setVisibleButtons(SearchableBar.SHOW_STATUS |
			SearchableBar.SHOW_HIGHLIGHTS);

		// Make everything more compact and user friendly
		boolean listenerAdded = false;
		for (final Component c : sBar.getComponents()) {
			((JComponent) c).setBorder(new EmptyBorder(0, 0, 0, 0));
			if (!listenerAdded && c instanceof JLabel && ((JLabel) c).getText()
				.contains("Find"))
			{
				((JLabel) c).setToolTipText("Double-click for Options");
				c.addMouseListener(new MouseAdapter() {

					@Override
					public void mouseClicked(final MouseEvent e) {
						if (e.getClickCount() > 1) searchHelpMsg();
					}
				});
				listenerAdded = true;
			}
		}
		sBar.setBorder(new EmptyBorder(0, 0, 0, 0));
		panel.add(sBar);
		return panel;
	}

	private boolean confirmDnD() {
		return guiUtils.getConfirmation(
			"Enabling this option will allow you to re-link paths through drag-and drop " +
				"of their respective nodes. Re-organizing paths in such way is useful to " +
				"proof-edit ill-relashionships but can also render the existing hierarchy " +
				"of paths meaningless. Please save your work before enabling this option. " +
				"Enable it now?", "Confirm Hierarchy Edits?");
	}

	private void noPathsMsg() {
		displayTmpMsg("No paths are currently selected.");
	}

	private void noSinglePathMsg() {
		displayTmpMsg("You must have exactly one path selected.");
	}

	private void searchHelpMsg() {
		final String key = GuiUtils.ctrlKey();
		final String msg = "<ol>" +
			"<li>Search is case-insensitive. Wildcards <b>?</b> and <b>*</b> are supported.</li>" +
			"<li>Select the <i>Highlight All</i> button or press " + key +
			"+A to select all the paths filtered by the search string</li>" +
			"<li>Press and hold " + key +
			" while pressing the up/down keys to select multiple filtered paths</li>" +
			"<li>Press the up/down keys to find the next/previous occurrence of the search string</li>" +
			"</ol></div></html>";
		guiUtils.msg(msg, "Searching Paths");
	}

	private void showPopup(final MouseEvent me) {
		assert SwingUtilities.isEventDispatchThread();
		popup.show(me.getComponent(), me.getX(), me.getY());
	}

	protected void getExpandedPaths(final HelpfulJTree tree,
		final TreeModel model, final MutableTreeNode node, final HashSet<Path> set)
	{
		assert SwingUtilities.isEventDispatchThread();
		final int count = model.getChildCount(node);
		for (int i = 0; i < count; i++) {
			final DefaultMutableTreeNode child = (DefaultMutableTreeNode) model
				.getChild(node, i);
			final Path p = (Path) child.getUserObject();
			if (tree.isExpanded(child.getPath())) {
				set.add(p);
			}
			if (!model.isLeaf(child)) getExpandedPaths(tree, model, child, set);
		}
	}

	protected void setExpandedPaths(final HelpfulJTree tree,
		final TreeModel model, final MutableTreeNode node, final HashSet<Path> set,
		final Path justAdded)
	{
		assert SwingUtilities.isEventDispatchThread();
		final int count = model.getChildCount(node);
		for (int i = 0; i < count; i++) {
			final DefaultMutableTreeNode child = (DefaultMutableTreeNode) model
				.getChild(node, i);
			final Path p = (Path) child.getUserObject();
			if (set.contains(p) || ((justAdded != null) && (justAdded == p))) {
				tree.setExpanded(child.getPath(), true);
			}
			if (!model.isLeaf(child)) setExpandedPaths(tree, model, child, set,
				justAdded);
		}

	}

	@Override
	public void setSelectedPaths(final HashSet<Path> selectedPaths,
		final Object source)
	{
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				if (source == this) return;
				final TreePath[] noTreePaths = {};
				tree.setSelectionPaths(noTreePaths);
				setSelectedPaths(tree, tree.getModel(), root, selectedPaths);
			}
		});
	}

	protected void setSelectedPaths(final HelpfulJTree tree,
		final TreeModel model, final MutableTreeNode node, final HashSet<Path> set)
	{
		assert SwingUtilities.isEventDispatchThread();
		final int count = model.getChildCount(node);
		for (int i = 0; i < count; i++) {
			final DefaultMutableTreeNode child = (DefaultMutableTreeNode) model
				.getChild(node, i);
			final Path p = (Path) child.getUserObject();
			if (set.contains(p)) {
				tree.setSelected(child.getPath());
			}
			if (!model.isLeaf(child)) setSelectedPaths(tree, model, child, set);
		}
	}

	@Override
	public void setPathList(final String[] pathList, final Path justAdded,
		final boolean expandAll)
	{

		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {

				// Save the selection state:

				final TreePath[] selectedBefore = tree.getSelectionPaths();
				final HashSet<Path> selectedPathsBefore = new HashSet<>();
				final HashSet<Path> expandedPathsBefore = new HashSet<>();

				if (selectedBefore != null) for (int i =
					0; i < selectedBefore.length; ++i)
				{
					final TreePath tp = selectedBefore[i];
					final DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) tp
						.getLastPathComponent();
					if (dmtn != root) {
						final Path p = (Path) dmtn.getUserObject();
						selectedPathsBefore.add(p);
					}
				}

				// Save the expanded state:
				getExpandedPaths(tree, tree.getModel(), root, expandedPathsBefore);

				/*
				 * Ignore the arguments and get the real path list from the
				 * PathAndFillManager:
				 */

				final DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode(
					"All Paths");
				final DefaultTreeModel model = new DefaultTreeModel(newRoot);
				// DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
				final Path[] primaryPaths = pathAndFillManager.getPathsStructured();
				for (int i = 0; i < primaryPaths.length; ++i) {
					final Path primaryPath = primaryPaths[i];
					// Add the primary path if it's not just a fitted version of
					// another:
					if (primaryPath.fittedVersionOf == null) addNode(newRoot, primaryPath,
						model);
				}
				root = newRoot;
				tree.setModel(model);

				model.reload();

				// Set back the expanded state:
				if (expandAll) {
					for (int i = 0; i < tree.getRowCount(); ++i)
						tree.expandRow(i);
				}
				else setExpandedPaths(tree, model, root, expandedPathsBefore,
					justAdded);

				setSelectedPaths(tree, model, root, selectedPathsBefore);
			}
		});
	}

	protected void addNode(final MutableTreeNode parent, final Path childPath,
		final DefaultTreeModel model)
	{
		assert SwingUtilities.isEventDispatchThread();
		final MutableTreeNode newNode = new DefaultMutableTreeNode(childPath);
		model.insertNodeInto(newNode, parent, parent.getChildCount());
		for (final Path p : childPath.children)
			addNode(newNode, p, model);
	}

	@Override
	public void setFillList(final String[] fillList) {

	}

	/** This class defines the JTree hosting traced paths */
	private static class HelpfulJTree extends JTree {

		public HelpfulJTree(final TreeNode root) {
			super(root);
			final DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {

				@Override
				public Component getTreeCellRendererComponent(final JTree tree,
					final Object value, final boolean selected, final boolean expanded,
					final boolean isLeaf, final int row, final boolean focused)
				{
					final Component c = super.getTreeCellRendererComponent(tree, value,
						selected, expanded, isLeaf, row, focused);
					final TreePath tp = tree.getPathForRow(row);
					if (tp == null) return c;
					final DefaultMutableTreeNode node = (DefaultMutableTreeNode) (tp
						.getLastPathComponent());
					if (node == null || node == root) return c;
					final Path p = (Path) node.getUserObject();
					final Color color = p.color;
					if (color == null) return c;
					if (isLeaf) setIcon(new NodeIcon(NodeIcon.EMPTY, color));
					else if (!expanded) setIcon(new NodeIcon(NodeIcon.PLUS, color));
					else setIcon(new NodeIcon(NodeIcon.MINUS, color));
					return c;
				}
			};
			renderer.setClosedIcon(new NodeIcon(NodeIcon.PLUS));
			renderer.setOpenIcon(new NodeIcon(NodeIcon.MINUS));
			renderer.setLeafIcon(new NodeIcon(NodeIcon.EMPTY));
			setCellRenderer(renderer);
			getSelectionModel().setSelectionMode(
				TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
			// setDragEnabled(true);
			setDropMode(DropMode.ON_OR_INSERT);
			setTransferHandler(new TreeTransferHandler());
		}

		public boolean isExpanded(final Object[] path) {
			assert SwingUtilities.isEventDispatchThread();
			final TreePath tp = new TreePath(path);
			return isExpanded(tp);
		}

		public void setExpanded(final Object[] path, final boolean expanded) {
			assert SwingUtilities.isEventDispatchThread();
			final TreePath tp = new TreePath(path);
			setExpandedState(tp, expanded);
		}

		public void setSelected(final Object[] path) {
			assert SwingUtilities.isEventDispatchThread();
			final TreePath tp = new TreePath(path);
			setSelectionPath(tp);
		}

	}

	/**
	 * This class generates the JTree node icons. Heavily inspired by
	 * http://stackoverflow.com/a/7984734
	 */
	private static class NodeIcon implements Icon {

		private static final int SIZE = 9;
		private static final char PLUS = '+';
		private static final char MINUS = '-';
		private static final char EMPTY = ' ';
		private final char type;
		private final Color color;

		private NodeIcon(final char type) {
			this.type = type;
			this.color = UIManager.getColor("Tree.background");
		}

		private NodeIcon(final char type, final Color color) {
			this.type = type;
			this.color = color;
		}

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x,
			final int y)
		{
			g.setColor(color);
			g.fillRect(x, y, SIZE - 1, SIZE - 1);
			g.setColor(color.darker());
			g.drawRect(x, y, SIZE - 1, SIZE - 1);
			if (type == EMPTY) return;
			g.setColor(UIManager.getColor("Tree.foreground"));
			g.drawLine(x + 2, y + SIZE / 2, x + SIZE - 3, y + SIZE / 2);
			if (type == PLUS) {
				g.drawLine(x + SIZE / 2, y + 2, x + SIZE / 2, y + SIZE - 3);
			}
		}

		@Override
		public int getIconWidth() {
			return SIZE;
		}

		@Override
		public int getIconHeight() {
			return SIZE;
		}

	}

	private static class TreeTransferHandler extends TransferHandler {

		private static final long serialVersionUID = 1L;
		DataFlavor nodesFlavor;
		DataFlavor[] flavors = new DataFlavor[1];
		DefaultMutableTreeNode[] nodesToRemove;

		public TreeTransferHandler() {
			try {
				final String mimeType = DataFlavor.javaJVMLocalObjectMimeType +
					";class=\"" + javax.swing.tree.DefaultMutableTreeNode[].class
						.getName() + "\"";
				nodesFlavor = new DataFlavor(mimeType);
				flavors[0] = nodesFlavor;
			}
			catch (final ClassNotFoundException e) {
				System.out.println("ClassNotFound: " + e.getMessage());
			}
		}

		@Override
		public boolean canImport(final TransferHandler.TransferSupport support) {
			if (!support.isDrop()) {
				return false;
			}
			support.setShowDropLocation(true);
			if (!support.isDataFlavorSupported(nodesFlavor)) {
				return false;
			}
			// Do not allow a drop on the drag source selections.
			final JTree.DropLocation dl = (JTree.DropLocation) support
				.getDropLocation();
			final JTree tree = (JTree) support.getComponent();
			final int dropRow = tree.getRowForPath(dl.getPath());
			final int[] selRows = tree.getSelectionRows();
			for (int i = 0; i < selRows.length; i++) {
				if (selRows[i] == dropRow) {
					return false;
				}
			}
			// Do not allow MOVE-action drops if a non-leaf node is
			// selected unless all of its children are also selected.
			final int action = support.getDropAction();
			if (action == MOVE) {
				return haveCompleteNode(tree);
			}
			// Do not allow a non-leaf node to be copied to a level
			// which is less than its source level.
			final TreePath dest = dl.getPath();
			final DefaultMutableTreeNode target = (DefaultMutableTreeNode) dest
				.getLastPathComponent();
			final TreePath path = tree.getPathForRow(selRows[0]);
			final DefaultMutableTreeNode firstNode = (DefaultMutableTreeNode) path
				.getLastPathComponent();
			if (firstNode.getChildCount() > 0 && target.getLevel() < firstNode
				.getLevel())
			{
				return false;
			}
			return true;
		}

		private boolean haveCompleteNode(final JTree tree) {
			final int[] selRows = tree.getSelectionRows();
			TreePath path = tree.getPathForRow(selRows[0]);
			final DefaultMutableTreeNode first = (DefaultMutableTreeNode) path
				.getLastPathComponent();
			final int childCount = first.getChildCount();
			// first has children and no children are selected.
			if (childCount > 0 && selRows.length == 1) return false;
			// first may have children.
			for (int i = 1; i < selRows.length; i++) {
				path = tree.getPathForRow(selRows[i]);
				final DefaultMutableTreeNode next = (DefaultMutableTreeNode) path
					.getLastPathComponent();
				if (first.isNodeChild(next)) {
					// Found a child of first.
					if (childCount > selRows.length - 1) {
						// Not all children of first are selected.
						return false;
					}
				}
			}
			return true;
		}

		@Override
		protected Transferable createTransferable(final JComponent c) {
			final JTree tree = (JTree) c;
			final TreePath[] paths = tree.getSelectionPaths();
			if (paths != null) {
				// Make up a node array of copies for transfer and
				// another for/of the nodes that will be removed in
				// exportDone after a successful drop.
				final List<DefaultMutableTreeNode> copies = new ArrayList<>();
				final List<DefaultMutableTreeNode> toRemove = new ArrayList<>();
				final DefaultMutableTreeNode node = (DefaultMutableTreeNode) paths[0]
					.getLastPathComponent();
				final DefaultMutableTreeNode copy = copy(node);
				copies.add(copy);
				toRemove.add(node);
				for (int i = 1; i < paths.length; i++) {
					final DefaultMutableTreeNode next = (DefaultMutableTreeNode) paths[i]
						.getLastPathComponent();
					// Do not allow higher level nodes to be added to list.
					if (next.getLevel() < node.getLevel()) {
						break;
					}
					else if (next.getLevel() > node.getLevel()) { // child node
						copy.add(copy(next));
						// node already contains child
					}
					else { // sibling
						copies.add(copy(next));
						toRemove.add(next);
					}
				}
				final DefaultMutableTreeNode[] nodes = copies.toArray(
					new DefaultMutableTreeNode[copies.size()]);
				nodesToRemove = toRemove.toArray(new DefaultMutableTreeNode[toRemove
					.size()]);
				return new NodesTransferable(nodes);
			}
			return null;
		}

		/** Defensive copy used in createTransferable. */
		private DefaultMutableTreeNode copy(final TreeNode node) {
			final DefaultMutableTreeNode n = (DefaultMutableTreeNode) node;
			return (DefaultMutableTreeNode) n.clone();
		}

		@Override
		protected void exportDone(final JComponent source, final Transferable data,
			final int action)
		{
			if ((action & MOVE) == MOVE) {
				final JTree tree = (JTree) source;
				final DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
				// Remove nodes saved in nodesToRemove in createTransferable.
				for (int i = 0; i < nodesToRemove.length; i++) {
					model.removeNodeFromParent(nodesToRemove[i]);
				}
			}
		}

		@Override
		public int getSourceActions(final JComponent c) {
			return COPY_OR_MOVE;
		}

		@Override
		public boolean importData(final TransferHandler.TransferSupport support) {
			if (!canImport(support)) {
				return false;
			}
			// Extract transfer data.
			DefaultMutableTreeNode[] nodes = null;
			try {
				final Transferable t = support.getTransferable();
				nodes = (DefaultMutableTreeNode[]) t.getTransferData(nodesFlavor);
			}
			catch (final UnsupportedFlavorException ufe) {
				System.out.println("UnsupportedFlavor: " + ufe.getMessage());
			}
			catch (final java.io.IOException ioe) {
				System.out.println("I/O error: " + ioe.getMessage());
			}
			// Get drop location info.
			final JTree.DropLocation dl = (JTree.DropLocation) support
				.getDropLocation();
			final int childIndex = dl.getChildIndex();
			final TreePath dest = dl.getPath();
			final DefaultMutableTreeNode parent = (DefaultMutableTreeNode) dest
				.getLastPathComponent();
			final JTree tree = (JTree) support.getComponent();
			final DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
			// Configure for drop mode.
			int index = childIndex; // DropMode.INSERT
			if (childIndex == -1) { // DropMode.ON
				index = parent.getChildCount();
			}
			// Add data to model.
			for (int i = 0; i < nodes.length; i++) {
				model.insertNodeInto(nodes[i], parent, index++);
			}
			return true;
		}

		@Override
		public String toString() {
			return getClass().getName();
		}

		public class NodesTransferable implements Transferable {

			DefaultMutableTreeNode[] nodes;

			public NodesTransferable(final DefaultMutableTreeNode[] nodes) {
				this.nodes = nodes;
			}

			@Override
			public Object getTransferData(final DataFlavor flavor)
				throws UnsupportedFlavorException
			{
				if (!isDataFlavorSupported(flavor))
					throw new UnsupportedFlavorException(flavor);
				return nodes;
			}

			@Override
			public DataFlavor[] getTransferDataFlavors() {
				return flavors;
			}

			@Override
			public boolean isDataFlavorSupported(final DataFlavor flavor) {
				return nodesFlavor.equals(flavor);
			}
		}
	}

	/**
	 * This class implements implements ActionListeners for most of PathWindow's
	 * MenuItems.
	 */
	private class AListener implements ActionListener {

		public static final String COLORS_MENU = "Colors";
		private final static String EXPAND_ALL_CMD = "Expand All";
		private final static String COLLAPSE_ALL_CMD = "Collapse All";
		private final static String DELETE_CMD = "Delete";
		private final static String RENAME_CMD = "Rename";
		private final static String MAKE_PRIMARY_CMD = "Make Primary";
		private final static String APPLY_SWC_COLORS_CMD = "Apply SWC-Type Colors";
		private final static String REMOVE_COLOR_CMD = "Remove Color Tags";

		@Override
		public void actionPerformed(final ActionEvent e) {
			assert SwingUtilities.isEventDispatchThread();

			if (e.getActionCommand().equals(EXPAND_ALL_CMD)) {
				for (int i = 0; i < tree.getRowCount(); i++)
					tree.expandRow(i);
				return;
			}
			else if (e.getActionCommand().equals(COLLAPSE_ALL_CMD)) {
				for (int i = 0; i < tree.getRowCount(); i++)
					tree.collapseRow(i);
				return;
			}

			final Set<Path> selectedPaths = getSelectedPaths();
			if (selectedPaths.isEmpty()) {
				noPathsMsg();
				return;
			}
			final Object source = e.getSource();
			final int n = selectedPaths.size();

			if (e.getActionCommand().equals(DELETE_CMD)) {

				final String msg = (n == 1) ? "Delete \"" + selectedPaths.iterator()
					.next() + "\"?" : "Delete the selected " + n + " paths?";
				if (guiUtils.getConfirmation(msg, "Confirm Deletion?")) deletePaths(
					selectedPaths);
				return;

			}
			else if (e.getActionCommand().equals(RENAME_CMD)) {
				if (selectedPaths.size() != 1) {
					displayTmpMsg("You must have exactly one path selected.");
					return;
				}
				final Path[] singlePath = selectedPaths.toArray(new Path[] {});
				final Path p = singlePath[0];
				final String s = guiUtils.getString("Rename this path to:",
					"Rename Path", p.getName());
				if (s == null) return;
				if (s.length() == 0) {
					displayTmpMsg("The new name cannot be empty.");
					return;
				}
				synchronized (pathAndFillManager) {
					if (pathAndFillManager.getPathFromName(s, false) != null) {
						displayTmpMsg("There is already a path named:\n('" + s + "')");
						return;
					}
					// Otherwise this is OK, change the name:
					p.setName(s);
					pathAndFillManager.resetListeners(null);
				}
				return;

			}
			else if (e.getActionCommand().equals(MAKE_PRIMARY_CMD)) {

				if (selectedPaths.size() != 1) {
					noSinglePathMsg();
					return;
				}
				final Path[] singlePath = selectedPaths.toArray(new Path[] {});
				final Path p = singlePath[0];
				final HashSet<Path> pathsExplored = new HashSet<>();
				p.setPrimary(true);
				pathsExplored.add(p);
				p.unsetPrimaryForConnected(pathsExplored);
				pathAndFillManager.resetListeners(null);
				return;

			}
			else if (e.getActionCommand().equals(APPLY_SWC_COLORS_CMD)) {

				if (n == 1 || (n > 1 && guiUtils.getConfirmation(
					"Apply SWC-type colors to selected paths?", "Confirm?")))
					resetPathsColor(getSelectedPaths(), true);

			}
			else if (e.getActionCommand().equals(REMOVE_COLOR_CMD)) {

				resetPathsColor(getSelectedPaths(), false);

			}
			else if (e.getActionCommand().equals(COLORS_MENU)) {

				final SWCColor swcColor = colorMenu.getSelectedSWCColor();
				if (swcColor.isTypeDefined()) setSWCType(selectedPaths, swcColor
					.type());
				setPathsColor(selectedPaths, swcColor.color());
				return;

			}
			else if (source.equals(fitVolumeMenuItem)) {

				final boolean showDetailedFittingResults = (e.getModifiers() &
					ActionEvent.SHIFT_MASK) > 0;

				final ArrayList<PathFitter> pathsToFit = new ArrayList<>();
				final boolean allAlreadyFitted = allUsingFittedVersion(selectedPaths);
				for (final Path p : selectedPaths) {
					if (allAlreadyFitted) {
						p.setUseFitted(false, plugin);
					}
					else {
						if (p.getUseFitted()) {
							continue;
						}
						if (p.fitted == null) {
							// There's not already a fitted version:
							final PathFitter pathFitter = new PathFitter(plugin, p,
								showDetailedFittingResults);
							pathsToFit.add(pathFitter);
						}
						else {
							// Just use the existing fitted version:
							p.setUseFitted(true, plugin);
						}
					}
				}
				pathAndFillManager.resetListeners(null);

				if (pathsToFit.size() > 0) fitPaths(pathsToFit);
				return;

			}
			else if (source.equals(fillOutMenuItem)) {

				plugin.startFillingPaths(selectedPaths);
				return;

			}
			else if (source == exportAsRoiMenuItem) {

				guiUtils.error("To be implemented"); // TODO: implement
				return;

			}
			else if (source == exportAsSWCMenuItem) {

				exportSelectedPaths(selectedPaths);
				return;

			}
			else if (source.equals(downsampleMenuItem)) {

				final Double userMaxDeviation = guiUtils.getDouble(
					"Maximum permitted distance from previous points:\n" +
						"(WARNING: this destructive operation cannot be undone)",
					"Downsampling (" + n + " path(s)) ", plugin.x_spacing);
				if (userMaxDeviation == null) return; // user pressed cancel

				final double maxDeviation = userMaxDeviation.doubleValue();
				if (Double.isNaN(maxDeviation) || maxDeviation <= 0) {
					guiUtils.error(
						"The maximum permitted distance must be a postive number");
					return;
				}
				for (final Path p : selectedPaths) { // TODO: Move to
																							// pathAndFillManager
					Path pathToUse = p;
					if (p.getUseFitted()) {
						pathToUse = p.fitted;
					}
					pathToUse.downsample(maxDeviation);
				}
				// Make sure that the 3D viewer and the stacks are redrawn:
				refreshPluginViewers();

			}
			else {
				SNT.debug("Unexpectedly got an event from an unknown source: " +
					source);
				return;
			}
		}
	}

}
