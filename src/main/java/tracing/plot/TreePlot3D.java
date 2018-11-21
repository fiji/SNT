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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
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
import org.jzy3d.io.obj.OBJFile;
import org.jzy3d.maths.BoundingBox3d;
import org.jzy3d.maths.Coord2d;
import org.jzy3d.maths.Coord3d;
import org.jzy3d.maths.Rectangle;
import org.jzy3d.plot3d.primitives.AbstractDrawable;
import org.jzy3d.plot3d.primitives.AbstractWireframeable;
import org.jzy3d.plot3d.primitives.LineStrip;
import org.jzy3d.plot3d.primitives.Point;
import org.jzy3d.plot3d.primitives.Shape;
import org.jzy3d.plot3d.primitives.Sphere;
import org.jzy3d.plot3d.primitives.Tube;
import org.jzy3d.plot3d.primitives.vbo.drawable.DrawableVBO;
import org.jzy3d.plot3d.rendering.canvas.ICanvas;
import org.jzy3d.plot3d.rendering.canvas.IScreenCanvas;
import org.jzy3d.plot3d.rendering.canvas.Quality;
import org.jzy3d.plot3d.rendering.legends.colorbars.AWTColorbarLegend;
import org.jzy3d.plot3d.rendering.lights.LightSet;
import org.jzy3d.plot3d.rendering.scene.Scene;
import org.jzy3d.plot3d.rendering.view.View;
import org.jzy3d.plot3d.rendering.view.ViewportMode;
import org.jzy3d.plot3d.rendering.view.annotation.CameraEyeOverlayAnnotation;
import org.jzy3d.plot3d.rendering.view.modes.CameraMode;
import org.jzy3d.plot3d.rendering.view.modes.ViewBoundMode;
import org.jzy3d.plot3d.rendering.view.modes.ViewPositionMode;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.awt.AWTWindows;
import org.scijava.util.ColorRGB;
import org.scijava.util.Colors;
import org.scijava.util.FileUtils;

import com.jidesoft.swing.CheckBoxList;
import com.jidesoft.swing.ListSearchable;
import com.jidesoft.swing.Searchable;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GLAnimatorControl;
import com.jogamp.opengl.GLException;

