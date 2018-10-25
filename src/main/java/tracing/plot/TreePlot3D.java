/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2010 - 2018 Fiji developers.
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
package tracing.plot;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.jzy3d.bridge.awt.FrameAWT;
import org.jzy3d.chart.AWTChart;
import org.jzy3d.chart.Chart;
import org.jzy3d.chart.controllers.ControllerType;
import org.jzy3d.chart.controllers.camera.AbstractCameraController;
import org.jzy3d.chart.controllers.mouse.AWTMouseUtilities;
import org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController;
import org.jzy3d.chart.factories.IFrame;
import org.jzy3d.colors.Color;
import org.jzy3d.io.IGLLoader;
import org.jzy3d.io.obj.OBJFileLoader;
import org.jzy3d.maths.BoundingBox3d;
import org.jzy3d.maths.Coord2d;
import org.jzy3d.maths.Coord3d;
import org.jzy3d.maths.Rectangle;
import org.jzy3d.plot3d.primitives.AbstractDrawable;
import org.jzy3d.plot3d.primitives.LineStrip;
import org.jzy3d.plot3d.primitives.Point;
import org.jzy3d.plot3d.primitives.Shape;
import org.jzy3d.plot3d.primitives.vbo.drawable.DrawableVBO;
import org.jzy3d.plot3d.rendering.canvas.ICanvas;
import org.jzy3d.plot3d.rendering.canvas.Quality;
import org.jzy3d.plot3d.rendering.legends.colorbars.AWTColorbarLegend;
import org.jzy3d.plot3d.rendering.lights.LightSet;
import org.jzy3d.plot3d.rendering.scene.Scene;
import org.jzy3d.plot3d.rendering.view.View;
import org.jzy3d.plot3d.rendering.view.ViewportMode;
import org.jzy3d.plot3d.rendering.view.modes.CameraMode;
import org.jzy3d.plot3d.rendering.view.modes.ViewBoundMode;
import org.jzy3d.plot3d.rendering.view.modes.ViewPositionMode;
import org.scijava.command.Command;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;
import org.scijava.util.FileUtils;

import com.jidesoft.swing.CheckBoxList;
import com.jogamp.opengl.GLException;

import ij.gui.HTMLDialog;
import net.imagej.ImageJ;
import net.imagej.display.ColorTables;
import net.imglib2.display.ColorTable;
import tracing.Path;
import tracing.SNT;
import tracing.Tree;
import tracing.analysis.TreeColorizer;
import tracing.gui.GuiUtils;
import tracing.gui.IconFactory;
import tracing.gui.IconFactory.GLYPH;
import tracing.gui.cmds.ColorRampCmd;
import tracing.gui.cmds.LoadObjCmd;
import tracing.util.PointInImage;


/**
 * Implements the SNT Reconstruction Viewer. Relies heavily on the
 * {@code org.jzy3d} package.
 * 
 * @author Tiago Ferreira
 */
public class TreePlot3D {

	private final static String ALLEN_MESH_LABEL = "MouseBrainAllen.obj";
	private final static String PATH_MANAGER_TREE_LABEL = "Path Manager Contents";
	private final static float DEF_NODE_RADIUS = 3f;
	private static final Color DEF_COLOR = Color.WHITE;

	/* Maps for plotted objects */
	private final Map<String, Shape> plottedTrees;
	private final Map<String, RemountableDrawableVBO> plottedObjs;
	private String lastImportKey;

	/* Settings */
	private Color defColor = DEF_COLOR;
	private float defThickness = DEF_NODE_RADIUS;
	private String screenshotDir;

	/* Color Bar */
	private AWTColorbarLegend cbar;
	private Shape cBarShape;

	/* Manager */
	private CheckBoxList managerList;
	private DefaultListModel<Object> managerModel;

	private Chart chart;
	private View view;
	private ViewerFrame frame;
	private GuiUtils gUtils;
	private KeyController keyControler;
	private MouseController mouseControler;

	/**
	 * Instantiates a new TreePlot3D.
	 */
	public TreePlot3D() {
		plottedTrees = new LinkedHashMap<>();
		plottedObjs = new LinkedHashMap<>();
		initView();
		setScreenshotDirectory("");
		initManagerList();
	}

	private boolean chartExists() {
		return chart != null && chart.getCanvas() != null;
	}

	/* returns true if chart was initialized */
	private boolean initView() {
		if (chartExists())
			return false;
		chart = new AWTChart(Quality.Nicest); // There does not seem to be a swing implementation of
												// ICameraMouseController so we are stuck with AWT
		chart.black();
		view = chart.getView();
		view.setBoundMode(ViewBoundMode.AUTO_FIT);
		keyControler = new KeyController(chart);
		mouseControler = new MouseController(chart);
		chart.getCanvas().addKeyController(keyControler);
		chart.getCanvas().addMouseController(mouseControler);
		chart.setAxeDisplayed(false);
		view.getCamera().setViewportMode(ViewportMode.STRETCH_TO_FILL);
		gUtils = new GuiUtils((Component) chart.getCanvas());
		return true;
	}

	/**
	 * Rebuilds entire scene. Useful to hard-reset this plot, e.g., to ensure all
	 * meshes are redraw.
	 * @see #updateView()
	 */
	public void rebuild() {
		chart = null;
		initView();
		addAllObjects();
		if (frame != null) frame.replaceCurrentChart(chart);
		updateView();
	}

	private void addAllObjects() {
		if (cBarShape != null && cbar != null) {
			chart.add(cBarShape);
			setColorbarColors(view.getBackgroundColor() == Color.BLACK);
		}
		plottedObjs.forEach((k, drawableVBO) -> {
			drawableVBO.unmount();
			chart.add(drawableVBO);
		});
		plottedTrees.forEach((k, surface) -> {
			chart.add(surface);
		});
		updateView();
		managerList.selectAll();
	}

