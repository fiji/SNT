/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/* Copyright 2006, 2007, 2008, 2009, 2010, 2011 Mark Longair */

/*
  This file is part of the ImageJ plugin "Simple Neurite Tracer".

  The ImageJ plugin "Simple Neurite Tracer" is free software; you
  can redistribute it and/or modify it under the terms of the GNU
  General Public License as published by the Free Software
  Foundation; either version 3 of the License, or (at your option)
  any later version.

  The ImageJ plugin "Simple Neurite Tracer" is distributed in the
  hope that it will be useful, but WITHOUT ANY WARRANTY; without
  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  PARTICULAR PURPOSE.  See the GNU General Public License for more
  details.

  In addition, as a special exception, the copyright holders give
  you permission to combine this program with free software programs or
  libraries that are released under the Apache Public License.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package tracing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
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

import ij.IJ;
import ij.gui.ColorChooser;
import ij.gui.GenericDialog;
import ij.io.SaveDialog;

@SuppressWarnings("serial")
public class PathWindow extends JFrame implements PathAndFillListener, TreeSelectionListener, ActionListener {

	public static class HelpfulJTree extends JTree {

		public HelpfulJTree(final TreeNode root) {
			super(root);
			DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
			renderer.setClosedIcon(new NodeIcon(NodeIcon.PLUS));
			renderer.setOpenIcon(new NodeIcon(NodeIcon.MINUS));
			renderer.setLeafIcon(new NodeIcon(NodeIcon.EMPTY));
			setCellRenderer(renderer);
			assert SwingUtilities.isEventDispatchThread();
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

	// See http://stackoverflow.com/a/7984734
	public static class NodeIcon implements Icon {

		private static final int SIZE = 9;
		protected static final char PLUS = '+';
		protected static final char MINUS = '-';
		protected static final char EMPTY = ' ';

		private final char type;

		public NodeIcon(final char type) {
			this.type = type;
		}

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			g.setColor(UIManager.getColor("Tree.background"));
			g.fillRect(x, y, SIZE - 1, SIZE - 1);
			g.setColor(UIManager.getColor("Tree.hash").darker());
			g.drawRect(x, y, SIZE - 1, SIZE - 1);
			if (type == EMPTY)
				return;
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
								IJ.error("The following exception was thrown: " + e);
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
				IJ.error("No paths were selected for deletion");
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
				IJ.error("You must have exactly one path selected");
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
				IJ.error("" + see.getMessage());
				return;
			}

			final SaveDialog sd = new SaveDialog("Export SWC file ...", plugin.getImagePlus().getShortTitle(), ".swc");

			if (sd.getFileName() == null) {
				return;
			}

			final File saveFile = new File(sd.getDirectory(), sd.getFileName());
			if ((saveFile != null) && saveFile.exists()) {
				if (!IJ.showMessageWithCancel("Export data...",
						"The file " + saveFile.getAbsolutePath() + " already exists.\n" + "Do you want to replace it?"))
					return;
			}

			IJ.showStatus("Exporting SWC data to " + saveFile.getAbsolutePath());

			try {
				final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(saveFile), "UTF-8"));
				pw.println("# Exported from \"Simple Neurite Tracer\" version " + SimpleNeuriteTracer.PLUGIN_VERSION);
				for (final SWCPoint p : swcPoints)
					p.println(pw);
				pw.close();

			} catch (final IOException ioe) {
				IJ.error("Saving to " + saveFile.getAbsolutePath() + " failed");
				return;
			}

		} else if (source == fillOutButton || source == fillOutMenuItem) {
			if (selectedPaths.size() < 1) {
				IJ.error("You must have one or more paths in the list selected");
				return;
			}
			plugin.startFillingPaths(selectedPaths);
		} else if (source == fitVolumeButton || source == fitVolumeMenuItem) {
			if (selectedPaths.size() < 1) {
				IJ.error("You must have one or more paths in the list selected");
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
				IJ.error("You must have exactly one path selected");
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
				IJ.error("The new name cannot be empty");
				return;
			}
			synchronized (pathAndFillManager) {
				if (pathAndFillManager.getPathFromName(s, false) != null) {
					IJ.error("There is already a path with that name ('" + s + "')");
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
				IJ.error("The maximum permitted distance must be a postive number");
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

				IJ.error("Unexpectedly got an event from an unknown source");
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
		fillOutSetEnabled(true);
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
		fillOutSetEnabled(true);
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

		setBounds(x, y, 600, 240);
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
		renameButton = smallButton("Rename");
		fitVolumeButton = smallButton("Fit Volume");
		fillOutButton = smallButton("Fill Out");
		makePrimaryButton = smallButton("Make Primary");
		deleteButton = smallButton("Delete");
		exportAsSWCButton = smallButton("Export as SWC");

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
				pathAndFillManager.update3DViewerContents();
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
		pathAndFillManager.update3DViewerContents();
	}

	protected static JButton smallButton(final String text) {
		final double SCALE = .85;
		final JButton button = new JButton(text);
		final Font font = button.getFont();
		button.setFont(font.deriveFont((float) (font.getSize() * SCALE)));
		final Insets insets = button.getMargin();
		button.setMargin(new Insets((int) (insets.top * SCALE), (int) (insets.left * SCALE),
				(int) (insets.bottom * SCALE), (int) (insets.right * SCALE)));
		return button;
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
				fillOutSetEnabled(enable);
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
