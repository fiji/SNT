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

import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.MouseInfo;
import java.awt.Point;
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
import tracing.gui.GuiUtils;
import tracing.hyperpanes.MultiDThreePanes;

public class InteractiveTracerCanvas extends TracerCanvas {

	private static final long serialVersionUID = 1L;

	private final Color transparentGreen = new Color(0, 128, 0, 128);
	private final SimpleNeuriteTracer tracerPlugin;
	private final GuiUtils guiUtils;
	private final PopupMenu pMenu;
	private CheckboxMenuItem toggleEditModeMenuItem;
	private CheckboxMenuItem togglePauseModeMenuItem;

	private double last_x_in_pane_precise = Double.MIN_VALUE;
	private double last_y_in_pane_precise = Double.MIN_VALUE;
	private boolean fillTransparent = false;
	private Path unconfirmedSegment;
	private Path currentPath;
	private boolean lastPathUnfinished;

	private Path editingPath;
	private int editingNode;
	private boolean editMode;
	private static String EDIT_MODE_LABEL = "Edit Mode";


	protected InteractiveTracerCanvas(final ImagePlus imp, final SimpleNeuriteTracer plugin, final int plane,
		final PathAndFillManager pathAndFillManager) {
		super(imp, plugin, plane, pathAndFillManager);
		tracerPlugin = plugin;
		guiUtils = new GuiUtils(this.getParent());
		pMenu = popupMenu();
		super.disablePopupMenu(true);
		super.add(pMenu);
	}

	private PopupMenu popupMenu() { // We are extending ImageCanvas: we'll avoid swing components here
		final PopupMenu pMenu = new PopupMenu();
		final AListener listener = new AListener();
		togglePauseModeMenuItem = new CheckboxMenuItem(AListener.PAUSE_TOOGLE);
		togglePauseModeMenuItem.addItemListener(listener);
		pMenu.add(togglePauseModeMenuItem);
		pMenu.addSeparator();
		toggleEditModeMenuItem = new CheckboxMenuItem(AListener.EDIT_TOOGLE);
		toggleEditModeMenuItem.addItemListener(listener);
		pMenu.add(toggleEditModeMenuItem);
		pMenu.add(menuItem(AListener.NODE_DELETE, listener));
		pMenu.add(menuItem(AListener.NODE_INSERT, listener));
		pMenu.add(menuItem(AListener.NODE_MOVE, listener));
		pMenu.add(menuItem(AListener.NODE_MOVE_Z, listener));
		return pMenu;
	}

	private void refreshPopupMenu() {
		for (int i = 2; i < pMenu.getItemCount(); i++) {
			// First 2 items : Edit mode & Pause mode toggles
			pMenu.getItem(i).setEnabled(editMode);
		}
	}

	private MenuItem menuItem(final String cmdName, final ActionListener lstnr) {
		final MenuItem mi = new MenuItem(cmdName);
		mi.addActionListener(lstnr);
		return mi;
	}

	public void setFillTransparent(final boolean transparent) {
		this.fillTransparent = transparent;
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

	private boolean editAllowed(final boolean warnUser) {
		final boolean uiReady = tracerPlugin
			.getUIState() == NeuriteTracerResultsDialog.WAITING_TO_START_PATH;
		if (warnUser && !uiReady) {
			displayError("You must finish current operation before editing a path");
			return false;
		}
		getEditingScope();
		final boolean pathExists = editingPath != null;
		if (warnUser && !pathExists) {
			displayError("You must select a single path in order to edit it");
			return false;
		}
		final boolean validPath = pathExists && !editingPath.getUseFitted();
		if (warnUser && !validPath) {
			displayError(
				"Only unfitted paths can be edited. Run \"Un-fit volume\" to proceed");
			return false;
		}
		final boolean editAllowed = uiReady && pathExists && validPath;
		editMode = editMode && editAllowed;
		return editAllowed;
	}

	public boolean isEditMode() {
		return editMode;
	}

	public void setEditMode(final boolean mode, final boolean assessConditions) {
		if (mode && !editAllowed(assessConditions)) return;
		editMode = mode;
		if (editMode) {
			setDrawCrosshairs(false);
			tracerPlugin.changeUIState(NeuriteTracerResultsDialog.EDITING_MODE);
		} else {
			setDrawCrosshairs(true);
			setCursorText(null);
			setCursorShape(null);
			tracerPlugin.changeUIState(NeuriteTracerResultsDialog.WAITING_TO_START_PATH);
			resetEdit();
		}
	}

	private void displayError(final String msg) {
		final Point loc = MouseInfo.getPointerInfo().getLocation();
		guiUtils.tempMsg(msg, loc.x, loc.y);
		return;
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
			tracerPlugin.floatingMsg("You must have a path selected in order to start Sholl analysis");
		}
	}

