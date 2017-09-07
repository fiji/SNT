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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
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

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
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

import ij.IJ;
import ij.gui.ColorChooser;
import ij.gui.GenericDialog;
import ij.io.SaveDialog;

@SuppressWarnings("serial")
public class PathWindow extends JFrame implements PathAndFillListener, TreeSelectionListener, ActionListener {

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

	public Set<Path> getSelectedPaths() {

		return SwingSafeResult.getResult(new Callable<Set<Path>>() {
			@Override
			public Set<Path> call() {
				final HashSet<Path> result = new HashSet<>();
				final TreePath[] selectedPaths = tree.getSelectionPaths();
				if (selectedPaths == null || selectedPaths.length == 0) {
					return result;
				}
				for (int i = 0; i < selectedPaths.length; ++i) {
					final TreePath tp = selectedPaths[i];
					final DefaultMutableTreeNode node = (DefaultMutableTreeNode) (tp.getLastPathComponent());
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

					final FittingProgress progress = new FittingProgress(numberOfPathsToFit);
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
							} catch (final Exception e) {
								SNT.error("The following exception was thrown: " + e);
								e.printStackTrace();
								return;
							}
							pathAndFillManager.resetListeners(null);
							progress.done();
						}
					});
				} catch (final InterruptedException ie) {
					/*
					 * We never call interrupt on these threads, so this should
					 * never happen...
					 */
				} finally {
					plugin.changeUIState(preFittingState);
				}
			}
		}).start();
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		assert SwingUtilities.isEventDispatchThread();
		final Object source = e.getSource();
		final Set<Path> selectedPaths = getSelectedPaths();
		if (source == deleteButton || source == deleteMenuItem) {
			if (selectedPaths.isEmpty()) {
				SNT.error("No paths were selected for deletion");
				return;
			}
			final int n = selectedPaths.size();
			String message = "Are you sure you want to delete ";
			if (n == 1) {
				message += "the path \"" + selectedPaths.iterator().next() + "\"";
			} else {
				message += "these " + n + " paths?";
			}
			message += "?";
			if (!IJ.showMessageWithCancel("Delete paths...", message))
				return;
			for (final Path p : selectedPaths) {
				p.disconnectFromAll();
				pathAndFillManager.deletePath(p);
			}
		} else if (source == makePrimaryButton || source == makePrimaryMenuItem) {
			if (selectedPaths.size() != 1) {
				SNT.error("You must have exactly one path selected");
				return;
			}
			final Path[] singlePath = selectedPaths.toArray(new Path[] {});
			final Path p = singlePath[0];
			final HashSet<Path> pathsExplored = new HashSet<>();
			p.setPrimary(true);
			pathsExplored.add(p);
			p.unsetPrimaryForConnected(pathsExplored);
			pathAndFillManager.resetListeners(null);
		} else if (source == exportAsSWCButton || source == exportAsSWCMenuItem) {
			ArrayList<SWCPoint> swcPoints = null;
			try {
				swcPoints = pathAndFillManager.getSWCFor(selectedPaths);
			} catch (final SWCExportException see) {
				SNT.error("" + see.getMessage());
				return;
			}

			final SaveDialog sd = new SaveDialog("Export SWC file ...", plugin.getImagePlus().getShortTitle(), ".swc");

			if (sd.getFileName() == null) {
				return;
			}

			final File saveFile = new File(sd.getDirectory(), sd.getFileName());
			if (saveFile.exists()) {
				if (!IJ.showMessageWithCancel("Export data...",
						"The file " + saveFile.getAbsolutePath() + " already exists.\n" + "Do you want to replace it?"))
					return;
			}

			IJ.showStatus("Exporting SWC data to " + saveFile.getAbsolutePath());

			try {
				final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(saveFile), "UTF-8"));
				pathAndFillManager.flushSWCPoints(swcPoints, pw);
				pw.close();
			} catch (final IOException ioe) {
				SNT.error("Saving to " + saveFile.getAbsolutePath() + " failed");
				return;
			}

		} else if (source == fillOutButton || source == fillOutMenuItem) {
			if (selectedPaths.size() < 1) {
				SNT.error("You must have one or more paths in the list selected");
				return;
			}
			plugin.startFillingPaths(selectedPaths);
		} else if (source == fitVolumeButton || source == fitVolumeMenuItem) {
			if (selectedPaths.size() < 1) {
				SNT.error("You must have one or more paths in the list selected");
				return;
			}
			final boolean showDetailedFittingResults = (e.getModifiers() & ActionEvent.SHIFT_MASK) > 0;

			final ArrayList<PathFitter> pathsToFit = new ArrayList<>();
			final boolean allAlreadyFitted = allUsingFittedVersion(selectedPaths);
			for (final Path p : selectedPaths) {
				if (allAlreadyFitted) {
					p.setUseFitted(false, plugin);
				} else {
					if (p.getUseFitted()) {
						continue;
					}
					if (p.fitted == null) {
						// There's not already a fitted version:
						final PathFitter pathFitter = new PathFitter(plugin, p, showDetailedFittingResults);
						pathsToFit.add(pathFitter);
					} else {
						// Just use the existing fitted version:
						p.setUseFitted(true, plugin);
					}
				}
			}
			pathAndFillManager.resetListeners(null);

			if (pathsToFit.size() > 0)
				fitPaths(pathsToFit);

		} else if (source == renameButton || source == renameMenuItem) {
			if (selectedPaths.size() != 1) {
				SNT.error("You must have exactly one path selected");
				return;
			}
			final Path[] singlePath = selectedPaths.toArray(new Path[] {});
			final Path p = singlePath[0];
			// Pop up the rename dialog:
			final String s = (String) JOptionPane.showInputDialog(this, "Rename this path to:", "Rename Path",
					JOptionPane.PLAIN_MESSAGE, null, null, p.getName());

			if (s == null)
				return;
			if (s.length() == 0) {
				SNT.error("The new name cannot be empty");
				return;
			}
			synchronized (pathAndFillManager) {
				if (pathAndFillManager.getPathFromName(s, false) != null) {
					SNT.error("There is already a path with that name ('" + s + "')");
					return;
				}
				// Otherwise this is OK, change the name:
				p.setName(s);
				pathAndFillManager.resetListeners(null);
			}

		} else if (source == downsampleMenuItem) {
			final GenericDialog gd = new GenericDialog("Choose level of downsampling");
			gd.addNumericField("Maximum permitted distance from previous points", plugin.x_spacing, 3);
			gd.addMessage("WARNING: this destructive operation cannot be undone");
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			final double maximumDeviation = gd.getNextNumber();
			if (Double.isNaN(maximumDeviation) || maximumDeviation <= 0) {
				SNT.error("The maximum permitted distance must be a postive number");
				return;
			}
			for (final Path p : selectedPaths) {
				Path pathToUse = p;
				if (p.getUseFitted()) {
					pathToUse = p.fitted;
				}
				pathToUse.downsample(maximumDeviation);
			}
			// Make sure that the 3D viewer and the stacks are redrawn:
			pathAndFillManager.update3DViewerContents();
			plugin.repaintAllPanes();

		} else {
			// Check if the source was from one of the SWC menu
			// items:
			int swcType = -1;
			int i = 0;
			for (final JMenuItem menuItem : swcTypeMenuItems) {
				if (source == menuItem) {
					swcType = i;
					break;
				}
				++i;
			}

			if (swcType >= 0) {
				for (final Path p : selectedPaths)
					p.setSWCType(swcType);
				pathAndFillManager.resetListeners(null);
			} else {

				SNT.error("Unexpectedly got an event from an unknown source");
				return;
			}
		}
	}

	protected void renameSetEnabled(final boolean enabled) {
		assert SwingUtilities.isEventDispatchThread();
		renameButton.setEnabled(enabled);
		renameMenuItem.setEnabled(enabled);
	}

	protected void fitVolumeSetEnabled(final boolean enabled) {
		assert SwingUtilities.isEventDispatchThread();
		fitVolumeButton.setEnabled(enabled);
		fitVolumeMenuItem.setEnabled(enabled);
	}

	protected void fillOutSetEnabled(final boolean enabled) {
		assert SwingUtilities.isEventDispatchThread();
		fillOutButton.setEnabled(enabled);
		fillOutMenuItem.setEnabled(enabled);
	}

	protected void makePrimarySetEnabled(final boolean enabled) {
		assert SwingUtilities.isEventDispatchThread();
		makePrimaryButton.setEnabled(enabled);
		makePrimaryMenuItem.setEnabled(enabled);
	}

	protected void deleteSetEnabled(final boolean enabled) {
		assert SwingUtilities.isEventDispatchThread();
		deleteButton.setEnabled(enabled);
		deleteMenuItem.setEnabled(enabled);
	}

	protected void exportAsSWCSetEnabled(final boolean enabled) {
		assert SwingUtilities.isEventDispatchThread();
		exportAsSWCButton.setEnabled(enabled);
		exportAsSWCMenuItem.setEnabled(enabled);
	}

	protected void fitVolumeSetText(final String s) {
		assert SwingUtilities.isEventDispatchThread();
		fitVolumeButton.setText(s);
		fitVolumeMenuItem.setText(s);
	}

	protected void updateButtonsNoneSelected() {
		assert SwingUtilities.isEventDispatchThread();
		renameSetEnabled(false);
		fitVolumeSetText("Fit Volume");
		fitVolumeSetEnabled(false);
		fillOutSetEnabled(false);
		makePrimarySetEnabled(false);
		deleteSetEnabled(false);
		exportAsSWCSetEnabled(false);
		swcTypeMenu.setEnabled(false);
		colorMenu.setEnabled(false);
	}

	protected void updateButtonsOneSelected(final Path p) {
		assert SwingUtilities.isEventDispatchThread();
		renameSetEnabled(true);
		if (p.getUseFitted())
			fitVolumeSetText("Un-fit Volume");
		else
			fitVolumeSetText("Fit Volume");
		fitVolumeSetEnabled(true);
		fillOutSetEnabled(plugin.getUIState() != NeuriteTracerResultsDialog.IMAGE_CLOSED);
		makePrimarySetEnabled(true);
		deleteSetEnabled(true);
		exportAsSWCSetEnabled(true);
		swcTypeMenu.setEnabled(true);
		colorMenu.setEnabled(true);
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

	protected void updateButtonsManySelected() {
		assert SwingUtilities.isEventDispatchThread();
		renameSetEnabled(false);
		{
			if (allSelectedUsingFittedVersion())
				fitVolumeSetText("Un-fit Volumes");
			else
				fitVolumeSetText("Fit Volumes");
		}
		fitVolumeSetEnabled(true);
		fillOutSetEnabled(plugin.getUIState() != NeuriteTracerResultsDialog.IMAGE_CLOSED);
		makePrimarySetEnabled(false);
		deleteSetEnabled(true);
		exportAsSWCSetEnabled(true);
		swcTypeMenu.setEnabled(true);
		colorMenu.setEnabled(true);
	}

	@Override
	public void valueChanged(final TreeSelectionEvent e) {
		assert SwingUtilities.isEventDispatchThread();
		final Set<Path> selectedPaths = getSelectedPaths();
		if (selectedPaths.isEmpty()) {
			pathAndFillManager.setSelected(new Path[] {}, this);
			updateButtonsNoneSelected();
		} else {
			final Path paths[] = selectedPaths.toArray(new Path[] {});
			if (selectedPaths.isEmpty())
				updateButtonsNoneSelected();
			else if (selectedPaths.size() == 1) {
				updateButtonsOneSelected(paths[0]);
			} else
				updateButtonsManySelected();
			pathAndFillManager.setSelected(paths, this);
		}
		plugin.update3DViewerContents();
	}

	public static class PathTreeNode extends DefaultMutableTreeNode {
	}

	protected JScrollPane scrollPane;

	protected HelpfulJTree tree;
	protected DefaultMutableTreeNode root;

	protected JPopupMenu popup;
	protected JMenu swcTypeMenu;
	protected JMenu colorMenu;
	protected JPanel buttonPanel;

	protected JButton renameButton;
	protected JButton fitVolumeButton;
	protected JButton fillOutButton;
	protected JButton makePrimaryButton;
	protected JButton deleteButton;
	protected JButton exportAsSWCButton;

	protected JMenuItem renameMenuItem;
	protected JMenuItem fitVolumeMenuItem;
	protected JMenuItem fillOutMenuItem;
	protected JMenuItem makePrimaryMenuItem;
	protected JMenuItem deleteMenuItem;
	protected JMenuItem exportAsSWCMenuItem;
	protected JMenuItem downsampleMenuItem;

	protected ArrayList<JMenuItem> swcTypeMenuItems = new ArrayList<>();

	protected SimpleNeuriteTracer plugin;
	protected PathAndFillManager pathAndFillManager;

	public PathWindow(final PathAndFillManager pathAndFillManager, final SimpleNeuriteTracer plugin) {
		this(pathAndFillManager, plugin, 200, 60);
	}

	public PathWindow(final PathAndFillManager pathAndFillManager, final SimpleNeuriteTracer plugin, final int x,
			final int y) {
		super("All Paths");
		assert SwingUtilities.isEventDispatchThread();

		new ClarifyingKeyListener().addKeyAndContainerListenerRecursively(this);

		this.pathAndFillManager = pathAndFillManager;
		this.plugin = plugin;

		setBounds(x, y, 620, 240);
		root = new DefaultMutableTreeNode("All Paths");
		tree = new HelpfulJTree(root);
		// tree.setRootVisible(false);
		tree.addTreeSelectionListener(this);
		scrollPane = new JScrollPane();
		scrollPane.getViewport().add(tree);
		add(scrollPane, BorderLayout.CENTER);

		buttonPanel = new JPanel();
		buttonPanel.setBorder(new EmptyBorder(0, 0, 0, 0));

		add(buttonPanel, BorderLayout.PAGE_END);

		// Create all the menu items:

		popup = new JPopupMenu();

		renameMenuItem = new JMenuItem("Rename");
		fitVolumeMenuItem = new JMenuItem("Fit Volume");
		fillOutMenuItem = new JMenuItem("Fill Out");
		makePrimaryMenuItem = new JMenuItem("Make Primary");
		deleteMenuItem = new JMenuItem("Delete");
		exportAsSWCMenuItem = new JMenuItem("Export as SWC");
		downsampleMenuItem = new JMenuItem("Downsample ...");

		popup.add(renameMenuItem);
		popup.add(fitVolumeMenuItem);
		popup.add(fillOutMenuItem);
		popup.add(makePrimaryMenuItem);
		popup.add(deleteMenuItem);
		popup.add(exportAsSWCMenuItem);
		popup.add(downsampleMenuItem);

		renameMenuItem.addActionListener(this);
		fitVolumeMenuItem.addActionListener(this);
		fillOutMenuItem.addActionListener(this);
		makePrimaryMenuItem.addActionListener(this);
		deleteMenuItem.addActionListener(this);
		exportAsSWCMenuItem.addActionListener(this);
		downsampleMenuItem.addActionListener(this);

		// Now also add the SWC types submenu:
		swcTypeMenu = new JMenu("Set SWC Type");
		for (final String s : Path.swcTypeNames) {
			final JMenuItem jmi = new JMenuItem(s);
			jmi.addActionListener(this);
			swcTypeMenu.add(jmi);
			swcTypeMenuItems.add(jmi);
		}
		popup.addSeparator();
		popup.add(swcTypeMenu);
		colorMenu = colorPopupMenu();
		popup.add(colorMenu);

		// Create all the menu items:
		renameButton = SNT.smallButton("Rename");
		fitVolumeButton = SNT.smallButton("Fit Volume");
		fillOutButton = SNT.smallButton("Fill Out");
		makePrimaryButton = SNT.smallButton("Make Primary");
		deleteButton = SNT.smallButton("Delete");
		exportAsSWCButton = SNT.smallButton("Export as SWC");

		buttonPanel.add(renameButton);
		buttonPanel.add(fitVolumeButton);
		buttonPanel.add(fillOutButton);
		buttonPanel.add(makePrimaryButton);
		buttonPanel.add(deleteButton);
		buttonPanel.add(exportAsSWCButton);

		renameButton.addActionListener(this);
		fitVolumeButton.addActionListener(this);
		fillOutButton.addActionListener(this);
		makePrimaryButton.addActionListener(this);
		deleteButton.addActionListener(this);
		exportAsSWCButton.addActionListener(this);

		final MouseListener ml = new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent me) {
				maybeShowPopup(me);
			}

			@Override
			public void mouseReleased(final MouseEvent me) {
				maybeShowPopup(me);
			}

			protected void maybeShowPopup(final MouseEvent me) {
				if (me.isPopupTrigger())
					showPopup(me);
			}
		};
		tree.addMouseListener(ml);
	}

	private JMenu colorPopupMenu() {
		final JMenu menu = new JMenu("Color");
		JMenuItem jmi;
		final String[] colors = ij.plugin.Colors.getColors();
		for (final String color : colors) {
			jmi = new JMenuItem(color);
			jmi.setActionCommand(color);
			menu.add(jmi);
			jmi.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					applyColortoSelectPaths(ij.plugin.Colors.getColor(e.getActionCommand(),
							SimpleNeuriteTracer.DEFAULT_DESELECTED_COLOR));
				}
			});
		}
		menu.addSeparator();
		jmi = new JMenuItem("Specify...");
		jmi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final ColorChooser chooser = new ColorChooser("Specify color",
						SimpleNeuriteTracer.DEFAULT_DESELECTED_COLOR, false);
				applyColortoSelectPaths(chooser.getColor());
			}
		});
		menu.add(jmi);
		jmi = new JMenuItem("(Re)color by SWC Type");
		jmi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final Set<Path> selectedPaths = getSelectedPaths();
				for (final Path p : selectedPaths)
					p.setColorBySWCtype();
				plugin.repaintAllPanes();
				plugin.update3DViewerContents();
			}
		});
		menu.add(jmi);
		jmi = new JMenuItem("Reset");
		jmi.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				applyColortoSelectPaths(null);
			}
		});
		menu.add(jmi);
		return menu;
	}

	private void applyColortoSelectPaths(final Color color) {
		final Set<Path> selectedPaths = getSelectedPaths();
		for (final Path p : selectedPaths)
			p.setColor(color);
		plugin.repaintAllPanes();
		plugin.update3DViewerContents();
	}

	protected void showPopup(final MouseEvent me) {
		assert SwingUtilities.isEventDispatchThread();
		// Possibly adjust the selection here:
		popup.show(me.getComponent(), me.getX(), me.getY());
	}

	protected void setButtonsEnabled(final boolean enable) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				renameSetEnabled(enable);
				fitVolumeSetEnabled(enable);
				fillOutSetEnabled(plugin.getUIState() != NeuriteTracerResultsDialog.IMAGE_CLOSED);
				makePrimarySetEnabled(enable);
				deleteSetEnabled(enable);
				exportAsSWCSetEnabled(enable);
			}
		});
	}

	protected void getExpandedPaths(final HelpfulJTree tree, final TreeModel model, final MutableTreeNode node,
			final HashSet<Path> set) {
		assert SwingUtilities.isEventDispatchThread();
		final int count = model.getChildCount(node);
		for (int i = 0; i < count; i++) {
			final DefaultMutableTreeNode child = (DefaultMutableTreeNode) model.getChild(node, i);
			final Path p = (Path) child.getUserObject();
			if (tree.isExpanded(child.getPath())) {
				set.add(p);
			}
			if (!model.isLeaf(child))
				getExpandedPaths(tree, model, child, set);
		}
	}

	protected void setExpandedPaths(final HelpfulJTree tree, final TreeModel model, final MutableTreeNode node,
			final HashSet<Path> set, final Path justAdded) {
		assert SwingUtilities.isEventDispatchThread();
		final int count = model.getChildCount(node);
		for (int i = 0; i < count; i++) {
			final DefaultMutableTreeNode child = (DefaultMutableTreeNode) model.getChild(node, i);
			final Path p = (Path) child.getUserObject();
			if (set.contains(p) || ((justAdded != null) && (justAdded == p))) {
				tree.setExpanded(child.getPath(), true);
			}
			if (!model.isLeaf(child))
				setExpandedPaths(tree, model, child, set, justAdded);
		}

	}

	@Override
	public void setSelectedPaths(final HashSet<Path> selectedPaths, final Object source) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (source == this)
					return;
				final TreePath[] noTreePaths = {};
				tree.setSelectionPaths(noTreePaths);
				setSelectedPaths(tree, tree.getModel(), root, selectedPaths);
			}
		});
	}

	protected void setSelectedPaths(final HelpfulJTree tree, final TreeModel model, final MutableTreeNode node,
			final HashSet<Path> set) {
		assert SwingUtilities.isEventDispatchThread();
		final int count = model.getChildCount(node);
		for (int i = 0; i < count; i++) {
			final DefaultMutableTreeNode child = (DefaultMutableTreeNode) model.getChild(node, i);
			final Path p = (Path) child.getUserObject();
			if (set.contains(p)) {
				tree.setSelected(child.getPath());
			}
			if (!model.isLeaf(child))
				setSelectedPaths(tree, model, child, set);
		}
	}

	@Override
	public void setPathList(final String[] pathList, final Path justAdded, final boolean expandAll) {

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {

				// Save the selection state:

				final TreePath[] selectedBefore = tree.getSelectionPaths();
				final HashSet<Path> selectedPathsBefore = new HashSet<>();
				final HashSet<Path> expandedPathsBefore = new HashSet<>();

				if (selectedBefore != null)
					for (int i = 0; i < selectedBefore.length; ++i) {
						final TreePath tp = selectedBefore[i];
						final DefaultMutableTreeNode dmtn = (DefaultMutableTreeNode) tp.getLastPathComponent();
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

				final DefaultMutableTreeNode newRoot = new DefaultMutableTreeNode("All Paths");
				final DefaultTreeModel model = new DefaultTreeModel(newRoot);
				// DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
				final Path[] primaryPaths = pathAndFillManager.getPathsStructured();
				for (int i = 0; i < primaryPaths.length; ++i) {
					final Path primaryPath = primaryPaths[i];
					// Add the primary path if it's not just a fitted version of
					// another:
					if (primaryPath.fittedVersionOf == null)
						addNode(newRoot, primaryPath, model);
				}
				root = newRoot;
				tree.setModel(model);

				model.reload();

				// Set back the expanded state:
				if (expandAll) {
					for (int i = 0; i < tree.getRowCount(); ++i)
						tree.expandRow(i);
				} else
					setExpandedPaths(tree, model, root, expandedPathsBefore, justAdded);

				setSelectedPaths(tree, model, root, selectedPathsBefore);
			}
		});
	}

	protected void addNode(final MutableTreeNode parent, final Path childPath, final DefaultTreeModel model) {
		assert SwingUtilities.isEventDispatchThread();
		final MutableTreeNode newNode = new DefaultMutableTreeNode(childPath);
		model.insertNodeInto(newNode, parent, parent.getChildCount());
		for (final Path p : childPath.children)
			addNode(newNode, p, model);
	}

	@Override
	public void setFillList(final String[] fillList) {

	}

}

class TreeTransferHandler extends TransferHandler {

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
			if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(
				flavor);
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
