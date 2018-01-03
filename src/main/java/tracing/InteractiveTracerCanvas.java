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

package tracing;

import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import ij.IJ;
import ij.ImagePlus;
import ij.Menus;
import tracing.hyperpanes.MultiDThreePanes;
import tracing.util.SWCColor;

public class InteractiveTracerCanvas extends TracerCanvas {

	private static final long serialVersionUID = 1L;
	private final SimpleNeuriteTracer tracerPlugin;
	private PopupMenu pMenu;
	private CheckboxMenuItem toggleEditModeMenuItem;
	private CheckboxMenuItem togglePauseModeMenuItem;

	private double last_x_in_pane_precise = Double.MIN_VALUE;
	private double last_y_in_pane_precise = Double.MIN_VALUE;
	private boolean fillTransparent = false;
	private Path unconfirmedSegment;
	private Path currentPath;
	private boolean lastPathUnfinished;
	private boolean editMode; // convenience flag to monitor SNT's edit mode
	
	private Color temporaryColor;
	private Color unconfirmedColor;
	private Color fillColor;

	protected static String EDIT_MODE_LABEL = "Edit Mode";
	protected static String PAUSE_MODE_LABEL = "SNT Paused";


	protected InteractiveTracerCanvas(final ImagePlus imp, final SimpleNeuriteTracer plugin, final int plane,
		final PathAndFillManager pathAndFillManager) {
		super(imp, plugin, plane, pathAndFillManager);
		tracerPlugin = plugin;
		buildPpupMenu();
		super.disablePopupMenu(true); // so that handlePopupMenu is not triggered
		super.add(pMenu);
	}

	private void buildPpupMenu() { // We are extending ImageCanvas: we'll avoid swing components here
		pMenu = new PopupMenu();
		final AListener listener = new AListener();
		pMenu.add(menuItem(AListener.SELECT_NEAREST, listener));
		pMenu.addSeparator();
		togglePauseModeMenuItem = new CheckboxMenuItem(AListener.PAUSE_TOOGLE);
		togglePauseModeMenuItem.addItemListener(listener);
		pMenu.add(togglePauseModeMenuItem);
		pMenu.addSeparator();
		toggleEditModeMenuItem = new CheckboxMenuItem(AListener.EDIT_TOOGLE);
		toggleEditModeMenuItem.addItemListener(listener);
		pMenu.add(toggleEditModeMenuItem);
		pMenu.addSeparator();
		pMenu.add(menuItem(AListener.NODE_DELETE, listener));
		pMenu.add(menuItem(AListener.NODE_INSERT, listener));
		pMenu.add(menuItem(AListener.NODE_MOVE, listener));
		pMenu.add(menuItem(AListener.NODE_MOVE_Z, listener));
		if (Menus.getFontSize()!=0) pMenu.setFont(Menus.getFont());
	}

	private void showPopupMenu(int x, int y) {
		final Path activePath = tracerPlugin.getSingleSelectedPath();
		final boolean be = uiReadyForModeChange(NeuriteTracerResultsDialog.EDITING_MODE);
		toggleEditModeMenuItem.setEnabled(be);
		toggleEditModeMenuItem.setState(be && editMode);
		toggleEditModeMenuItem.setLabel((activePath != null) ? "Edit " + activePath.getName() : AListener.EDIT_TOOGLE);
		final boolean bp = uiReadyForModeChange(NeuriteTracerResultsDialog.PAUSED);
		togglePauseModeMenuItem.setEnabled(bp);
		togglePauseModeMenuItem.setState(bp && tracerPlugin.getUIState()==NeuriteTracerResultsDialog.PAUSED);
		for (int i = 6; i < pMenu.getItemCount(); i++) {
			// First 6 items: Select nearest, sep, Edit mode, sep, Pause mode, separator
			pMenu.getItem(i).setEnabled(editMode);
		}
		pMenu.show(this, x, y);
	}

	private MenuItem menuItem(final String cmdName, final ActionListener lstnr) {
		final MenuItem mi = new MenuItem(cmdName);
		mi.addActionListener(lstnr);
		return mi;
	}

	public void setFillTransparent(final boolean transparent) {
		this.fillTransparent = transparent;
		if (transparent && fillColor != null) setFillColor(SWCColor.alphaColor(fillColor, 50));
	}

