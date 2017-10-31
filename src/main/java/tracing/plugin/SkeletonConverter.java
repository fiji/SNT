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

package tracing.plugin;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Vector;
import java.util.stream.IntStream;

import org.apache.commons.math3.stat.StatUtils;

import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import sc.fiji.analyzeSkeleton.AnalyzeSkeleton_;
import sc.fiji.analyzeSkeleton.SkeletonResult;
import sc.fiji.skeletonize3D.Skeletonize3D_;
import tracing.Path;
import tracing.PathAndFillManager;
import tracing.SimpleNeuriteTracer;
import tracing.gui.GuiUtils;

/**
 * Convenience class for converting SNT paths into skeletonized images.
 *
 * @author Tiago Ferreira
 */
public class SkeletonConverter implements DialogListener {

	private GenericDialog gd;
	private boolean restrictByRoi;
	private boolean restrictBySWCType;
	private boolean callAnalyzeSkeleton;
	private boolean summarizeSkeleton;
	private ArrayList<Integer> selectedSwcTypes;
	private ArrayList<Path> renderingPaths;

	private final SimpleNeuriteTracer plugin;
	private final PathAndFillManager pafm;
	private final ImagePlus imp;
	private Roi roi;

	public SkeletonConverter(final SimpleNeuriteTracer plugin) {
		this.plugin = plugin;
		pafm = plugin.getPathAndFillManager();
		imp = plugin.getImagePlus();
	}

	/** Runs SNT's 'Convert To Skeletons' dialog. */
	public void runGui() {

		selectedSwcTypes = new ArrayList<>();
		renderingPaths = new ArrayList<>();

		final GuiUtils gUtils = new GuiUtils(plugin.getUI().getPathWindow());
		if (!pafm.anySelected()) {
			gUtils.error("No paths selected.");
			return;
		}

		if (!showDialog())
			return;

		roi = imp.getRoi();
		if (restrictByRoi && (roi == null || !roi.isArea())) {
			if (!gUtils.getConfirmation(
				"ROI filtering requested but image contains no area ROI.\n" +
					"Proceed without ROI filtering?", "Proceed Without ROI Filtering?"))
				return;
			restrictByRoi = false;
		}

		if (selectedSwcTypes.isEmpty())
			restrictBySWCType = false;

		for (Path p : pafm.getPaths()) {
			if (p.getUseFitted())
				p = p.getFitted();
			else if (p.isFittedVersionOfAnotherPath())
				continue;
			if (restrictBySWCType && !selectedSwcTypes.contains(p.getSWCType()))
				continue;
			renderingPaths.add(p);
		}

		if (renderingPaths.isEmpty()) {
			gUtils.error("No paths fullfilled the selected criteria.");
			return;
		}

		final ImagePlus imagePlus = plugin.makePathVolume(renderingPaths);
		if (restrictByRoi && roi != null && roi.isArea()) {
			final ImageStack stack = imagePlus.getStack();
			for (int i = 1; i <= stack.getSize(); i++) {
				final ImageProcessor ip = stack.getProcessor(i);
				ip.setValue(0);
				ip.fillOutside(roi);
			}
			imagePlus.setRoi(roi);
		}

		if (callAnalyzeSkeleton || summarizeSkeleton) {
			final Skeletonize3D_ skeletonizer = new Skeletonize3D_();
			skeletonizer.setup("", imagePlus);
			skeletonizer.run(imagePlus.getProcessor());
			final AnalyzeSkeleton_ analyzer = new AnalyzeSkeleton_();
			analyzer.setup("", imagePlus);
			if (callAnalyzeSkeleton)
				analyzer.run(imagePlus.getProcessor());
			else
				summarizeSkeleton(analyzer.run());
		}

		imagePlus.show();

	}