import ij.gui.HTMLDialog;
import net.imagej.ImageJ;
import net.imagej.display.ColorTables;
import net.imglib2.display.ColorTable;
import tracing.Path;
import tracing.SNT;
import tracing.SNTService;
import tracing.Tree;
import tracing.analysis.TreeColorMapper;
import tracing.gui.GuiUtils;
import tracing.gui.IconFactory;
import tracing.gui.IconFactory.GLYPH;
import tracing.gui.cmds.ColorizeReconstructionCmd;
import tracing.gui.cmds.LoadObjCmd;
import tracing.gui.cmds.LoadReconstructionCmd;
import tracing.gui.cmds.MLImporterCmd;
import tracing.gui.cmds.RemoteSWCImporterCmd;
import tracing.util.PointInImage;
import tracing.util.SWCColor;


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
	private static final Color DEF_COLOR = new Color(1f, 1f, 1f, 0.05f);
	private static final Color INVERTED_DEF_COLOR = new Color(0f, 0f, 0f, 0.05f);

	/* Maps for plotted objects */
	private final Map<String, ShapeTree> plottedTrees;
	private final Map<String, RemountableDrawableVBO> plottedObjs;

	/* Settings */
	private Color defColor;
	private float defThickness = DEF_NODE_RADIUS;
	private String screenshotDir;

	/* Color Bar */
	private AWTColorbarLegend cBar;
	private Shape cBarShape;

	/* Manager */
	private CheckBoxList managerList;
	private DefaultListModel<Object> managerModel;

	private Chart chart;
	private View view;
	private ViewerFrame frame;
	private GuiUtils gUtils;
	private KeyController keyController;
	private MouseController mouseController;
	private boolean viewUpdatesEnabled = true;
	private final UUID uuid;

	@Parameter
	private CommandService cmdService;

	@Parameter
	private SNTService sntService;


	/**
	 * Instantiates TreePlot3D without the 'Controls' dialog ('kiosk mode'). Such a
	 * plot is more suitable for large datasets and allows for Trees to be added
	 * concurrently,
	 */
	public TreePlot3D() {
		plottedTrees = new TreeMap<>();
		plottedObjs = new TreeMap<>();
		initView();
		setScreenshotDirectory("");
		uuid = UUID.randomUUID();
	}

	/**
	 * Instantiates an interactive TreePlot3D with GUI Controls to import, manage
	 * and customize the plot scene.
	 * 
	 * @param context the SciJava application context providing the services
	 *                required by the class
	 */
	public TreePlot3D(final Context context) {
		this();
		initManagerList();
		context.inject(this);
	}

	/**
	 * Sets whether Plot's View should update (refresh) every time a new
	 * reconstruction (or mesh) is added/removed from the scene. Should be set to
	 * false when performing bulk operations;
	 *
	 * @param enabled Whether view updates should be enabled
	 */
	public void setViewUpdatesEnabled(final boolean enabled) {
		viewUpdatesEnabled = enabled;
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
		keyController = new KeyController(chart);
		mouseController = new MouseController(chart);
		chart.getCanvas().addKeyController(keyController);
		chart.getCanvas().addMouseController(mouseController);
		chart.setAxeDisplayed(false);
		view.getCamera().setViewportMode(ViewportMode.STRETCH_TO_FILL);
		gUtils = new GuiUtils((Component) chart.getCanvas());
		return true;
	}

	private void rebuild() {
		SNT.log("Rebuilding scene...");
		try {
			final boolean lighModeOn = !isDarkModeOn();
			chart.stopAnimator();
			chart.dispose();
			chart = null;
			initView();
			addAllObjects();
			updateView();
			if (lighModeOn) keyController.toggleDarkMode();
			if (managerList != null) managerList.selectAll();
		} catch (final GLException exc) {
			SNT.error("Rebuild Error", exc);
		}
		if (frame != null) frame.replaceCurrentChart(chart);
		updateView();
	}

	/**
	 * Checks if all drawables in the 3D scene are being rendered properly,
	 * rebuilding the entire scene if not. Useful to "hard-reset" the plot, e.g., to
	 * ensure all meshes are redraw.
	 * 
	 * @see #updateView()
	 */
	public void validate() {
		if (!sceneIsOK()) rebuild();
	}

	private boolean isDarkModeOn() {
		return view.getBackgroundColor() == Color.BLACK;
	}

	private void addAllObjects() {
		if (cBarShape != null && cBar != null) {
			chart.add(cBarShape, false);
			setColorbarColors(isDarkModeOn());
		}
		plottedObjs.forEach((k, drawableVBO) -> {
			drawableVBO.unmount();
			chart.add(drawableVBO, false);
		});
		plottedTrees.values().forEach(shapeTree -> chart.add(shapeTree.get(), false));
	}

	private void initManagerList() {
		managerModel = new DefaultListModel<Object>();
		managerList = new CheckBoxList(managerModel);
		managerModel.addElement(CheckBoxList.ALL_ENTRY);
		managerList.getCheckBoxListSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(final ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					final List<String> selectedKeys = getLabelsCheckedInManager();
					plottedTrees.forEach((k, shapeTree) -> {
						shapeTree.setDisplayed(selectedKeys.contains(k));
					});
					plottedObjs.forEach((k, drawableVBO) -> {
						drawableVBO.setDisplayed(selectedKeys.contains(k));
					});
					//view.shoot();
				}
			}
		});
	}

	private Color fromAWTColor(final java.awt.Color color) {
		return (color == null) ? getDefColor()
				: new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	private Color fromColorRGB(final ColorRGB color) {
		return (color == null) ? getDefColor()
				: new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
	}

	private String makeUniqueKey(final Map<String, ?> map, final String key) {
		for (int i = 2; i <= 100; i++) {
			final String candidate = key + " (" + i + ")";
			if (!map.containsKey(candidate)) return candidate;
		}
		return key + " (" + UUID.randomUUID() + ")";
	}

	private String getUniqueLabel(final Map<String, ?> map, final String fallbackPrefix, final String candidate) {
		final String label = (candidate == null || candidate.trim().isEmpty()) ? fallbackPrefix : candidate;
		return (map.containsKey(label)) ? makeUniqueKey(map, label) : label;
	}

	/**
	 * Adds a tree to this plot. Note that calling {@link #updateView()} may be
	 * required to ensure that the current View's bounding box includes the added
	 * Tree.
	 *
	 * @param tree the {@link Tree)} to be added. The Tree's label will be used as
	 *             identifier. It is expected to be unique when plotting multiple
	 *             Trees, if not (or no label exists) a unique label will be
	 *             generated.
	 * 
	 * @see {@link Tree#getLabel()}
	 * @see #remove(String)
	 * @see #updateView()
	 */
	public void add(final Tree tree) {
		final String label = getUniqueLabel(plottedTrees, "Tree ", tree.getLabel());
		final ShapeTree shapeTree = new ShapeTree(tree);
		plottedTrees.put(label, shapeTree);
		addItemToManager(label);
		chart.add(shapeTree.get(), viewUpdatesEnabled);
	}

	private void addItemToManager(final String label) {
		if (managerList == null) return;
		final int[] indices = managerList.getCheckBoxListSelectedIndices();
		final int index = managerModel.size() - 1;
		managerModel.insertElementAt(label, index);
		//managerList.ensureIndexIsVisible(index);
		managerList.addCheckBoxListSelectedIndex(index);
		for (final int i : indices) managerList.addCheckBoxListSelectedIndex(i);
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
		cBar = new AWTColorbarLegend(cBarShape, view.getAxe().getLayout());
		setColorbarColors(view.getBackgroundColor() == Color.BLACK);
		// cbar.setMinimumSize(new Dimension(100, 600));
		cBarShape.setLegend(cBar);
		chart.add(cBarShape, viewUpdatesEnabled);
	}

	private void setColorbarColors(final boolean darkMode) {
		if (cBar == null)
			return;
		if (darkMode) {
			cBar.setBackground(Color.BLACK);
			cBar.setForeground(Color.WHITE);
		} else {
			cBar.setBackground(Color.WHITE);
			cBar.setForeground(Color.BLACK);
		}
	}

	/**
	 * Shows this TreePlot and returns a reference to its frame. If the frame has
	 * been made displayable, this will simply make the frame visible. Should only
	 * be called once all objects have been added to the Plot.
	 * If this is an interactive Viewer, the  'Controls' dialog is also displayed.
	 *
	 * @return the frame containing the plot.
	 */
	public Frame show() {
		final boolean viewInitialized = initView();
		if (!viewInitialized && frame != null) {
			updateView();
			frame.setVisible(true);
			return frame;
		} else if (viewInitialized) {
			plottedTrees.forEach((k, shapeTree) -> {
				chart.add(shapeTree.get(), viewUpdatesEnabled);
			});
			plottedObjs.forEach((k, drawableVBO) -> {
				chart.add(drawableVBO, viewUpdatesEnabled);
			});
		}
		frame = new ViewerFrame(chart, managerList != null);
		displayMsg("Press 'H' or 'F1' for help", 3000);
		return frame;
	}

	@Deprecated
	public Frame show(final boolean showManager) {
		return show();
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
		final Map<String, Shape> map = new HashMap<>();
		plottedTrees.forEach((k, shapeTree) -> {
			map.put(k, shapeTree.get());
		});
		return map;
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
		final ShapeTree shapeTree = plottedTrees.get(treeLabel);
		if (shapeTree == null)
			return false;
		boolean removed = plottedTrees.remove(treeLabel) != null;
		if (chart != null) {
			removed = removed && chart.getScene().getGraph().remove(shapeTree.get(), viewUpdatesEnabled);
			if (removed) deleteItemFromManager(treeLabel);
		}
		return removed;
	}

	/**
	 * Removes the specified OBJ mesh.
	 *
	 * @param meshLabel the key defining the OBJ mesh to be removed.
	 * @return true, if mesh was successfully removed.
	 * @see #loadOBJ(String, ColorRGB, double)
	 */
	public boolean removeOBJ(final String meshLabel) {
		return removeDrawable(plottedObjs, meshLabel);
	}

	/**
	 * Removes all loaded OBJ meshes from current plot
	 */
	public void removeAllOBJs() {
		final Iterator<Entry<String, RemountableDrawableVBO>> it = plottedObjs.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry<String, RemountableDrawableVBO> entry = it.next();
			chart.getScene().getGraph().remove(entry.getValue(), false);
			managerModel.removeElement(entry.getKey());
			it.remove();
		}
		if (viewUpdatesEnabled) chart.render();	}

	/**
	 * Removes all the Trees from current plot
	 */
	public void removeAll() {
		final Iterator<Entry<String, ShapeTree>> it = plottedTrees.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry<String, ShapeTree> entry = it.next();
			chart.getScene().getGraph().remove(entry.getValue().get(), false);
			managerModel.removeElement(entry.getKey());
			it.remove();
		}
		if (viewUpdatesEnabled) chart.render();
	}

	@SuppressWarnings("unchecked")
	private List<String> getLabelsCheckedInManager() {
		final Object[] values = managerList.getCheckBoxListSelectedValues();
		final List<String> list = (List<String>) (List<?>) Arrays.asList(values);
		return list;
	}

	private <T extends AbstractDrawable> boolean allDrawablesRendered(final BoundingBox3d viewBounds, 
			final Map<String, T> map, final List<String> selectedKeys) {
		final Iterator<Entry<String, T>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			final Map.Entry<String, T> entry = it.next();
			final T drawable = entry.getValue();
			final BoundingBox3d bounds = drawable.getBounds();
			if (bounds == null || !viewBounds.contains(bounds)) return false;
			if ((selectedKeys.contains(entry.getKey()) && !drawable.isDisplayed())) {
				drawable.setDisplayed(true);
				if (!drawable.isDisplayed()) return false;
			}
		}
		return true;
	}

	private synchronized <T extends AbstractDrawable> boolean removeDrawable(final Map<String, T> map, final String label) {
		final T drawable = map.get(label);
		if (drawable == null)
			return false;
		boolean removed = map.remove(label) != null;
		if (chart != null) {
			removed = removed && chart.getScene().getGraph().remove(drawable, viewUpdatesEnabled);
			if (removed) deleteItemFromManager(label);
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
			chart.getScene().getGraph().remove(plottedTrees.get(PATH_MANAGER_TREE_LABEL).get());
			final ShapeTree newShapeTree = new ShapeTree(tree);
			plottedTrees.replace(PATH_MANAGER_TREE_LABEL, newShapeTree);
			chart.add(newShapeTree.get(), viewUpdatesEnabled);
		} else {
			tree.setLabel(PATH_MANAGER_TREE_LABEL);
			add(tree);
		}
		updateView();
		return plottedTrees.get(PATH_MANAGER_TREE_LABEL).get().isDisplayed();
	}

	private boolean isValid(final AbstractDrawable drawable) {
		return drawable.getBounds() != null
				&& drawable.getBounds().getRange().distanceSq(new Coord3d(0f, 0f, 0f)) > 0f;
	}

	private boolean sceneIsOK() {
		try {
			updateView();
		} catch (final GLException ignored) {
			SNT.log("Upate view failed...");
			return false;
		}
		// now check that everything  is visible
		if (managerList == null) return true;
		final List<String> selectedKeys = getLabelsCheckedInManager();
		final BoundingBox3d viewBounds = chart.view().getBounds();
		return allDrawablesRendered(viewBounds, plottedObjs, selectedKeys)
				&& allDrawablesRendered(viewBounds, getTrees(), selectedKeys);
	}

	/** returns true if a drawable was removed */
	@SuppressWarnings("unused")
	private <T extends AbstractDrawable> boolean removeInvalid(final Map<String, T> map) {
		final Iterator<Entry<String, T>> it = map.entrySet().iterator();
		final int initialSize = map.size();
		while (it.hasNext()) {
			final Entry<String, T> entry = it.next();
			if (!isValid(entry.getValue())) {
				if (chart.getScene().getGraph().remove(entry.getValue(), false))
					deleteItemFromManager(entry.getKey());
				it.remove();
			}
		}
		return initialSize > map.size();
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
		final ShapeTree treeShape = plottedTrees.get(treeOrObjLabel);
		if (treeShape != null)
			treeShape.get().setDisplayed(visible);
		final DrawableVBO obj = plottedObjs.get(treeOrObjLabel);
		if (obj != null)
			obj.setDisplayed(visible);
	}

	public double[] colorize(final String treeLabel, final String measurement, final ColorTable colorTable) {
		final ShapeTree treeShape = plottedTrees.get(treeLabel);
		if (treeShape == null) return null;
		return treeShape.colorize(measurement, colorTable);
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
	 * Loads a Wavefront .OBJ file. Files should be loaded _before_ displaying the
	 * scene, otherwise, if the scene is already visible, {@link #validate()} should
	 * be called to ensure all meshes are visible.
	 *
	 * @param filePath            the absolute file path (or URL) of the file to be
	 *                            imported. The filename is used as unique
	 *                            identifier of the object (see
	 *                            {@link #setVisible(String, boolean)})
	 * @param color               the color to render the imported file
	 * @param transparencyPercent the color transparency (in percentage)
	 * @throws IllegalArgumentException if filePath is invalid or file does not
	 *                                  contain a compilable mesh
	 */
	public void loadOBJ(final String filePath, final ColorRGB color, final double transparencyPercent)
			throws IllegalArgumentException {
		if (filePath == null || filePath.isEmpty()) {
			throw new IllegalArgumentException("Invalid file path");
		}
		final ColorRGB inputColor = (color == null) ? Colors.WHITE : color;
		final Color c = new Color(inputColor.getRed(), inputColor.getGreen(), inputColor.getBlue(),
				(int) Math.round((100 - transparencyPercent) * 255 / 100));
		SNT.log("Retrieving "+ filePath);
		final URL url;
		try {
			// see https://stackoverflow.com/a/402771
			if (filePath.startsWith("jar")) {
				final URL jarUrl = new URL(filePath);
				final JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
				url = connection.getJarFileURL();
			} else if (!filePath.startsWith("http")) {
				url = (new File(filePath)).toURI().toURL();
			} else {
				url = new URL(filePath);
			}
		} catch (final ClassCastException | IOException e) {
			throw new IllegalArgumentException("Invalid path: "+ filePath);
		}
		loadOBJ(url, c);
	}

	/**
	 * Loads the contour meshes for the Allen Mouse Brain Atlas. It will simply
	 * make the mesh visible if has already been loaded.
	 *
	 * @throws IllegalArgumentException if Viewer is not available, i.e.,
	 *                                  {@link #getView()} is null
	 */
	public void loadMouseRefBrain() throws IllegalArgumentException {
		if (getOBJs().keySet().contains(ALLEN_MESH_LABEL)) {
			setVisible(ALLEN_MESH_LABEL, true);
			return;
		}
		final ClassLoader loader = Thread.currentThread().getContextClassLoader();
		final URL url = loader.getResource("meshes/" + ALLEN_MESH_LABEL);
		if (url == null)
			throw new IllegalArgumentException(ALLEN_MESH_LABEL + " not found");
		loadOBJ(url, getNonUserDefColor());
	}

	private Color getNonUserDefColor() {
		return (isDarkModeOn()) ? DEF_COLOR : INVERTED_DEF_COLOR;
	}

	private Color getDefColor() {
		return (defColor == null) ? getNonUserDefColor() : defColor; 
	}

	private void loadOBJ(final URL url, final Color color) throws IllegalArgumentException {
			final OBJFileLoaderPlus loader = new OBJFileLoaderPlus(url);
			if (!loader.compileModel()) {
				throw new IllegalArgumentException("Mesh could not be compiled. Invalid file?");
			}
			final RemountableDrawableVBO drawable = new RemountableDrawableVBO(loader);
			// drawable.setQuality(chart.getQuality());
			drawable.setColor(color);
			chart.add(drawable, false); // GLException if true
			final String label = loader.getLabel();
			plottedObjs.put(label, drawable);
			addItemToManager(label);
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
			final String title = (isSNTInstance()) ? " (SNT)" : "";
			initialize(chart, new Rectangle(800, 600), "Reconstruction Viewer" + title);
			if (includeManager) {
				manager = getManager();
				managerList.selectAll();
				manager.setVisible(true);
			}
			toFront();
		}

		public void replaceCurrentChart(final Chart chart) {
			this.chart = chart;
			canvas = (Component) chart.getCanvas();
			removeAll();
			add(canvas);
			//doLayout();
			revalidate();
			//update(getGraphics());
		}

		public JDialog getManager() {
			final JDialog dialog = new JDialog(this, "RV Controls");
			dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
			//dialog.setLocationRelativeTo(this);
			final java.awt.Point parentLoc = getLocation();
			dialog.setLocation(parentLoc.x + getWidth() + 5, parentLoc.y);
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
			setSize(new Dimension(bounds.width, bounds.height));
			AWTWindows.centerWindow(this);
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
		private final Searchable searchable;
		
		public ManagerPanel(final GuiUtils guiUtils) {
			super();
			this.guiUtils = guiUtils;
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			final JScrollPane scrollPane = new JScrollPane(managerList);
			searchable = new ListSearchable(managerList);
			searchable.setCaseSensitive(false);
			searchable.setFromStart(false);
			searchable.setSearchLabel("Find");
			managerList.setComponentPopupMenu(popupMenu());
			scrollPane.setWheelScrollingEnabled(true);
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
			buttonPanel.add(menuButton(GLYPH.SYNC, reloadMenu(), "Reload/Synchronize"));
			buttonPanel.add(menuButton(GLYPH.ATOM, addMenu(), "Scene Elements"));
			buttonPanel.add(menuButton(GLYPH.SLIDERS, optionsMenu(), "Customize"));
			return buttonPanel;
		}

		private JButton menuButton(final GLYPH glyph, final JPopupMenu menu, final String tooltipMsg) {
			final JButton button = new JButton(IconFactory.getButtonIcon(glyph));
			button.setToolTipText(tooltipMsg);
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
				} catch (final IllegalArgumentException ex) {
					guiUtils.error(ex.getMessage());
				}
			});
			reloadMenu.add(mi);
			mi = new JMenuItem("Reload Scene/Reset Bounds");
			mi.addActionListener(e -> {
				if (!sceneIsOK() && guiUtils.getConfirmation(
						"Scene was reloaded but some objects have invalid attributes. "//
						+ "Rebuild 3D Scene Completely?", "Rebuild Required")) {
					rebuild();
				} else {
					displayMsg("Scene reloaded");
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

		private JPopupMenu popupMenu() {
			final JMenuItem sort = new JMenuItem("Sort List", IconFactory.getMenuIcon(GLYPH.SORT));
			sort.addActionListener(e -> {
				final List<String> checkedLabels = getLabelsCheckedInManager();
				try {
					managerList.setValueIsAdjusting(true);
					managerModel.removeAllElements();
					plottedTrees.keySet().forEach(k -> {
						managerModel.addElement(k);
					});
					plottedObjs.keySet().forEach(k -> {
						managerModel.addElement(k);
					});
					managerModel.addElement(CheckBoxList.ALL_ENTRY);
				} finally {
					managerList.setValueIsAdjusting(false);
				}
				managerList.addCheckBoxListSelectedValues(checkedLabels.toArray());
			});

			// Select menu
			final JMenu selectMenu = new JMenu("Select");
			selectMenu.setIcon(IconFactory.getMenuIcon(GLYPH.POINTER));
			final JMenuItem selectMeshes = new JMenuItem("Meshes");
			selectMeshes.addActionListener(e -> selectRows(plottedObjs));
			selectMenu.add(selectMeshes);
			final JMenuItem selectTrees = new JMenuItem("Trees");
			selectTrees.addActionListener(e -> selectRows(plottedTrees));
			selectMenu.add(selectTrees);
			selectMenu.addSeparator();
			final JMenuItem selectNone = new JMenuItem("None");
			selectNone.addActionListener(e -> managerList.clearSelection());
			selectMenu.add(selectNone);

			// Hide menu
			final JMenu hideMenu = new JMenu("Hide");
			hideMenu.setIcon(IconFactory.getMenuIcon(GLYPH.EYE_SLASH));
			final JMenuItem hideMeshes = new JMenuItem("Meshes");
			hideMeshes.addActionListener(e -> retainVisibility(plottedTrees));
			hideMenu.add(hideMeshes);
			final JMenuItem hideTrees = new JMenuItem("Trees");
			hideTrees.addActionListener(e -> {
				setArborsDisplayed(getLabelsCheckedInManager(), false);
			});
			final JMenuItem hideSomas = new JMenuItem("Somas of Visible Trees");
			hideSomas.addActionListener(e -> displaySomas(false));
			hideMenu.add(hideSomas);
			final JMenuItem hideAll = new JMenuItem("All");
			hideAll.addActionListener(e -> managerList.selectNone());
			hideMenu.addSeparator();
			hideMenu.add(hideAll);

			// Show Menu
			final JMenu showMenu = new JMenu("Show");
			showMenu.setIcon(IconFactory.getMenuIcon(GLYPH.EYE));
			final JMenuItem showMeshes = new JMenuItem("Meshes");
			showMeshes.addActionListener(e -> managerList.addCheckBoxListSelectedValues(plottedObjs.keySet().toArray()));
			showMenu.add(showMeshes);
			final JMenuItem showTrees = new JMenuItem("Trees");
			showTrees.addActionListener(e -> managerList.addCheckBoxListSelectedValues(plottedTrees.keySet().toArray()));
			showMenu.add(showTrees);
			final JMenuItem showSomas = new JMenuItem("Somas of Visible Trees");
			showSomas.addActionListener(e -> displaySomas(true));
			showMenu.add(showSomas);
			final JMenuItem showAll = new JMenuItem("All");
			showAll.addActionListener(e -> managerList.selectAll());
			showMenu.add(showAll);

			final JMenuItem find = new JMenuItem("Find...", IconFactory.getMenuIcon(GLYPH.BINOCULARS));
			find.addActionListener(e -> {
				managerList.clearSelection();
				managerList.requestFocusInWindow();
				searchable.showPopup("");
			});

			final JPopupMenu pMenu = new JPopupMenu();
			pMenu.add(selectMenu);
			pMenu.add(showMenu);
			pMenu.add(hideMenu);
			pMenu.addSeparator();
			pMenu.add(find);
			pMenu.addSeparator();
			pMenu.add(sort);
			return pMenu;
		}

		private void displaySomas(final boolean displayed) {
			final List<String> labels = getLabelsCheckedInManager();
			if (labels.isEmpty()) {
				displayMsg("There are no visible reconstructions");
				return;
			}
			setSomasDisplayed(labels, displayed);
		}

		private void selectRows(final Map<String, ?> map) {
			final int[] indices = new int[map.keySet().size()];
			int i = 0;
			for (final String k : map.keySet()) {
				indices[i++] = managerModel.indexOf(k);
			}
			managerList.setSelectedIndices(indices);
		}

		private void retainVisibility(final Map<String, ?> map) {
			final List<String> selectedKeys = new ArrayList<>(getLabelsCheckedInManager());
			selectedKeys.retainAll(map.keySet());
			managerList.setSelectedObjects(selectedKeys.toArray());
		}

		protected List<String> getSelectedTrees() {
			return getSelectedKeys(plottedTrees, "reconstructions");
		}

		protected List<String> getSelectedMeshes() {
			return getSelectedKeys(plottedObjs, "meshes");
		}

		private List<String> getSelectedKeys(final Map<String, ?> map, final String mapDescriptor) {
			if (map.isEmpty()) {
				guiUtils.error("There are no loaded " + mapDescriptor + ".");
				return null;
			}
			final List<?> selectedKeys = managerList.getSelectedValuesList();
			final List<String> allKeys = new ArrayList<>(map.keySet());
			if (selectedKeys.isEmpty() || (selectedKeys.size() == 1 && selectedKeys.get(0) == CheckBoxList.ALL_ENTRY)) {
				if (!guiUtils.getConfirmation(
						"There are no " + mapDescriptor + " selected. " + "Apply changes to all " + mapDescriptor + "?",
						"Apply to All?")) {
					return null;
				}
				return allKeys;
			}
			allKeys.retainAll(selectedKeys);
			return allKeys;
		}

		private JPopupMenu optionsMenu() {
			final JPopupMenu optionsMenu = new JPopupMenu();
			final JMenu meshMenu = new JMenu("Meshes");
			meshMenu.setIcon(IconFactory.getMenuIcon(GLYPH.CUBE));
			optionsMenu.add(meshMenu);
			final JMenu recMenu = new JMenu("Trees");
			recMenu.setIcon(IconFactory.getMenuIcon(GLYPH.TREE));
			optionsMenu.add(recMenu);

			// Mesh customizations
			JMenuItem mi = new JMenuItem("Color...");
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedMeshes();
				if (keys == null) return;
				final java.awt.Color c = guiUtils.getColor("Mesh(es) Color", java.awt.Color.WHITE, "HSB");
				if (c == null) {
					return; // user pressed cancel
				}
				final Color color = fromAWTColor(c);
				for (final String label : keys) {
					plottedObjs.get(label).setColor(color);
				}
			});
			meshMenu.add(mi);
			mi = new JMenuItem("Change Transparency...");
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedMeshes();
				if (keys == null)
					return;
				final Double t = guiUtils.getDouble("Mesh Transparency (%)", "Transparency (%)", 95);
				if (t == null) {
					return; // user pressed cancel
				}
				final float fValue = 1 - (t.floatValue( ) / 100);
				if (Float.isNaN(fValue) || fValue <= 0 || fValue >= 1) {
					guiUtils.error("Invalid transparency value: Only ]0, 100[ accepted.");
					return;
				}
				for (final String label : keys) {
					plottedObjs.get(label).getColor().a = fValue;
				}
			});
			meshMenu.add(mi);

			// Tree customizations
			mi = new JMenuItem("Color...");
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees();
				if (keys == null || !okToApplyColor(keys)) return;
				final ColorRGB c = guiUtils.getColorRGB("Reconstruction(s) Color", java.awt.Color.RED, "HSB");
				if (c == null) {
					return; // user pressed cancel
				}
				applyColorToPlottedTrees(keys, c);
			});
			recMenu.add(mi);
			mi = new JMenuItem("Thickness...");
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees();
				if (keys == null) return;
				String msg = "<HTML><body><div style='width:500;'>"
						+ "Please specify a constant thickness to be applied "
						+ "to selected "+ keys.size() + " reconstruction(s).";
				if (isSNTInstance()) {
					msg += " This value will only affect how Paths are displayed "
							+ "in the Reconstruction Viewer.";
				}
				final Double thickness = guiUtils.getDouble(msg,
								"Path Thickness", getDefaultThickness());
				if (thickness == null) {
					return; // user pressed cancel
				}
				if (Double.isNaN(thickness) || thickness <= 0) {
					guiUtils.error("Invalid thickness value.");
					return;
				}
				applyThicknessToPlottedTrees(keys, thickness.floatValue());
			});
			recMenu.add(mi);
			recMenu.addSeparator();

			mi = new JMenuItem("Color Coding...");
			mi.addActionListener(e -> {
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("labels", getSelectedTrees());
				runCmd(ColorizeReconstructionCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			recMenu.add(mi);

			mi = new JMenuItem("Color Each Cell Uniquely...");
			mi.addActionListener(e -> {
				final List<String> keys = getSelectedTrees();
				if (keys == null || !okToApplyColor(keys)) return;
	
				final ColorRGB[] colors = SWCColor.getDistinctColors(keys.size());
				final int[] counter = new int[] { 0 };
				plottedTrees.forEach((k, shapeTree) -> {
					final Shape shape = shapeTree.get();
					if (keys.contains(k)) {
						for (int i = 0; i < shape.size(); i++) {
							if (shape.get(i) instanceof LineStrip) {
								((LineStrip) shape.get(i)).setColor(fromColorRGB(colors[counter[0]]));
							}
						}
						counter[0]++;
					}
				});
			});
			recMenu.add(mi);

			// Misc options
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
			mi.addActionListener(e -> keyController.showHelp(true));
			optionsMenu.add(mi);
			return optionsMenu;
		}

		private boolean okToApplyColor(final List<String> labelsOfselectedTrees) {
			if (!treesContainColoredNodes(labelsOfselectedTrees))
				return true;
			return guiUtils.getConfirmation("Some of the selected reconstructions "
					+ "seem to be color-coded. Apply homogeneous color nevertheless?", "Override Color Code?");
		}

		private JPopupMenu addMenu() {
			final JPopupMenu addMenu = new JPopupMenu();
			final JMenu legendMenu = new JMenu("Color Legends");
			final JMenu meshMenu = new JMenu("Meshes");
			meshMenu.setIcon(IconFactory.getMenuIcon(GLYPH.CUBE));
			final JMenu tracesMenu = new JMenu("Trees");
			tracesMenu.setIcon(IconFactory.getMenuIcon(GLYPH.TREE));
			addMenu.add(meshMenu);
			addMenu.add(tracesMenu);
			addMenu.addSeparator();
			addMenu.add(legendMenu);

			// Traces Menu
			JMenuItem mi = new JMenuItem("Import File...");
			mi.addActionListener(e -> {
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("importDir", false);
				runCmd(LoadReconstructionCmd.class, inputs, CmdWorker.DO_NOTHING);
			});			tracesMenu.add(mi);
			mi = new JMenuItem("Import Directory...");
			mi.addActionListener(e -> {
				final Map<String, Object> inputs = new HashMap<>();
				inputs.put("importDir", true);
				runCmd(LoadReconstructionCmd.class, inputs, CmdWorker.DO_NOTHING);
			});
			tracesMenu.add(mi);
			tracesMenu.addSeparator();
			mi = new JMenuItem("Import from MouseLight...");
			mi.addActionListener(e -> runCmd(MLImporterCmd.class, null, CmdWorker.DO_NOTHING));
			tracesMenu.add(mi);
			mi = new JMenuItem("Import from NeuroMorpho...");
			mi.addActionListener(e -> runCmd(NMImporterCmd.class, null, CmdWorker.DO_NOTHING));
			tracesMenu.add(mi);
			tracesMenu.addSeparator();
			mi = new JMenuItem("Remove All...");
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all reconstructions from scene?", "Remove All Reconstructions?")) {
					return;
				}
				getOuter().removeAll();
			});
			tracesMenu.add(mi);

			// Legend Menu
			mi = new JMenuItem("Add...");
			mi.addActionListener(e -> {
				runCmd(ColorizeReconstructionCmd.class, null, CmdWorker.DO_NOTHING);
			});
			legendMenu.add(mi);
			mi = new JMenuItem("Remove Last");
			mi.addActionListener(e -> removeColorLegends(true));
			legendMenu.add(mi);
			meshMenu.addSeparator();
			mi = new JMenuItem("Remove All...");
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all color legends from scene?", "Remove All Legends?")) {
					return;
				}
				removeColorLegends(false);
			});
			legendMenu.add(mi);

			// Meshes Menu
			mi = new JMenuItem("Import OBJ File(s)...");
			mi.addActionListener(e -> runCmd(LoadObjCmd.class, null, CmdWorker.DO_NOTHING));
			meshMenu.add(mi);
			mi = new JMenuItem("Load Allen Mouse Brain Atlas Contour");
			mi.addActionListener(e -> {
				if (getOBJs().keySet().contains(ALLEN_MESH_LABEL)) {
					guiUtils.error(ALLEN_MESH_LABEL + " is already loaded.");
					managerList.addCheckBoxListSelectedValue(ALLEN_MESH_LABEL, true);
					return;
				}
				loadMouseRefBrain();
				getOuter().validate();
			});
			meshMenu.add(mi);
			meshMenu.addSeparator();
			mi = new JMenuItem("Remove All...");
			mi.addActionListener(e -> {
				if (!guiUtils.getConfirmation("Remove all meshes from scene?", "Remove All Meshes?")) {
					return;
				}
				removeAllOBJs();
			});
			meshMenu.add(mi);
			return addMenu;
		}

		private void removeColorLegends(final boolean justLastOne) {
			final List<AbstractDrawable> allDrawables = chart.getScene().getGraph().getAll();
			final Iterator<AbstractDrawable> iterator = allDrawables.iterator();
			while(iterator.hasNext()) {
				final AbstractDrawable drawable = iterator.next();
				if (drawable != null && drawable.hasLegend() && drawable.isLegendDisplayed()) {
					iterator.remove();
					if (justLastOne) break;
				}
			}
			cBar = null;
			cBarShape = null;
		}

		private void runCmd(final Class<? extends Command> cmdClass, final Map<String, Object> inputs,
				final int cmdType) {
			if (cmdService == null) {
				guiUtils.error("This command requires Reconstruction Viewer to be aware of a Scijava Context");
				return;
			}
			SwingUtilities.invokeLater(() -> {
			(new CmdWorker(cmdClass, inputs, cmdType)).execute();});
		}
	}

	private TreePlot3D getOuter() {
		return this;
	}

	private class ShapeTree extends Shape {

		private static final float SOMA_SCALING_FACTOR = 2.5f;
		private static final float SOMA_SLICES = 15f; // Sphere default;

		private final Tree tree;
		private Shape treeSubShape;
		private AbstractWireframeable somaSubShape;

		public ShapeTree(final Tree tree) {
			super();
			this.tree = tree;
		}

		@Override
		public boolean isDisplayed() {
			return (super.isDisplayed())
					|| ((somaSubShape != null) && somaSubShape.isDisplayed())
					|| ((treeSubShape != null) && treeSubShape.isDisplayed());
		}

		@Override
		public void setDisplayed(boolean displayed) {
			setArborDisplayed(displayed);
			//setSomaDisplayed(displayed);
		}

		public void setSomaDisplayed(boolean displayed) {
			if (somaSubShape != null) somaSubShape.setDisplayed(displayed);
		}

		public void setArborDisplayed(boolean displayed) {
			if (treeSubShape != null) treeSubShape.setDisplayed(displayed);
		}

		public Shape get() {
			if (components == null || components.isEmpty()) assembleShape();
			return this;
		}

		private void assembleShape() {

			final List<LineStrip> lines = new ArrayList<>();
			final List<PointInImage> somaPoints = new ArrayList<>();
			final List<java.awt.Color> somaColors = new ArrayList<>();

			for (final Path p : tree.list()) {

				// Stash soma coordinates
				if (Path.SWC_SOMA == p.getSWCType()) {
					for (int i = 0; i < p.size(); i++) {
						final PointInImage pim = p.getPointInImage(i);
						pim.v = p.getNodeRadius(i);
						somaPoints.add(pim);
					}
					if (p.hasNodeColors()) {
						somaColors.addAll(Arrays.asList(p.getNodeColors()));
					} else {
						somaColors.add(p.getColor());
					}
					continue;
				}

				// Assemble arbor(s)
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

			// Group all lines into a Composite. BY default the composite
			// will have no wireframe color, to allow colors for Paths/
			// nodes to be reveleade. Once a wireframe color is explicit
			// set it will be applied to all the paths in the composite
			if (!lines.isEmpty()) {
				treeSubShape = new Shape();
				treeSubShape.setWireframeColor(null);
				treeSubShape.add(lines);
				add(treeSubShape);
			}
			assembleSoma(somaPoints, somaColors);
			if (somaSubShape != null) add(somaSubShape);
			//shape.setFaceDisplayed(true);
			//shape.setWireframeDisplayed(true);
		}

		private void assembleSoma(List<PointInImage> somaPoints, List<java.awt.Color> somaColors) {
			final Color color = fromAWTColor(SWCColor.average(somaColors));
			switch(somaPoints.size()) {
			case 0:
				SNT.log(tree.getLabel() + ": No soma attribute");
				somaSubShape = null;
				return;
			case 1:
				// single point soma: http://neuromorpho.org/SomaFormat.html
				final PointInImage sCenter = somaPoints.get(0);
				somaSubShape = sphere(sCenter, color);
				return;
			case 3 :
				// 3 point soma representation: http://neuromorpho.org/SomaFormat.html
				final PointInImage p1 = somaPoints.get(0);
				final PointInImage p2 = somaPoints.get(1);
				final PointInImage p3 = somaPoints.get(2);
				final Tube t1 = tube(p2, p1, color);
				final Tube t2 = tube(p1, p3, color);
				final Shape composite = new Shape();
				composite.add(t1);
				composite.add(t2);
				somaSubShape = composite;
				return;
			default:
				// just create a centroid sphere
				final PointInImage cCenter = PointInImage.average(somaPoints);
				somaSubShape = sphere(cCenter, color);
				return;
			}
		}
	
		private <T extends AbstractWireframeable & ISingleColorable> void setWireFrame(final T t, final float r, final Color color) {
			t.setColor(contrastColor(color).alphaSelf(0.4f));
			t.setWireframeColor(color.alphaSelf(0.8f));
			t.setWireframeWidth(Math.max(1f, r/SOMA_SLICES/3));
			t.setWireframeDisplayed(true);
		}

		private Tube tube(final PointInImage bottom, final PointInImage top, final Color color) {
			final Tube tube = new Tube();
			tube.setPosition(new Coord3d((bottom.x + top.x) / 2, (bottom.y + top.y) / 2, (bottom.z + top.z) / 2));
			final float height = (float) bottom.distanceTo(top);
			tube.setVolume((float) bottom.v, (float) top.v, height);
			return tube;
		}

		private Sphere sphere(final PointInImage center, final Color color) {
			final Sphere s = new Sphere();
			s.setPosition(new Coord3d(center.x, center.y, center.z));
			final float radius = (float) Math.max(center.v, SOMA_SCALING_FACTOR * defThickness);
			s.setVolume(radius);
			setWireFrame(s, radius, color);
			return s;
		}

		public void rebuildShape() {
			if (isDisplayed()) {
				assembleShape();
				chart.removeDrawable(this, viewUpdatesEnabled);
				chart.add(this, viewUpdatesEnabled);
			}
		}

		public void setThickness(final float thickness) {
			treeSubShape.setWireframeWidth(thickness);
		}

		private void setArborColor(final ColorRGB color) {
			setArborColor(fromColorRGB(color));
		}

		private void setArborColor(final Color color) {
			treeSubShape.setWireframeColor(color);
//			for (int i = 0; i < treeSubShape.size(); i++) {
//				((LineStrip) treeSubShape.get(i)).setColor(color);
//			}
		}

		private Color getArborWireFrameColor() {
			return (treeSubShape == null) ? null : treeSubShape.getWireframeColor();
		}

		private Color getSomaColor() {
			return (somaSubShape == null) ? null : somaSubShape.getWireframeColor();
		}

		private void setSomaColor(Color color) {
			if (somaSubShape != null)
				somaSubShape.setWireframeColor(color);
		}

		public void setSomaColor(final ColorRGB color) {
			setSomaColor(fromColorRGB(color));
		}

		public double[] colorize(final String measurement, final ColorTable colorTable) {
			final TreeColorMapper colorizer = new TreeColorMapper();
			colorizer.colorize(tree, measurement, colorTable);
			rebuildShape();
			return colorizer.getMinMax();
		}

		private Color contrastColor(final Color color) {
			final float factor = 0.75f;
			return new Color(factor - color.r, factor - color.g, factor - color.b);
		}

	}

	private class CmdWorker extends SwingWorker<Boolean, Object> {

		private static final int DO_NOTHING = 0;
		private static final int VALIDATE_SCENE = 1;

		private final Class<? extends Command> cmd;
		private final Map<String, Object> inputs;
		private final int type;

		public CmdWorker(final Class<? extends Command> cmd, 
				final Map<String, Object> inputs, final int type) {
			this.cmd = cmd;
			this.inputs = inputs;
			this.type = type;
		}

		@Override
		public Boolean doInBackground() {
			try {
				final Map<String, Object> input = new HashMap<>();
				input.put("recViewer", getOuter());
				if (inputs != null) input.putAll(inputs);
				cmdService.run(cmd, true, input).get();
				return true;
			} catch (final NullPointerException e1) {
				return false;
			} catch (InterruptedException | ExecutionException e2) {
				gUtils.error("Unfortunately an exception occured. See console for details.");
				SNT.error("Error", e2);
				return false;
			}
		}

		@Override
		protected void done() {
			boolean status = false;
			try {
				status = get();
				if (status) {
					switch (type) {
					case VALIDATE_SCENE:
						validate();
						break;
					case DO_NOTHING:
					default:
						break;
					}
				}
			} catch (final Exception ignored) {
				// do nothing
			}
		};
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
				mouseController.zoom(0.9f);
				break;
			case '-':
			case '_':
				mouseController.zoom(1.1f);
				break;
			default:
				switch (e.getKeyCode()) {
				case KeyEvent.VK_F1:
					showHelp(true);
					break;
				case KeyEvent.VK_DOWN:
					mouseController.rotateLive(new Coord2d(0f, -STEP));
					break;
				case KeyEvent.VK_UP:
					mouseController.rotateLive(new Coord2d(0f, STEP));
					break;
				case KeyEvent.VK_LEFT:
					mouseController.rotateLive(new Coord2d(-STEP, 0));
					break;
				case KeyEvent.VK_RIGHT:
					mouseController.rotateLive(new Coord2d(STEP, 0));
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

		private void setEnableDebugMode(final boolean enable) {
			if (enable){
				overlayAnnotation = new OverlayAnnotation(chart.getView());
				((AWTChart)chart).addRenderer(overlayAnnotation);
			} else {
				((AWTChart)chart).removeRenderer(overlayAnnotation);
				overlayAnnotation = null;
			}
			SNT.setDebugMode(enable);
		}

		private void toggleDarkMode() {
//			if (chart == null)
//				return;
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
			if (overlayAnnotation != null) overlayAnnotation.setForegroundColor(newForeground);

			// Apply foreground color to trees with background color
			plottedTrees.values().forEach(shapeTree -> {
				final Shape shape = shapeTree.get();
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
			sb.append("    <td>Toggle Debug <u>I</u>nfo</td>");
			sb.append("    <td>Press 'I'</td>");
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

	private class OverlayAnnotation extends CameraEyeOverlayAnnotation {

		private java.awt.Color color;
		private GLAnimatorControl control;

		public OverlayAnnotation(final View view) {
			super(view);
			control = ((IScreenCanvas)chart.getCanvas()).getAnimator();
			control.setUpdateFPSFrames(120, null);
			setForegroundColor(view.getAxe().getLayout().getMainColor());
		}

		private void setForegroundColor(final Color c) {
			color = new java.awt.Color(c.r, c.g, c.b);
		}

		@Override
		public void paint(final Graphics g, final int canvasWidth, final int canvasHeight) {
			final Graphics2D g2d = (Graphics2D) g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2d.setColor(color);
			g2d.drawString("View: " + view.getCamera().getEye(), 20, 20);
			g2d.drawString("FOV: " + view.getCamera().getRenderingSphereRadius(), 20, 40);
			g2d.drawString(control.getLastFPS()+" FPS", 20, 60);
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

	/**
	 * Sets the default color for rendering {@link Tree}s.
	 *
	 * @param color the new color. Note that this value only applies to Paths that
	 *              have no specified color and no colors assigned to its nodes
	 */
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
	 * Applies a constant thickness (line width) to a subset of plotted trees.
	 *
	 * @param labels    the Collection of keys specifying the subset of trees
	 * @param thickness the thickness
	 */
	protected void applyThicknessToPlottedTrees(final List<String> labels, final float thickness) {
		plottedTrees.forEach((k, shapeTree) -> {
			if (labels.contains(k)) shapeTree.setThickness(thickness);
		});
	}

	private void setArborsDisplayed(final Collection<String> labels, final boolean displayed) {
		plottedTrees.forEach((k, shapeTree) -> {
			if (labels.contains(k)) setArborDisplayed(k, displayed);
		});
	}

	private void setArborDisplayed(final String treeLabel, final boolean displayed) {
		final ShapeTree shapeTree = plottedTrees.get(treeLabel);
		if (shapeTree != null) shapeTree.setArborDisplayed(displayed);
	}

	public void setSomasDisplayed(final Collection<String> labels, final boolean displayed) {
		plottedTrees.forEach((k, shapeTree) -> {
			if (labels.contains(k)) setSomaDisplayed(k, displayed);
		});
	}

	private void setSomaDisplayed(final String treeLabel, final boolean displayed) {
		final ShapeTree shapeTree = plottedTrees.get(treeLabel);
		if (shapeTree != null) shapeTree.setSomaDisplayed(displayed);
	}

	/**
	 * Applies a color to a subset of plotted trees.
	 *
	 * @param labels the Collection of keys specifying the subset of trees
	 * @param color  the color
	 */
	protected void applyColorToPlottedTrees(final List<String> labels, final ColorRGB color) {
		plottedTrees.forEach((k, shapeTree) -> {
			if (labels.contains(k)) {
				shapeTree.setArborColor(color);
				shapeTree.setSomaColor(color);	
			}
		});
	}

	protected boolean treesContainColoredNodes(final List<String> labels) {
		Color refColor = null;
		for (final Map.Entry<String, ShapeTree> entry : plottedTrees.entrySet()) {
			if (labels.contains(entry.getKey())) {
				final Shape shape = entry.getValue().get();
				for (int i = 0; i < shape.size(); i++) {
					if (!(shape.get(i) instanceof LineStrip)) continue;
					final Color color = getNodeColor((LineStrip) shape.get(i));
					if (color == null) continue;
					if (refColor == null) {
						refColor = color;
						continue;
					}
					if (color.r != refColor.r || color.g != refColor.g || color.b != refColor.b)
						return true;
				}
			}
		}
		return false;
	}

	private Color getNodeColor(LineStrip lineStrip) {
		for (Point p : lineStrip.getPoints()) {
			if (p != null) return p.rgb;
		}
		return null;
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

	public boolean isSNTInstance() {
		return sntService !=null && sntService.isActive() && sntService.getUI() != null
				&& this.equals(sntService.getUI().getReconstructionViewer(false));
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) return true;
		if (o == null) return false;
		if (getClass() != o.getClass()) return false;
		return uuid.equals(((TreePlot3D)o).uuid);
	}

	/* IDE debug method */
	public static void main(final String[] args) throws InterruptedException {
		GuiUtils.setSystemLookAndFeel();
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final Tree tree = new Tree("/home/tferr/code/test-files/AA0100.swc");
		final TreeColorMapper colorizer = new TreeColorMapper(ij.getContext());
		colorizer.colorize(tree, TreeColorMapper.BRANCH_ORDER, ColorTables.ICE);
		final double[] bounds = colorizer.getMinMax();
		SNT.setDebugMode(true);
		final TreePlot3D jzy3D = new TreePlot3D(ij.context());
		jzy3D.addColorBarLegend(ColorTables.ICE, (float) bounds[0], (float) bounds[1]);
		jzy3D.add(tree);
		jzy3D.loadMouseRefBrain();
		jzy3D.show();
	}

}