	public void setPathUnfinished(final boolean unfinished) {
		this.lastPathUnfinished = unfinished;
	}

	public void setTemporaryPath(final Path path) {
		this.unconfirmedSegment = path;
	}

	public void setCurrentPath(final Path path) {
		this.currentPath = path;
	}

	private boolean uiReadyForModeChange(int mode) {
		return tracerPlugin.isReady() && (tracerPlugin
			.getUIState() == NeuriteTracerResultsDialog.WAITING_TO_START_PATH ||
			tracerPlugin.getUIState() == mode);
	}

	public void toggleJustNearSlices() {
		just_near_slices = !just_near_slices;
	}

	public void fakeMouseMoved(final boolean shift_pressed, final boolean join_modifier_pressed) {
		tracerPlugin.mouseMovedTo(last_x_in_pane_precise, last_y_in_pane_precise, plane, shift_pressed,
			join_modifier_pressed);
	}

	public void clickAtMaxPoint() {
		final int x = (int) Math.round(last_x_in_pane_precise);
		final int y = (int) Math.round(last_y_in_pane_precise);
		final int[] p = new int[3];
		tracerPlugin.findPointInStack(x, y, plane, p);
		SNT.debug("Clicking on x="+x + " y= "+ y + "on pane " + plane 
			+ " which corresponds to image position x="+ p[0] +", y="+ p[1] + " z="+ p[2]);
		tracerPlugin.clickAtMaxPoint(x, y, plane);
		tracerPlugin.setSlicesAllPanes(p[0], p[1], p[2]);
	}