	private void summarizeSkeleton(final SkeletonResult sr) {
		final String TABLE_TITLE = "Skeletonized Paths Summary";
		final ResultsTable rt = getTable(TABLE_TITLE);
		try {
			double sumLength = 0d;
			final int[] branches = sr.getBranches();
			final double[] avgLengths = sr.getAverageBranchLength();
			for (int i = 0; i < sr.getNumOfTrees(); i++)
				sumLength += avgLengths[i] * branches[i];
			rt.incrementCounter();
			rt.addValue("N. Converted Paths", renderingPaths.size());
			rt.addValue("Unit", imp.getCalibration().getUnits());
			rt.addValue("Total length", sumLength);
			rt.addValue("Mean branch length", StatUtils.mean(avgLengths));
			rt.addValue("Length of longest branch", StatUtils.max(sr.getMaximumBranchLength()));
			rt.addValue("# Branches", IntStream.of(sr.getBranches()).sum());
			rt.addValue("# Junctions", IntStream.of(sr.getJunctions()).sum());
			rt.addValue("# End-points", IntStream.of(sr.getEndPoints()).sum());
			rt.addValue("Fitering", getFilterString());
			if (restrictByRoi && roi != null && roi.isArea())
				rt.addValue("ROI Name", roi.getName() == null ? "Unammed ROI" : roi.getName());

		} catch (final Exception ignored) {
			GuiUtils.errorPrompt("Some statistics could not be calculated.");
		} finally {
			rt.show(TABLE_TITLE);
		}
	}

	private String getFilterString() {
		final StringBuilder filter = new StringBuilder();
		if (restrictByRoi && roi != null && roi.isArea())
			filter.append("OnlyPointsInsideROI");
		if (restrictBySWCType) {
			filter.append(" [ ");
			for (final int type : selectedSwcTypes)
				filter.append(Path.getSWCtypeName(type)).append(" ");
			filter.append("]");
		}
		if (filter.length() == 0)
			filter.append("None");
		return filter.toString();
	}

	private ResultsTable getTable(final String title) {
		ResultsTable rt = null;
		final Window window = WindowManager.getWindow(title);
		if (window != null)
			rt = ((TextWindow) window).getTextPanel().getResultsTable();
		if (rt == null)
			rt = new ResultsTable();
		rt.setPrecision(5);
		rt.setNaNEmptyCells(true);
		rt.showRowNumbers(false);
		return rt;
	}

	private boolean showDialog() {

		gd = new GenericDialog("Convert to Topographic Skeletons");
		final String[] roiScopes = { "None", "Convert only segments contained by ROI" };
		gd.addRadioButtonGroup("ROI filtering:", roiScopes, 2, 1, roiScopes[restrictByRoi ? 1 : 0]);

		// Assemble SWC choices
		final ArrayList<String> swcTypeNames = Path.getSWCtypeNames();
		final int nTypes = swcTypeNames.size();
		final String[] typeNames = swcTypeNames.toArray(new String[nTypes]);
		final boolean[] typeChoices = new boolean[nTypes];
		for (int i = 0; i < nTypes; i++)
			typeChoices[i] = typeNames[i].contains("dendrite");
		final String[] swcScopes = { "None", "Include only the following SWC types:" };
		gd.addRadioButtonGroup("SWC filtering:", swcScopes, 2, 1, swcScopes[restrictBySWCType ? 1 : 0]);
		gd.setInsets(0, 40, 0);
		gd.addCheckboxGroup(nTypes / 2, 2, typeNames, typeChoices);

		final String[] analysisScopes = { "None", "Obtain summary", "Run \"Analyze Skeleton\" plugin" };
		gd.addRadioButtonGroup("Analysis of converted paths:", analysisScopes, 3, 1, analysisScopes[0]);
		gd.addDialogListener(this);
		dialogItemChanged(gd, null);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		else if (gd.wasOKed()) {
			return dialogItemChanged(gd, null);
		}
		return false;

	}

	@Override
	public boolean dialogItemChanged(final GenericDialog arg, final AWTEvent event) {

		restrictByRoi = gd.getNextRadioButton().contains("only");
		restrictBySWCType = gd.getNextRadioButton().contains("only");
		final String analysisChoice = gd.getNextRadioButton();
		summarizeSkeleton = analysisChoice.contains("summary");
		callAnalyzeSkeleton = analysisChoice.contains("Analyze Skeleton");
		if (restrictBySWCType) {
			selectedSwcTypes.clear();
			for (final int type : Path.getSWCtypes()) {
				if (gd.getNextBoolean())
					selectedSwcTypes.add(type);
			}
		}
		final Vector<?> cbxs = gd.getCheckboxes();
		for (int i = 0; i < cbxs.size(); i++)
			((Checkbox) cbxs.get(i)).setEnabled(restrictBySWCType);

		return true;
	}

}