	private void getEditingScope() {
		if (pathAndFillManager.selectedPathsSet.size() == 1) {
			editingPath = tracerPlugin.getSelectedPaths().iterator().next();
			editingNode = editingPath.getNodeIndex((int)last_x_in_pane_precise, (int)last_y_in_pane_precise);
		} else {
			editingPath = null;
			editingNode = -1;
		}
	}

	public void selectNearestPathToMousePointer(final boolean addToExistingSelection) {

		if (pathAndFillManager.size() == 0) {
			tracerPlugin.floatingMsg("There are no paths yet, so you can't select one with 'g'");
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
			tracerPlugin.floatingMsg("BUG: No nearby path was found within " + diagonalLength + " of the pointer");
			return;
		}

		final Path path = np.getPath();

		/*
		 * FIXME: in fact shift-G for multiple selections doesn't work, since in
		 * ImageJ that's a shortcut for taking a screenshot. Holding down
		 * control doesn't work since that's already used to restrict the
		 * cross-hairs to the selected path. Need to find some way around this
		 * ...
		 */

		tracerPlugin.selectPath(path, addToExistingSelection);
	}

	@Override
	public void mouseMoved(final MouseEvent e) {

		if (super.isMouseEventsDisabled() || !tracerPlugin.isReady()) {
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

		if (editMode) {
			getEditingScope();
			super.setCursor((editingNode == -1)?defaultCursor:handCursor);
		} else {
			super.setCursor(crosshairCursor);
		}
		super.mouseMoved(e);
		{	if (tracerPlugin.snapCursor && plane == MultiDThreePanes.XY_PLANE && !joiner_modifier_down && !shift_key_down) {
				final double[] p = new double[3];
				tracerPlugin.findSnappingPointInXYview(last_x_in_pane_precise, last_y_in_pane_precise, p);
				last_x_in_pane_precise = p[0];
				last_y_in_pane_precise = p[1];
				shift_key_down = true;
			}
		}
		tracerPlugin.mouseMovedTo(last_x_in_pane_precise, last_y_in_pane_precise, plane, shift_key_down,
			joiner_modifier_down);
		
	}

	@Override
	public void mouseEntered(final MouseEvent e) {

		if (super.isMouseEventsDisabled() || !tracerPlugin.isReady()) {
			super.mouseEntered(e);
			return;
		}
		if (tracerPlugin.autoCanvasActivation) imp.getWindow().toFront();
	}

	@Override
	public void mousePressed(final MouseEvent me) {
		if (me.isPopupTrigger()) {
			toggleEditModeMenuItem.setEnabled(editMode || editAllowed(false));
			refreshPopupMenu();
			pMenu.show(this, me.getX(), me.getY());
			me.consume();
			return;
		}
		if (isMouseEventsDisabled() || !tracerPlugin.isReady()) {
			super.mousePressed(me);
			return;
		}
	}

	@Override
	public void mouseClicked(final MouseEvent e) {

		if (isMouseEventsDisabled() || !tracerPlugin.isReady()) {
			super.mouseClicked(e);
			return;
		}

		final int currentState = tracerPlugin.getUI().getState();

		if (currentState == NeuriteTracerResultsDialog.LOADING || currentState == NeuriteTracerResultsDialog.SAVING
				|| currentState == NeuriteTracerResultsDialog.IMAGE_CLOSED) {

			// Do nothing

		} else if (currentState == NeuriteTracerResultsDialog.EDITING_MODE) {

			if (impossibleEdit(false)) return;
			editingPath.setEditableNode(editingNode);
			update(getGraphics());

		} else if (currentState == NeuriteTracerResultsDialog.WAITING_FOR_SIGMA_POINT) {

			tracerPlugin.launchPaletteAround(myOffScreenX(e.getX()), myOffScreenY(e.getY()), imp.getZ() - 1);
			restoreDefaultCursor();

		} else if (currentState == NeuriteTracerResultsDialog.WAITING_FOR_SIGMA_CHOICE) {

			tracerPlugin.floatingMsg("You must close the sigma palette to continue");

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

	private void drawSquare(final Graphics g, final PointInImage p, final Color fillColor, final Color edgeColor,
		final int side) {

		int x, y;

		if (plane == MultiDThreePanes.XY_PLANE) {
			x = myScreenXD(p.x / tracerPlugin.x_spacing);
			y = myScreenYD(p.y / tracerPlugin.y_spacing);
		} else if (plane == MultiDThreePanes.XZ_PLANE) {
			x = myScreenXD(p.x / tracerPlugin.x_spacing);
			y = myScreenYD(p.z / tracerPlugin.z_spacing);
		} else { // MultiDThreePanes.ZY_PLANE
			x = myScreenXD(p.z / tracerPlugin.z_spacing);
			y = myScreenYD(p.y / tracerPlugin.y_spacing);
		}

		final int rectX = x - side / 2;
		final int rectY = y - side / 2;

		g.setColor(fillColor);
		g.fillRect(rectX, rectY, side, side);

		if (edgeColor != null) {
			g.setColor(edgeColor);
			g.drawRect(rectX, rectY, side, side);
		}
	}

	private boolean impossibleEdit(final boolean displayError) {
		boolean invalid = (editingPath == null || editingNode == -1);
		if (invalid && displayError) displayError("No node selected");
		return invalid;
	}

	private void resetEdit() {
		editingNode = -1;
		if (editingPath !=null) {
			editingPath.setEditableNode(-1);
			repaint();
		}
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
			filler.setDrawingColors(fillTransparent ? transparentGreen : Color.GREEN,
				fillTransparent ? transparentGreen : Color.GREEN);
			filler.setDrawingThreshold(filler.getThreshold());
		}

		if (editMode) {
			final int edge = 4;
			final Font font = new Font("SansSerif", Font.PLAIN, 18).deriveFont((float) (18*magnification));
			final FontMetrics fm = getFontMetrics(font);
			g.setFont(font);
			g.setColor(getCursorAnnotationsColor());
			g.drawString(EDIT_MODE_LABEL, edge, edge + fm.getHeight());
		}
	
		super.drawOverlay(g);

		final double magnification = getMagnification();
		int pixel_size = magnification < 1 ? 1 : (int) magnification;
		if (magnification >= 4)
			pixel_size = (int) (magnification / 2);

		final int spotDiameter = 5 * pixel_size;

		if (unconfirmedSegment != null) {
			unconfirmedSegment.drawPathAsPoints(this, g, Color.BLUE, plane, drawDiametersXY, sliceZeroIndexed,
				eitherSideParameter);

			if (unconfirmedSegment.endJoins != null) {
				final int n = unconfirmedSegment.size();
				final PointInImage p = unconfirmedSegment.getPointInImage(n - 1);
				drawSquare(g, p, Color.BLUE, Color.GREEN, spotDiameter);
			}
		}

		final Path currentPathFromTracer = tracerPlugin.getCurrentPath();

		if (currentPathFromTracer != null) {
			currentPathFromTracer.drawPathAsPoints(this, g, Color.RED, plane, drawDiametersXY, sliceZeroIndexed,
				eitherSideParameter);

			if (lastPathUnfinished && currentPath.size() == 0) {

				final PointInImage p = new PointInImage(tracerPlugin.last_start_point_x * tracerPlugin.x_spacing,
					tracerPlugin.last_start_point_y * tracerPlugin.y_spacing,
					tracerPlugin.last_start_point_z * tracerPlugin.z_spacing);

				Color edgeColour = null;
				if (currentPathFromTracer.startJoins != null)
					edgeColour = Color.GREEN;

				//FIXME:drawSquare(g, p, Color.BLUE, edgeColour, spotDiameter);
			}
		}

	}


	/**
	 * This class implements implements ActionListeners for
	 * InteractiveTracerCanvas contextual menu.
	 */
	private class AListener implements ActionListener, ItemListener {

		public static final String PAUSE_TOOGLE = "Pause Tracing";
		public static final String EDIT_TOOGLE = "Allow Node Editing";
		private final static String NODE_CONNECT = "Connect To...";
		private final static String NODE_DELETE = "Delete Selected";
		private final static String NODE_INSERT = "Insert Node Here";
		private final static String NODE_MOVE = "Move Selected";
		private final static String NODE_MOVE_Z = "Assign Current Plane";

		@Override
		public void itemStateChanged(final ItemEvent e) {
			setEditMode(toggleEditModeMenuItem.getState(), true);
			disableMouseEvents(togglePauseModeMenuItem.getState());
		}

		@Override
		public void actionPerformed(final ActionEvent e) {

			if (impossibleEdit(true)) return;

			if (e.getActionCommand().equals(NODE_DELETE)) {
				editingPath.removeNode(editingNode);
			}

			else if (e.getActionCommand().equals(NODE_INSERT))
			{
				System.out.println("TBD "+ e.getActionCommand());
				return;
			}
			else if (e.getActionCommand().equals(NODE_MOVE))
			{
				System.out.println("TBD "+ e.getActionCommand());
				return;
			}
			else if (e.getActionCommand().equals(NODE_MOVE_Z))
			{

				double newZ = editingPath.precise_z_positions[editingNode];
				switch (plane) {
					case MultiDThreePanes.XY_PLANE:
						newZ = (imp.getZ() - 1) * editingPath.z_spacing;
						break;
					case MultiDThreePanes.XZ_PLANE:
						newZ = last_y_in_pane_precise;
						break;
					case MultiDThreePanes.ZY_PLANE:
						newZ = last_x_in_pane_precise;
						break;
				}
				editingPath.moveNode(editingNode, new PointInImage(
					editingPath.precise_x_positions[editingNode],
					editingPath.precise_y_positions[editingNode], newZ));
			}

			else {
				SNT.debug("Unexpectedly got an event from an unknown source: ");
				return;
			}
			resetEdit();
		}
	}

}