	public void startShollAnalysis() {
		if (pathAndFillManager.anySelected()) {
			final double[] p = new double[3];
			tracerPlugin.findPointInStackPrecise(last_x_in_pane_precise, last_y_in_pane_precise, plane, p);
			final PointInImage pointInImage = pathAndFillManager.nearestJoinPointOnSelectedPaths(p[0], p[1], p[2]);
			final boolean autoCanvasActivationState = tracerPlugin.autoCanvasActivation;
			tracerPlugin.autoCanvasActivation = false;
			final ShollAnalysisDialog sd = new ShollAnalysisDialog(
				"Sholl analysis for tracing of " + tracerPlugin.getImagePlus().getTitle(), pointInImage.x,
				pointInImage.y, pointInImage.z, pathAndFillManager, tracerPlugin.getImagePlus());
			sd.toFront();
			sd.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosed(final WindowEvent e) {
					tracerPlugin.autoCanvasActivation = autoCanvasActivationState;
				}
			});
		} else {
			tracerPlugin.discreteMsg("You must have a path selected in order to start Sholl analysis");
		}
	}

	public void selectNearestPathToMousePointer(final boolean addToExistingSelection) {

		if (pathAndFillManager.size() == 0) {
			tracerPlugin.discreteMsg("There are no paths yet, so you can't select one with 'g'");
			return;
		}

		final double[] p = new double[3];
		tracerPlugin.findPointInStackPrecise(last_x_in_pane_precise, last_y_in_pane_precise, plane, p);

		final double diagonalLength = tracerPlugin.getStackDiagonalLength();

		/*
		 * Find the nearest point on any path - we'll select that path...
		 */

		final NearPoint np = pathAndFillManager.nearestPointOnAnyPath(p[0] * tracerPlugin.x_spacing,
			p[1] * tracerPlugin.y_spacing, p[2] * tracerPlugin.z_spacing, diagonalLength);

		if (np == null) {
			tracerPlugin.discreteMsg("No nearby path was found within " + diagonalLength + tracerPlugin.spacing_units + " of the pointer! (temporary and single-point paths were ignored)");
			return;
		}

		final Path path = np.getPath();
		tracerPlugin.selectPath(path, addToExistingSelection);

	}

	@Override
	public void mouseMoved(final MouseEvent e) {

		if (super.isEventsDisabled() || !tracerPlugin.isReady()) {
			super.mouseMoved(e);
			return;
		}

		final int rawX = e.getX();
		final int rawY = e.getY();

		last_x_in_pane_precise = myOffScreenXD(rawX);
		last_y_in_pane_precise = myOffScreenYD(rawY);

		final boolean mac = IJ.isMacintosh();

		boolean shift_key_down = (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
		final boolean joiner_modifier_down = mac ? ((e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0)
			: ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0);

		super.mouseMoved(e);

		if (!editMode && tracerPlugin.snapCursor && plane == MultiDThreePanes.XY_PLANE && !joiner_modifier_down && !shift_key_down) {
				final double[] p = new double[3];
				tracerPlugin.findSnappingPointInXYview(last_x_in_pane_precise, last_y_in_pane_precise, p);
				last_x_in_pane_precise = p[0];
				last_y_in_pane_precise = p[1];
				shift_key_down = true;
		}
		
		tracerPlugin.mouseMovedTo(last_x_in_pane_precise, last_y_in_pane_precise, plane, shift_key_down,
			joiner_modifier_down);
		
		if (editMode) {
			setCursor((tracerPlugin.getEditingNode()==-1) ? defaultCursor : handCursor);
		} else {
			setCursor(crosshairCursor);
		}
	
		
	}

	@Override
	public void mouseEntered(final MouseEvent e) {

		if (super.isEventsDisabled() || !tracerPlugin.isReady()) {
			super.mouseEntered(e);
			return;
		}
		if (tracerPlugin.autoCanvasActivation) imp.getWindow().toFront();
	}

	@Override
	public void mousePressed(final MouseEvent me) {
		final boolean ready = tracerPlugin.isReady();
		if (ready && me.isPopupTrigger()) {
			showPopupMenu(me.getX(), me.getY());
			me.consume();
			return;
		}

		if (tracerPlugin.panMode || isEventsDisabled() || !ready) {
			super.mousePressed(me);
			return;
		}
	}

	@Override
	public void mouseClicked(final MouseEvent e) {

		if (isEventsDisabled() || !tracerPlugin.isReady()) {
			super.mouseClicked(e);
			return;
		}

		final int currentState = tracerPlugin.getUI().getState();

		if (currentState == NeuriteTracerResultsDialog.LOADING || currentState == NeuriteTracerResultsDialog.SAVING
				|| currentState == NeuriteTracerResultsDialog.IMAGE_CLOSED) {

			// Do nothing

		} else if (currentState == NeuriteTracerResultsDialog.EDITING_MODE) {

			if (impossibleEdit(true)) return;
			update(getGraphics());

		} else if (currentState == NeuriteTracerResultsDialog.WAITING_FOR_SIGMA_POINT) {

			tracerPlugin.launchPaletteAround(myOffScreenX(e.getX()), myOffScreenY(e.getY()), imp.getZ() - 1);
			restoreDefaultCursor();

		} else if (currentState == NeuriteTracerResultsDialog.WAITING_FOR_SIGMA_CHOICE) {

			tracerPlugin.discreteMsg("You must close the sigma palette to continue");

		} else if (tracerPlugin.setupTrace) {

			final boolean join = IJ.isMacintosh() ? e.isAltDown() : e.isControlDown();

			if (tracerPlugin.snapCursor && !join && !e.isShiftDown()) {
				tracerPlugin.clickForTrace(last_x_in_pane_precise, last_y_in_pane_precise, plane, join);
			} else {
				tracerPlugin.clickForTrace(myOffScreenXD(e.getX()), myOffScreenYD(e.getY()), plane, join);
			}

		} else
			SNT.debug("BUG: No operation chosen");
	}

	private boolean impossibleEdit(final boolean displayError) {
		boolean invalid = !tracerPlugin.pathAndFillManager.isSelected(tracerPlugin.getEditingPath());
		if (invalid && displayError)
			tracerPlugin.discreteMsg("Editing path not selected");
		if (!invalid) {
			invalid = (tracerPlugin.getEditingNode() == -1);
			if (invalid && displayError)
				tracerPlugin.discreteMsg("No node selected");
		}
		return invalid;
	}

	private void redrawEditingPath() {
		redrawEditingPath(getGraphics2D(getGraphics()));
		repaint();
	}

	private void redrawEditingPath(final Graphics2D g) {
		tracerPlugin.getEditingPath().drawPathAsPoints(g, this, tracerPlugin);
	}

	@Override
	protected void drawOverlay(final Graphics2D g) {

		if (tracerPlugin.loading)
			return;

		final boolean drawDiametersXY = tracerPlugin.getDrawDiametersXY();
		final int sliceZeroIndexed = imp.getZ() - 1;
		int eitherSideParameter = eitherSide;
		if (!just_near_slices)
			eitherSideParameter = -1;

		final FillerThread filler = tracerPlugin.filler;
		if (filler != null) {
			filler.setDrawingColors(getFillColor(), getFillColor());
			filler.setDrawingThreshold(filler.getThreshold());
		}

		super.drawOverlay(g); // draw all paths, crosshair, etc.

		if (editMode && tracerPlugin.getEditingPath() != null) {
			redrawEditingPath(g);
			return; // no need to proceed: only editing path has been updated
		}

		// Now render temporary/incomplete paths
		final double spotDiameter = 2 * nodeDiameter();

		if (unconfirmedSegment != null) {
			unconfirmedSegment.drawPathAsPoints(this, g, getUnconfirmedPathColor(), plane, drawDiametersXY,
					sliceZeroIndexed,
					eitherSideParameter);
			if (unconfirmedSegment.endJoins != null) {
				final PathNode pn = new PathNode(unconfirmedSegment, unconfirmedSegment.size()-1, this);
				pn.setSize(spotDiameter);
				pn.draw(g, getUnconfirmedPathColor());
			}
		}

		final Path currentPathFromTracer = tracerPlugin.getCurrentPath();

		if (currentPathFromTracer != null) {
			currentPathFromTracer.drawPathAsPoints(this, g, getTemporaryPathColor(), plane, drawDiametersXY, sliceZeroIndexed,
				eitherSideParameter);

			if (lastPathUnfinished && currentPath.size() == 0) { // first point in path
				final PointInImage p = new PointInImage(tracerPlugin.last_start_point_x * tracerPlugin.x_spacing,
						tracerPlugin.last_start_point_y * tracerPlugin.y_spacing,
						tracerPlugin.last_start_point_z * tracerPlugin.z_spacing);
				p.onPath = currentPath;
				final PathNode pn = new PathNode(p, this);
				pn.setSize(spotDiameter);
				pn.draw(g, getUnconfirmedPathColor());
			}
		}

	}

	private void enableEditMode(boolean enable) {
		final boolean activate = enable && tracerPlugin.editModeAllowed(true);
		if (activate) {
			tracerPlugin.enableEditMode(true);
		} else {
			tracerPlugin.enableEditMode(false);
		}
	}

	public void setTemporaryPathColor(final Color color) {
		this.temporaryColor = color;
	}

	public void setUnconfirmedPathColor(final Color color) {
		this.unconfirmedColor = color;
	}

	public void setFillColor(final Color color) {
		this.fillColor = color;
	}

	public Color getTemporaryPathColor() {
		return (temporaryColor == null) ? Color.RED : temporaryColor;
	}

	public Color getUnconfirmedPathColor() {
		return (unconfirmedColor == null) ? Color.CYAN : unconfirmedColor;
	}

	public Color getFillColor() {
		if (fillColor == null) fillColor =  new Color(0, 128, 0);
		if (fillTransparent) fillColor = SWCColor.alphaColor(fillColor, 50);
		return fillColor;
	}

	/**
	 * This class implements implements ActionListeners for
	 * InteractiveTracerCanvas contextual menu.
	 */
	private class AListener implements ActionListener, ItemListener {

		public static final String SELECT_NEAREST = "Select Nearest Path  [G, Shift+G]";
		public static final String PAUSE_TOOGLE = "Pause Tracing";
		public static final String EDIT_TOOGLE = "Edit Path";
		private final static String NODE_DELETE = "Delete Active Node  [D, Backspace]";
		private final static String NODE_INSERT = "Insert New Node at Cursor Position  [I, Ins]";
		private final static String NODE_MOVE = "Move Active Node to Cursor Position  [M]";
		private final static String NODE_MOVE_Z = "Bring Active Node to current Z-plane  [B]";

		@Override
		public void itemStateChanged(final ItemEvent e) {
			if (e.getSource().equals(toggleEditModeMenuItem)) {
				enableEditMode(toggleEditModeMenuItem.getState());
			}
		else if (e.getSource().equals(togglePauseModeMenuItem))
			tracerPlugin.pause(togglePauseModeMenuItem.getState());
		}

		@Override
		public void actionPerformed(final ActionEvent e) {

			if (e.getActionCommand().equals(SELECT_NEAREST)) {
				final boolean add = ((e.getModifiers() & ActionEvent.SHIFT_MASK) >0);
				selectNearestPathToMousePointer(add);
			}
			else if (impossibleEdit(true)) return;

			if (e.getActionCommand().equals(NODE_DELETE)) {
				deleteEditingNode(true);
			}
			else if (e.getActionCommand().equals(NODE_INSERT)) {
				apppendLastPositionToEditingNode(true);
			}
			else if (e.getActionCommand().equals(NODE_MOVE))
			{
				moveEditingNodeToLastPosition(true);
			}
			else if (e.getActionCommand().equals(NODE_MOVE_Z))
			{
				assignLastZPositionToEditNode(true);
			}
			else {
				SNT.debug("Unexpectedly got an event from an unknown source: ");
				return;
			}
			
		}
	}
	
	protected boolean isEditMode() {
		return editMode;
	}
	
	protected void setEditMode(final boolean editMode) {
		this.editMode = editMode;
	}
	
	protected void deleteEditingNode(final boolean warnOnFailure) {
		if (impossibleEdit(warnOnFailure)) return;
		final Path editingPath = tracerPlugin.getEditingPath();
		if (editingPath.size() > 1) {
			editingPath.removeNode(editingPath.getEditableNodeIndex());
			redrawEditingPath();
		} else { // single point path
			tracerPlugin.getPathAndFillManager().deletePath(editingPath);
			tracerPlugin.detectEditingPath();
			tracerPlugin.repaintAllPanes();
		}
	}
	
	protected void apppendLastPositionToEditingNode(final boolean warnOnFailure) {
		if (impossibleEdit(warnOnFailure)) return;
		final Path editingPath = tracerPlugin.getEditingPath();
		final int editingNode = editingPath.getEditableNodeIndex();
		final double[] p = new double[3];
		tracerPlugin.findPointInStackPrecise(last_x_in_pane_precise, last_y_in_pane_precise, plane, p);
		editingPath.addNode(editingNode, new PointInImage(p[0], p[1], p[2]));
		editingPath.setEditableNode(editingNode + 1);
		redrawEditingPath();
		return;
	}

	protected void moveEditingNodeToLastPosition(final boolean warnOnFailure) {
		if (impossibleEdit(warnOnFailure)) return;
		final Path editingPath = tracerPlugin.getEditingPath();
		final int editingNode = editingPath.getEditableNodeIndex();
		final double[] p = new double[3];
		tracerPlugin.findPointInStackPrecise(last_x_in_pane_precise, last_y_in_pane_precise, plane, p);
		editingPath.moveNode(editingNode, new PointInImage(p[0]*tracerPlugin.x_spacing, p[1]*tracerPlugin.y_spacing, p[2]*tracerPlugin.z_spacing));
		redrawEditingPath();
		return;
	}
	
	protected void assignLastZPositionToEditNode(final boolean warnOnFailure) {
		if (impossibleEdit(warnOnFailure)) return;
		final Path editingPath = tracerPlugin.getEditingPath();
		final int editingNode = editingPath.getEditableNodeIndex();
		double newZ = editingPath.precise_z_positions[editingNode];
		switch (plane) {
			case MultiDThreePanes.XY_PLANE:
				newZ = (imp.getZ() - 1) * tracerPlugin.z_spacing;
				break;
			case MultiDThreePanes.XZ_PLANE:
				newZ = last_y_in_pane_precise * tracerPlugin.y_spacing;
				break;
			case MultiDThreePanes.ZY_PLANE:
				newZ = last_x_in_pane_precise * tracerPlugin.x_spacing;;
				break;
		}
		editingPath.moveNode(editingNode, new PointInImage(
			editingPath.precise_x_positions[editingNode],
			editingPath.precise_y_positions[editingNode], newZ));
		redrawEditingPath();
	}

}