	@SuppressWarnings("unchecked")
	private void initManagerList() {
		managerModel = new DefaultListModel<Object>();
		managerList = new CheckBoxList(managerModel);
		managerModel.addElement(CheckBoxList.ALL_ENTRY);
		managerList.getCheckBoxListSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					final Object[] values = managerList.getCheckBoxListSelectedValues();
					final List<String> selectedKeys = (List<String>) (List<?>) Arrays.asList(values);
					plottedTrees.forEach((k, surface) -> {
						surface.setDisplayed(selectedKeys.contains(k));
					});
					plottedObjs.forEach((k, drawableVBO) -> {
						drawableVBO.setDisplayed(selectedKeys.contains(k));
					});
					view.shoot();
				}
			}
		});
	}

	private Color fromAWTColor(final java.awt.Color color) {
		return (color == null) ? defColor
				: new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	private Color fromColorRGB(final ColorRGB color) {
		return (color == null) ? defColor
				: new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	private String getUniqueLabel(final Tree tree) {
		String key = tree.getLabel();
		if (key == null || key.isEmpty() || plottedTrees.containsKey(key)) {
			key = "Tree " + plottedTrees.size();
		}
		return key;
	}

	/**
	 * Adds a tree to this plot. It is displayed immediately if {@link #show()} has
	 * been called. Note that calling {@link #updateView()} may be required to
	 * ensure that the current View's bounding box includes the added Tree.
	 *
	 * @param tree the {@link Tree)} to be added. The Tree's label will be used as
	 *             identifier. It is expected to be unique when plotting multiple
	 *             Trees, if not (or no label exists) a unique label will be
	 *             generated based on the number of Trees currently plotted.
	 * 
	 * @see {@link Tree#getLabel()}
	 * @see #remove(String)
	 * @see #updateView()
	 */
	public void add(final Tree tree) {
		final Shape surface = getShape(tree);
		final String label = getUniqueLabel(tree);
		plottedTrees.put(label, surface);
		addItemToManager(label);
		initView();
		chart.add(surface);
	}

	private Shape getShape(final Tree tree) {
		final List<LineStrip> lines = new ArrayList<>();
		for (final Path p : tree.list()) {
			final LineStrip line = new LineStrip(p.size());
			for (int i = 0; i < p.size(); ++i) {
				final PointInImage pim = p.getPointInImage(i);
				final Coord3d coord = new Coord3d(pim.x, pim.y, pim.z);
				final Color color = fromAWTColor(p.hasNodeColors() ? p.getNodeColor(i) : p.getColor());
				final float width = Math.max((float) p.getNodeRadius(i), DEF_NODE_RADIUS);
				line.add(new Point(coord, color, width));
			}
			line.setShowPoints(true);
			line.setWireframeWidth(defThickness);
			lines.add(line);
		}

		// group all lines into a Composite
		final Shape surface = new Shape();
		surface.add(lines);
		surface.setFaceDisplayed(true);
		surface.setWireframeDisplayed(true);
		return surface;
	}

	private void addItemToManager(final String label) {
		final int index = managerModel.size() - 1;
		managerModel.insertElementAt(label, index);
		managerList.ensureIndexIsVisible(index);
		lastImportKey = label;
	}

	private boolean deleteItemFromManager(final String label) {
		return managerModel.removeElement(label);
	}

	/**
	 * Updates the plot's view, ensuring all objects are rendered within axes
	 * dimensions.
	 * @see #rebuild()
	 */
	public void updateView() {
		if (view != null) {
			view.shoot(); // !? without forceRepaint() dimensions are not updated
			view.lookToBox(view.getScene().getGraph().getBounds());
		}
	}

	/**
	 * Adds a color bar legend (LUT ramp).
	 *
	 * @param colorTable the color table
	 * @param min        the minimum value in the color table
	 * @param max        the maximum value in the color table
	 */
	public void addColorBarLegend(final ColorTable colorTable, final float min, final float max) {
		cBarShape = new Shape();
		cBarShape.setColorMapper(new ColorTableMapper(colorTable, min, max));
		cbar = new AWTColorbarLegend(cBarShape, view.getAxe().getLayout());
		setColorbarColors(view.getBackgroundColor() == Color.BLACK);
		// cbar.setMinimumSize(new Dimension(100, 600));
		cBarShape.setLegend(cbar);
		chart.add(cBarShape);
	}

	private void setColorbarColors(final boolean darkMode) {
		if (cbar == null)
			return;
		if (darkMode) {
			cbar.setBackground(Color.BLACK);
			cbar.setForeground(Color.WHITE);
		} else {
			cbar.setBackground(Color.WHITE);
			cbar.setForeground(Color.BLACK);
		}
	}

	/**
	 * Shows this TreePlot and returns a reference to its frame. If the frame has
	 * been made displayable, this will simply make the frame visible. Should only
	 * be called once all objects have been added to the Plot.
	 *
	 * @param showManager whether the 'Reconstruction Manager' dialog should be
	 *                    displayed. It is only respect the first time the method is
	 *                    called, i.e., when the frame is first made displayable.
	 * @return the frame containing the plot.
	 */
	public Frame show(final boolean showManager) {
		final boolean viewInitialized = initView();
		if (!viewInitialized && frame != null) {
			updateView();
			frame.setVisible(true);
			return frame;
		} else if (viewInitialized) {
			plottedTrees.forEach((k, surface) -> {
				chart.add(surface);
			});
			plottedObjs.forEach((k, drawableVBO) -> {
				chart.add(drawableVBO);
			});
		}
		frame = new ViewerFrame(chart, showManager);
		displayMsg("Press 'H' or 'F1' for help", 3000);
		return frame;
	}

	private void displayMsg(final String msg) {
		displayMsg(msg, 2500);
	}

	private void displayMsg(final String msg, final int msecs) {
		if (gUtils != null && chartExists()) {
			gUtils.setTmpMsgTimeOut(msecs);
			gUtils.tempMsg(msg);
		} else {
			System.out.println(msg);
		}
	}

	/**
	 * Returns the Collection of Trees in this plot.
	 *
	 * @return the plotted Trees (keys being the Tree identifier as per
	 *         {@link #add(Tree)})
	 */
	public Map<String, Shape> getTrees() {
		return plottedTrees;
	}

	/**
	 * Returns the Collection of OBJ meshes imported into this plot.
	 *
	 * @return the plotted Meshes (keys being the filename of the imported OBJ file
	 *         as per {@link #loadOBJ(String, ColorRGB, double)}
	 */
	public Map<String, DrawableVBO> getOBJs() {
		final Map<String,DrawableVBO> newMap =new LinkedHashMap<String,DrawableVBO>();
		plottedObjs.forEach((k, drawable) -> {
			newMap.put(k, (DrawableVBO) drawable);
		});
		return newMap;
	}

	/**
	 * Removes the specified Tree.
	 *
	 * @param treeLabel the key defining the tree to be removed.
	 * @return true, if tree was successfully removed.
	 * @see #add(Tree)
	 */
	public boolean remove(final String treeLabel) {
		final Shape tree = plottedTrees.get(treeLabel);
		if (tree == null)
			return false;
		boolean removed = plottedTrees.remove(treeLabel) != null;
		if (chart != null) {
			removed = removed && chart.getScene().getGraph().remove(tree);
			deleteItemFromManager(treeLabel);
		}
		return removed;
	}

	/**
	 * (Re)loads the current list of Paths in the Path Manager list.
	 *
	 * @return true, if synchronization was apparently successful, false otherwise
	 * @throws UnsupportedOperationException if SNT is not running
	 */
	public boolean syncPathManagerList() throws UnsupportedOperationException{
		if (SNT.getPluginInstance() == null)
			throw new IllegalArgumentException("SNT is not running.");
		final Tree tree = new Tree(SNT.getPluginInstance().getPathAndFillManager().getPathsFiltered());
		if (plottedTrees.containsKey(PATH_MANAGER_TREE_LABEL)) {
			chart.getScene().getGraph().remove(plottedTrees.get(PATH_MANAGER_TREE_LABEL));
			final Shape newShape = getShape(tree);
			plottedTrees.put(PATH_MANAGER_TREE_LABEL, newShape);
			chart.add(newShape);
		} else {
			tree.setLabel(PATH_MANAGER_TREE_LABEL);
			add(tree);
		}
		updateView();
		managerList.addCheckBoxListSelectedValue(PATH_MANAGER_TREE_LABEL, false);
		return plottedTrees.get(PATH_MANAGER_TREE_LABEL).isDisplayed();
	}

	private boolean lastImportedIsDisplayed() {
		boolean isDisplayed = false;
		if (plottedTrees.keySet().contains(lastImportKey)) {
			isDisplayed = plottedTrees.get(lastImportKey).isDisplayed();
		} else if (plottedObjs.keySet().contains(lastImportKey)) {
			isDisplayed = plottedObjs.get(lastImportKey).isDisplayed();
		}
		return isDisplayed;
	}

	private void lastImportedObjectNotRendered() {
		if (lastImportedIsDisplayed()) return;
		final GuiUtils guiUtils = (frame.manager != null && frame.manager.isActive()) ? new GuiUtils(frame.manager) : gUtils;
		if (guiUtils.getConfirmation(lastImportKey //
				+ " was imported but it does not seem to be " //
				+ " visible in current view. A scene rebuild may be "//
				+ "required. Rebuild now?", lastImportKey + " Not Visible")) {
			rebuild();
		}
	}

	/**
	 * Toggles the visibility of a plotted Tree or a loaded OBJ mesh.
	 *
	 * @param treeOrObjLabel the unique identifier of the Tree (as per
	 *                       {@link #add(Tree)}), or the filename of the loaded OBJ
	 *                       {@link #loadOBJ(String, java.awt.Color)}
	 * @param visible        whether the Object should be displayed
	 */
	public void setVisible(final String treeOrObjLabel, final boolean visible) {
		final Shape tree = plottedTrees.get(treeOrObjLabel);
		if (tree != null)
			tree.setDisplayed(visible);
		final DrawableVBO obj = plottedObjs.get(treeOrObjLabel);
		if (obj != null)
			obj.setDisplayed(visible);
	}

	/**
	 * Sets the screenshot directory.
	 *
	 * @param screenshotDir the absolute file path of the screenshot saving
	 *                      directory. Set it to {@code null} to have screenshots
	 *                      saved in the default directory: the Desktop folder of
	 *                      the user's home directory
	 */
	public void setScreenshotDirectory(final String screenshotDir) {
		if (screenshotDir == null || screenshotDir.isEmpty()) {
			this.screenshotDir = System.getProperty("user.home") + File.separator + "Desktop";
		} else {
			this.screenshotDir = screenshotDir;
		}
	}

	/**
	 * Gets the screenshot directory.
	 *
	 * @return the screenshot directory
	 */
	public String getScreenshotDirectory() {
		return screenshotDir;
	}

	/**
	 * Saves a screenshot of current plot as a PNG image. Image is saved using an
	 * unique time stamp as a file name in the directory specified by
	 * {@link #getScreenshotDirectory()}
	 * 
	 *
	 * @return true, if successful
	 * @throws IllegalArgumentException if Viewer is not available, i.e.,
	 *                                  {@link #getView()} is null
	 */
	public boolean saveScreenshot() throws IllegalArgumentException {
		if (!chartExists()) {
			throw new IllegalArgumentException("Viewer is not visible");
		}
		final String file = new SimpleDateFormat("'SNT 'yyyy-MM-dd HH-mm-ss'.png'").format(new Date());
		try {
			final File f = new File(screenshotDir, file);
			SNT.log("Saving snapshot to " + f);
			chart.screenshot(f);
		} catch (final IOException e) {
			SNT.error("IOException", e);
			return false;
		}
		return true;
	}

	/**
	 * Loads a Wavefront .OBJ file file (Experimental). Note that some meshes may
	 * not be supported or rendered properly.
	 *
	 * @param filePath            the absolute file path (or URL) of the file to be
	 *                            imported. The filename is used as unique
	 *                            identifier of the object (see
	 *                            {@link #setVisible(String, boolean)})
	 * @param color               the color to render the imported file
	 * @param transparencyPercent the color transparency (in percentage)
	 * @return true, if file was successfully loaded
	 * @throws IllegalArgumentException if Viewer is not available, i.e.,
	 *                                  {@link #getView()} is null
	 * @see #updateView()
	 */
	public boolean loadOBJ(final String filePath, final ColorRGB color, final double transparencyPercent)
			throws IllegalArgumentException {
		final ColorRGB inputColor = (color == null) ? Colors.WHITE : color;
		final Color c = new Color(inputColor.getRed(), inputColor.getGreen(), inputColor.getBlue(),
				(int) Math.round((100 - transparencyPercent) * 255 / 100));
		return loadOBJ(filePath, c);
	}

	/**
	 * Loads the contour mesh for Allen Mouse Brain Atlas reference. It will simply
	 * make the mesh visible if has already been loaded.
	 *
	 * @return true, if import was successful
	 * @throws IllegalArgumentException if Viewer is not available, i.e.,
	 *                                  {@link #getView()} is null
	 */
	public boolean loadMouseRefBrain() throws IllegalArgumentException {
		if (getOBJs().keySet().contains(ALLEN_MESH_LABEL)) {
			setVisible(ALLEN_MESH_LABEL, true);
			return false;
		}
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		final URL url = loader.getResource("meshes/" + ALLEN_MESH_LABEL);
		if (url == null) throw new IllegalArgumentException(ALLEN_MESH_LABEL + " not found");
		return loadOBJ(url.getPath(), new Color(1f, 1f, 1f, 0.05f));
	}

	private boolean loadOBJ(final String filePath, final Color color) throws IllegalArgumentException {
		if (getView() == null) {
			throw new IllegalArgumentException("Viewer is not available");
		}
		final OBJFileLoader loader = new OBJFileLoader(
				(filePath.startsWith("http") || filePath.startsWith("file:/")) ? filePath : "file://" + filePath);
		final RemountableDrawableVBO drawable = new RemountableDrawableVBO(loader);
		drawable.setColor(color);
		//drawable.setQuality(chart.getQuality());
		final int nElemens = getSceneElements().size();
		chart.add(drawable, true);
		final boolean success = getSceneElements().size() > nElemens;
		if (success) {
			final String label = new File(filePath).getName();
			plottedObjs.put(label, drawable);
			addItemToManager(label);
			SNT.log("Successfully loaded "+ filePath);
			if (frame != null && frame.isVisible()) {
				updateView();
				if (!drawable.isDisplayed()) {
					SNT.log("Error: Loaded mesh is not being displayed. Attempting to reload...");
				}
			}
		} else {
			SNT.log("Failed to load "+ filePath);
		}
		return success;
	}

	private List<AbstractDrawable> getSceneElements() {
		return chart.getScene().getGraph().getAll();
	}

	/**
	 * Returns this plot's {@link View} holding {@link Scene}, {@link LightSet},
	 * {@link ICanvas}, etc.
	 * 
	 * @return this plot's View, or null if it was disposed after {@link #show()}
	 *         has been called
	 */
	public View getView() {
		return (chart == null) ? null : view;
	}

	//NB: MouseContoller does not seem to work with FrameSWing so we are stuck with AWT
	private class ViewerFrame extends FrameAWT implements IFrame {

		private static final long serialVersionUID = 1L;
		private Chart chart;
		private Component canvas;
		private JDialog manager;

		/**
		 * Instantiates a new viewer frame.
		 *
		 * @param chart          the chart to be rendered in the frame
		 * @param includeManager whether the "Reconstruction Viewer Manager" dialog
		 *                       should be made visible
		 */
		public ViewerFrame(final Chart chart, final boolean includeManager) {
			initialize(chart, new Rectangle(800, 600), "Reconstruction Viewer");
			if (includeManager) {
				manager = getManager();
				managerList.selectAll();
				manager.setVisible(true);
			}
		}

		public void replaceCurrentChart(final Chart chart) {
			this.chart = chart;
			canvas = (Component) chart.getCanvas();
			removeAll();
			add(canvas);
			doLayout();
			revalidate();
			update(getGraphics());
		}

		public JDialog getManager() {
			final JDialog dialog = new JDialog(this, "RV Controls");
			dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			dialog.setLocationRelativeTo(this);
			final JPanel panel = new ManagerPanel(new GuiUtils(dialog));
			dialog.setContentPane(panel);
			dialog.pack();
			return dialog;
		}

		/* (non-Javadoc)
		 * @see org.jzy3d.bridge.awt.FrameAWT#initialize(org.jzy3d.chart.Chart, org.jzy3d.maths.Rectangle, java.lang.String)
		 */
		@Override
		public void initialize(final Chart chart, final Rectangle bounds, final String title) {
			this.chart = chart;
			canvas = (Component) chart.getCanvas();
			setTitle(title);
			add(canvas);
			pack();
			setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(final WindowEvent e) {
					ViewerFrame.this.remove(canvas);
					ViewerFrame.this.chart.dispose();
					ViewerFrame.this.chart = null;
					if (ViewerFrame.this.manager != null)
						ViewerFrame.this.manager.dispose();
					ViewerFrame.this.dispose();
				}
			});
			setVisible(true);
		}

		/* (non-Javadoc)
		 * @see org.jzy3d.bridge.awt.FrameAWT#initialize(org.jzy3d.chart.Chart, org.jzy3d.maths.Rectangle, java.lang.String, java.lang.String)
		 */
		@Override
		public void initialize(final Chart chart, final Rectangle bounds, final String title, final String message) {
			initialize(chart, bounds, title + message);
		}
	}

	private class ManagerPanel extends JPanel {

		private static final long serialVersionUID = 1L;
		private final GuiUtils guiUtils;

		public ManagerPanel(final GuiUtils guiUtils) {
			super();
			this.guiUtils = guiUtils;
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			final JScrollPane scrollPane = new JScrollPane(managerList);
			scrollPane.setBorder(null);
			scrollPane.setViewportView(managerList);
			add(scrollPane);
			scrollPane.revalidate();
			add(buttonPanel());
		}

		private JPanel buttonPanel() {
			final JPanel buttonPanel = new JPanel(new GridLayout(1, 3));
			buttonPanel.setBorder(null);
			// do not allow panel to resize vertically
			buttonPanel.setMaximumSize(new Dimension(buttonPanel.getMaximumSize().width,
					(int) buttonPanel.getPreferredSize().getHeight()));
			buttonPanel.add(menuButton(GLYPH.SYNC, reloadMenu()));
			buttonPanel.add(menuButton(GLYPH.PLUS, addMenu()));
			buttonPanel.add(menuButton(GLYPH.OPTIONS, optionsMenu()));
			return buttonPanel;
		}

		private JButton menuButton(GLYPH glyph, JPopupMenu menu) {
			final JButton button = new JButton(IconFactory.getButtonIcon(glyph));
			button.addActionListener(e -> menu.show(button, button.getWidth() / 2, button.getHeight() / 2));
			return button;
		}

		private JPopupMenu reloadMenu() {
			final JPopupMenu reloadMenu = new JPopupMenu();
			JMenuItem mi = new JMenuItem("Sync Path Manager Changes");
			mi.addActionListener(e -> {
				try {
					if (!syncPathManagerList()) rebuild();
					displayMsg("Path Manager contents updated");
				} catch (UnsupportedOperationException ex) {
					guiUtils.error(ex.getMessage());
				}
			});
			reloadMenu.add(mi);
			mi = new JMenuItem("Refresh View/Update Bounds");
			mi.addActionListener(e -> {
				updateView();
				if (!allCheckedtemsVisible() && guiUtils.getConfirmation(
						"View was refreshed but some objects may not be visible. "//
						+ "Rebuild 3D Scene Completely?", "Rebuild Required")) {
					rebuild();
				} else {
					displayMsg("View refreshed");
				}
			});
			reloadMenu.add(mi);
			reloadMenu.addSeparator();
			mi = new JMenuItem("Rebuild Scene...");
			mi.addActionListener(e -> {
				if (guiUtils.getConfirmation("Rebuild 3D Scene Completely?", "Force Rebuild")) {
					rebuild();
				}
			});
			reloadMenu.add(mi);
			return reloadMenu;
		}

		@SuppressWarnings("unchecked")
		private boolean allCheckedtemsVisible() {
			final Object[] values = managerList.getCheckBoxListSelectedValues();
			final List<String> selectedKeys = (List<String>) (List<?>) Arrays.asList(values);
			if (selectedKeys.isEmpty()) {
				for (final Shape s: plottedTrees.values()) {
					if (s.isDisplayed()) return false;
				}
				for (final RemountableDrawableVBO d: plottedObjs.values()) {
					if (d.isDisplayed()) return false;
				}
			}
			for (final Map.Entry<String, Shape> item : plottedTrees.entrySet()) {
				if (selectedKeys.contains(item.getKey()) && !item.getValue().isDisplayed())
					return false;
			}
			for (final Entry<String, RemountableDrawableVBO> item : plottedObjs.entrySet()) {
				if (selectedKeys.contains(item.getKey()) && !item.getValue().isDisplayed())
					return false;
			}
			return true;
		}
	
		private JPopupMenu optionsMenu() {
			final JPopupMenu optionsMenu = new JPopupMenu();
			JMenuItem mi = new JMenuItem("Path Thickness...");
			mi.addActionListener(e -> {
				if (plottedTrees.isEmpty()) {
					guiUtils.error("There are no loaded meshes");
					return;
				}
				final Double thickness = guiUtils.getDouble(
						"<HTML><body><div style='width:500;'>"
								+ "Please specify a constant thickness to be applied "
								+ "to all reconstructions. This value only affects how "
								+ "Paths are displayed in the Reconstruction Viewer.",
								"Path Thickness", getDefaultThickness());
				if (thickness == null) {
					return; // user pressed cancel
				}
				if (Double.isNaN(thickness) || thickness <= 0) {
					guiUtils.error("Invalid thickness value.");
					return;
				}
				applyThicknessToPlottedTrees(thickness.floatValue());
			});
			optionsMenu.add(mi);
			mi = new JMenuItem("Recolor Mesh(es)...");
			mi.addActionListener(e -> {
				if (plottedObjs.isEmpty()) {
					guiUtils.error("There are no loaded meshes");
					return;
				}
				final java.awt.Color c = guiUtils.getColor("Mesh(es) Color", java.awt.Color.WHITE, "HSB");
				if (c == null) {
					return; // user pressed cancel
				}
				applyColorToPlottedObjs(fromAWTColor(c));
			});
			optionsMenu.add(mi);
			optionsMenu.addSeparator();
			mi = new JMenuItem("Screenshot Directory...");
			mi.addActionListener(e -> {
				final File oldDir = new File(getScreenshotDirectory());
				final File newDir = guiUtils
						.chooseDirectory("Choose Directory for saving Rec. Viewer's screenshots", oldDir);
				if (newDir != null) {
					final String newPath = newDir.getAbsolutePath();
					setScreenshotDirectory(newPath);
					displayMsg("Screenshot directory is now "+ FileUtils.limitPath(newPath, 50));
				}
			});
			optionsMenu.add(mi);
			optionsMenu.addSeparator();
			mi = new JMenuItem("Keyboard Operations...");
			mi.addActionListener(e -> keyControler.showHelp(true));
			optionsMenu.add(mi);
			return optionsMenu;
		}

		private JPopupMenu addMenu() {
			final JPopupMenu addMenu = new JPopupMenu();
			final JMenu legendMenu = new JMenu("Color Legend");
			final JMenu meshMenu = new JMenu("Meshes");
			addMenu.add(legendMenu);
			addMenu.add(meshMenu);

			JMenuItem mi = new JMenuItem("Add...");
			mi.addActionListener(e -> runSNTCmd(ColorRampCmd.class));
			legendMenu.add(mi);
			mi = new JMenuItem("Remove All...");
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all color legends from scene?", "Remove All Legends?")) {
					return;
				}
				final List<AbstractDrawable> allDrawables = chart.getScene().getGraph().getAll();
				for (final Iterator<AbstractDrawable> iterator = allDrawables.iterator(); iterator.hasNext();) {
					final AbstractDrawable drawable = iterator.next();
					if (drawable != null && drawable.hasLegend() && drawable.isLegendDisplayed())
						chart.getScene().getGraph().remove(drawable);
				}
				cbar = null;
				cBarShape = null;
			});
			legendMenu.add(mi);

			mi = new JMenuItem("Load OBJ...");
			mi.addActionListener(e -> runSNTCmd(LoadObjCmd.class));
			meshMenu.add(mi);
			mi = new JMenuItem("Load Allen Mouse Brain Atlas Contour");
			mi.addActionListener(e -> {
				if (getOBJs().keySet().contains(ALLEN_MESH_LABEL)) {
					guiUtils.error(ALLEN_MESH_LABEL + " is already loaded.");
					managerList.addCheckBoxListSelectedValue(ALLEN_MESH_LABEL, true);
					return;
				}
				try {
					if (!loadMouseRefBrain()) {
						guiUtils.error("Reference brain could not be loaded");
						return;
					}
					lastImportedObjectNotRendered();
				} catch (final GLException ex) {
					SNT.error("Scene rebuilt upon exception", ex);
					rebuild();
					displayMsg("Scene rebuilt upon exception...");
				}
			});
			meshMenu.add(mi);
			meshMenu.addSeparator();
			mi = new JMenuItem("Remove All...");
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all meshes from scene?", "Remove All Meshes?")) {
					return;
				}
				final Iterator<Entry<String, RemountableDrawableVBO>> it = plottedObjs.entrySet().iterator();
				while (it.hasNext()) {
					final Map.Entry<String, RemountableDrawableVBO> entry = it.next();
					chart.getScene().getGraph().remove(entry.getValue());
					managerModel.removeElement(entry.getKey());
					it.remove();
				}
			});
			meshMenu.add(mi);
			return addMenu;
		}


		private boolean sntRunning() {
			if (SNT.getPluginInstance() != null) {
				return true;
			}
			guiUtils.error("This command requires SNT to be running.");
			return false;
		}

		private void runSNTCmd(final Class<? extends Command> cmdClass) {
			if (sntRunning()) {
				SNT.getPluginInstance().getUI().runCmd(cmdClass);
			}
		}
	}

	private class MouseController extends AWTCameraMouseController {

		private final float PAN_FACTOR = 1f; // lower values mean more responsive pan
		private boolean panDone;
		private Coord3d prevMouse3d;

		public MouseController(final Chart chart) {
			super(chart);
		}

		private int getY(final MouseEvent e) {
			return -e.getY() + chart.getCanvas().getRendererHeight();
		}

		private void rotateLive(final Coord2d move) {
			rotate(move, true);
		}

		/* see AWTMousePickingPan2dController */
		public void pan(final Coord3d from, final Coord3d to) {
			final BoundingBox3d viewBounds = view.getBounds();
			final Coord3d offset = to.sub(from).div(-PAN_FACTOR);
			final BoundingBox3d newBounds = viewBounds.shift(offset);
			view.setBoundManual(newBounds);
			view.shoot();
			fireControllerEvent(ControllerType.PAN, offset);
		}

		public void zoom(final float factor) {
			final BoundingBox3d viewBounds = view.getBounds();
			final BoundingBox3d newBounds = viewBounds.scale(new Coord3d(factor, factor, factor));
			view.setBoundManual(newBounds);
			view.shoot();
			fireControllerEvent(ControllerType.ZOOM, factor);
		}

		public void snapToNextView() {
			final ViewPositionMode[] modes = { ViewPositionMode.FREE, ViewPositionMode.PROFILE, ViewPositionMode.TOP };
			final String[] descriptions = { "Unconstrained", "Side Constrained", "Top Constrained" };
			final ViewPositionMode currentView = chart.getViewMode();
			int nextViewIdx = 0;
			for (int i = 0; i < modes.length; i++) {
				if (modes[i] == currentView) {
					nextViewIdx = i + 1;
					break;
				}
			}
			if (nextViewIdx == modes.length)
				nextViewIdx = 0;
			stopThreadController();
			chart.setViewMode(modes[nextViewIdx]);
			displayMsg("View Mode: " + descriptions[nextViewIdx]);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController#
		 * mousePressed(java.awt.event.MouseEvent)
		 */
		@Override
		public void mousePressed(final MouseEvent e) {
			if (e.isControlDown() && AWTMouseUtilities.isLeftDown(e)) {
				snapToNextView();
			} else {
				super.mousePressed(e);
			}
			prevMouse3d = view.projectMouse(e.getX(), getY(e));
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController#
		 * mouseWheelMoved(java.awt.event.MouseWheelEvent)
		 */
		@Override
		public void mouseWheelMoved(final MouseWheelEvent e) {
			stopThreadController();
			final float factor = 1 + (e.getWheelRotation() / 10.0f);
			zoom(factor);
			prevMouse3d = view.projectMouse(e.getX(), getY(e));
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.jzy3d.chart.controllers.mouse.camera.AWTCameraMouseController#
		 * mouseDragged(java.awt.event.MouseEvent)
		 */
		@Override
		public void mouseDragged(final MouseEvent e) {

			final Coord2d mouse = xy(e);

			// Rotate on left-click
			if (AWTMouseUtilities.isLeftDown(e)) {
				final Coord2d move = mouse.sub(prevMouse).div(100);
				rotate(move);
			}

			// Pan on right-click
			else if (AWTMouseUtilities.isRightDown(e)) {
				final Coord3d thisMouse3d = view.projectMouse(e.getX(), getY(e));
				if (!panDone) { // 1/2 pan for cleaner rendering
					pan(prevMouse3d, thisMouse3d);
					panDone = true;
				} else {
					panDone = false;
				}
				prevMouse3d = thisMouse3d;
			}
			prevMouse = mouse;
		}
	}

	/**
	 * This is just to make {@link DrawableVBO#hasMountedOnce()} accessible, which
	 * seems to allow meshes to be loaded during an interactive session
	 */
	private class RemountableDrawableVBO extends DrawableVBO {

		public RemountableDrawableVBO(final IGLLoader<DrawableVBO> loader) {
			super(loader);
		}

		public void unmount() {
			super.hasMountedOnce = false;
		}
	}


	private class KeyController extends AbstractCameraController implements KeyListener {

		private static final float STEP = 0.1f;

		public KeyController(final Chart chart) {
			register(chart);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.awt.event.KeyListener#keyPressed(java.awt.event.KeyEvent)
		 */
		@Override
		public void keyPressed(final KeyEvent e) {
			switch (e.getKeyChar()) {
			case 'a':
			case 'A':
				chart.setAxeDisplayed(!view.isAxeBoxDisplayed());
				break;
			case 'c':
			case 'C':
				changeCameraMode();
				break;
			case 'd':
			case 'D':
				toggleDarkMode();
				break;
			case 'h':
			case 'H':
				showHelp(false);
				break;
			case 'r':
			case 'R':
				chart.setViewPoint(View.DEFAULT_VIEW);
				chart.setViewMode(ViewPositionMode.FREE);
				view.setBoundMode(ViewBoundMode.AUTO_FIT);
				displayMsg("View reset");
				break;
			case 's':
			case 'S':
				saveScreenshot();
				displayMsg("Screenshot saved to "+ FileUtils.limitPath(getScreenshotDirectory(), 50));
				break;
			case '+':
			case '=':
				mouseControler.zoom(0.9f);
				break;
			case '-':
			case '_':
				mouseControler.zoom(1.1f);
				break;
			default:
				switch (e.getKeyCode()) {
				case KeyEvent.VK_F1:
					showHelp(true);
					break;
				case KeyEvent.VK_DOWN:
					mouseControler.rotateLive(new Coord2d(0f, -STEP));
					break;
				case KeyEvent.VK_UP:
					mouseControler.rotateLive(new Coord2d(0f, STEP));
					break;
				case KeyEvent.VK_LEFT:
					mouseControler.rotateLive(new Coord2d(-STEP, 0));
					break;
				case KeyEvent.VK_RIGHT:
					mouseControler.rotateLive(new Coord2d(STEP, 0));
					break;
				default:
					break;
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.awt.event.KeyListener#keyTyped(java.awt.event.KeyEvent)
		 */
		@Override
		public void keyTyped(final KeyEvent e) {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.awt.event.KeyListener#keyReleased(java.awt.event.KeyEvent)
		 */
		@Override
		public void keyReleased(final KeyEvent e) {
		}

		private void changeCameraMode() {
			final CameraMode newMode = (view.getCameraMode() == CameraMode.ORTHOGONAL) ? CameraMode.PERSPECTIVE
					: CameraMode.ORTHOGONAL;
			view.setCameraMode(newMode);
			final String mode = (newMode == CameraMode.ORTHOGONAL) ? "Orthogonal" : "Perspective";
			displayMsg("Camera mode changed to \"" + mode + "\"");
		}

		/* This seems to work only at initialization */
		@SuppressWarnings("unused")
		private void changeQuality() {
			final Quality[] levels = { Quality.Fastest, Quality.Intermediate, Quality.Advanced, Quality.Nicest };
			final String[] grades = { "Fastest", "Intermediate", "High", "Best" };
			final Quality currentLevel = chart.getQuality();
			int nextLevelIdx = 0;
			for (int i = 0; i < levels.length; i++) {
				if (levels[i] == currentLevel) {
					nextLevelIdx = i + 1;
					break;
				}
			}
			if (nextLevelIdx == levels.length)
				nextLevelIdx = 0;
			chart.setQuality(levels[nextLevelIdx]);
			displayMsg("Quality level changed to '" + grades[nextLevelIdx] + "'");
		}

		private void toggleDarkMode() {
			if (chart == null)
				return;
			Color newForeground;
			Color newBackground;
			if (view.getBackgroundColor() == Color.BLACK) {
				newForeground = Color.BLACK;
				newBackground = Color.WHITE;
				setColorbarColors(false);
			} else {
				newForeground = Color.WHITE;
				newBackground = Color.BLACK;
				setColorbarColors(true);
			}
			view.setBackgroundColor(newBackground);
			view.getAxe().getLayout().setGridColor(newForeground);
			view.getAxe().getLayout().setMainColor(newForeground);

			// Apply foreground color to trees with background color
			plottedTrees.values().forEach(shape -> {
				if (isSameRGB(shape.getColor(), newBackground)) {
					shape.setColor(newForeground);
					return; // replaces continue in lambda expression;
				}
				for (int i = 0; i < shape.size(); i++) {
					if (shape.get(i) instanceof LineStrip) {
						final List<Point> points = ((LineStrip) shape.get(i)).getPoints();
						points.stream().forEach(p -> {
							final Color pColor = p.getColor();
							if (isSameRGB(pColor, newBackground)) {
								changeRGB(pColor, newForeground);
							}
						});
					}
				}
			});

			// Apply foreground color to meshes with background color
			plottedObjs.values().forEach(obj -> {
				final Color objColor = obj.getColor();
				if (isSameRGB(objColor, newBackground)) {
					changeRGB(objColor, newForeground);
				}
			});

		}

		private boolean isSameRGB(final Color c1, final Color c2) {
			return c1 != null && c1.r == c2.r && c1.g == c2.g && c1.b == c2.b;
		}

		private void changeRGB(final Color from, final Color to) {
			from.r = to.r;
			from.g = to.g;
			from.b = to.b;
		}

		private void showHelp(final boolean showInDialog) {
			final StringBuffer sb = new StringBuffer("<HTML>");
			sb.append("<table>");
			sb.append("  <tr>");
			sb.append("    <td>Pan</td>");
			sb.append("    <td>Right-click &amp; drag</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Rotate</td>");
			sb.append("    <td>Left-click &amp; drag (or arrow keys)</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Scale</td>");
			sb.append("    <td>Scroll (or + / - keys)</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Animate</td>");
			sb.append("    <td>Double left-click</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Snap to View</td>");
			sb.append("    <td>Ctrl + left-click</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Toggle <u>A</u>xes</td>");
			sb.append("    <td>Press 'A'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Toggle <u>C</u>amera Mode &nbsp;</td>");
			sb.append("    <td>Press 'C'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td>Toggle <u>D</u>ark Mode</td>");
			sb.append("    <td>Press 'D'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td><u>R</u>eset View</td>");
			sb.append("    <td>Press 'R'</td>");
			sb.append("  </tr>");
			sb.append("  <tr>");
			sb.append("    <td><u>S</u>creenshot</td>");
			sb.append("    <td>Press 'S'</td>");
			sb.append("  </tr>");
			if (showInDialog) {
				sb.append("  <tr>");
				sb.append("    <td><u>H</u>elp</td>");
				sb.append("    <td>Press 'H' (notification) or F1 (list)</td>");
				sb.append("  </tr>");
			}
			sb.append("</table>");
			if (showInDialog) {
				new HTMLDialog("Reconstruction Viewer Shortcuts", sb.toString(), false);
			} else {
				displayMsg(sb.toString(), 9000);
			}
			
		}
	}



	
	/**
	 * This is just to make {@link DrawableVBO#hasMountedOnce()} accessible,
	 * allowing to force the re-loading of meshes during an interactive session
	 */
	private class RemountableDrawableVBO extends DrawableVBO {

		public RemountableDrawableVBO(final IGLLoader<DrawableVBO> loader) {
			super(loader);
		}

		public void unmount() {
			super.hasMountedOnce = false;
		}

	}

	/**
	 * This is a shameless version of {@link #OBJFileLoader} with extra methods that
	 * allow to check if OBJFile is valid before converting it into a Drawable #
	 */
	private class OBJFileLoaderPlus implements IGLLoader<DrawableVBO>{

		protected URL url;
		protected OBJFile obj;

		public OBJFileLoaderPlus(final URL url) {
			this.url = url;
			if (url == null) throw new IllegalArgumentException("Null URL");
		}

		public String getLabel() {
			String label = url.toString();
			label = label.substring(label.lastIndexOf("/") + 1);
			return getUniqueLabel(plottedObjs, "Mesh", label);
		}

		public boolean compileModel() {
			obj = new OBJFile();
			SNT.log("Loading OBJ file '" + url + "'");
			if (!obj.loadModelFromURL(url)) {
				SNT.log("Loading failed. Invalid file?");
				return false;
			}
			obj.compileModel();
			SNT.log(String.format("Meshed compiled: %d vertices and %d triangles", obj.getPositionCount(),
					(obj.getIndexCount() / 3)));
			return obj.getPositionCount() > 0;
		}

		@Override
		public void load(final GL gl, final DrawableVBO drawable) {
			final int size = obj.getIndexCount();
			final int indexSize = size * Buffers.SIZEOF_INT;
			final int vertexSize = obj.getCompiledVertexCount() * Buffers.SIZEOF_FLOAT;
			final int byteOffset = obj.getCompiledVertexSize() * Buffers.SIZEOF_FLOAT;
			final int normalOffset = obj.getCompiledNormalOffset() * Buffers.SIZEOF_FLOAT;
			final int dimensions = obj.getPositionSize();
			final int pointer = 0;
			final FloatBuffer vertices = obj.getCompiledVertices();
			final IntBuffer indices = obj.getCompiledIndices();
			final BoundingBox3d bounds = obj.computeBoundingBox();
			drawable.doConfigure(pointer, size, byteOffset, normalOffset, dimensions);
			drawable.doLoadArrayFloatBuffer(gl, vertexSize, vertices);
			drawable.doLoadElementIntBuffer(gl, indexSize, indices);
			drawable.doSetBoundingBox(bounds);
		}
	}

	/**
	 * Sets the line thickness for rendering {@link Tree}s that have no specified
	 * radius.
	 *
	 * @param thickness the new line thickness. Note that this value only applies to
	 *                  Paths that have no specified radius
	 */
	public void setDefaultThickness(final float thickness) {
		this.defThickness = thickness;
	}

	public void setDefaultColor(final ColorRGB color) {
		this.defColor = fromColorRGB(color);
	}

	/**
	 * Returns the default line thickness.
	 *
	 * @return the default line thickness used to render Paths without radius
	 */
	private float getDefaultThickness() {
		return defThickness;
	}

	/**
	 * Applies a constant thickness (line width) to plotted trees.
	 *
	 * @param thickness the thickness
	 */
	protected void applyThicknessToPlottedTrees(final float thickness) {
		plottedTrees.values().forEach(shape -> {
			for (int i = 0; i < shape.size(); i++) {
				if (shape.get(i) instanceof LineStrip) {
					((LineStrip) shape.get(i)).setWireframeWidth(thickness);
				}
			}
		});
	}

	/**
	 * Applies a constant color to plotted meshes.
	 *
	 * @param color the color
	 */
	protected void applyColorToPlottedObjs(final Color color) {
		plottedObjs.values().forEach(drawable -> {
			drawable.setColor(color);
		});
	}

	/* IDE debug method */
	public static void main(final String[] args) throws InterruptedException {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		final Tree tree = new Tree("/home/tferr/code/test-files/AA0100.swc");
		final TreeColorizer colorizer = new TreeColorizer(ij.getContext());
		colorizer.colorize(tree, TreeColorizer.BRANCH_ORDER, ColorTables.ICE);
		final double[] bounds = colorizer.getMinMax();
		SNT.setDebugMode(true);
		final TreePlot3D jzy3D = new TreePlot3D();
		jzy3D.addColorBarLegend(ColorTables.ICE, (float) bounds[0], (float) bounds[1]);
		jzy3D.add(tree);
		//jzy3D.loadMouseRefBrain();
		jzy3D.show(true);
	}

}
