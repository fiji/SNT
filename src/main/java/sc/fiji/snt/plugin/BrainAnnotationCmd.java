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

package sc.fiji.snt.plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import net.imagej.ImageJ;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.NodeStatistics;
import sc.fiji.snt.analysis.SNTChart;
import sc.fiji.snt.analysis.TreeStatistics;
import sc.fiji.snt.annotation.BrainAnnotation;
import sc.fiji.snt.gui.cmds.CommonDynamicCmd;
import sc.fiji.snt.io.MouseLightLoader;
import sc.fiji.snt.util.PointInImage;

/**
 * Command to perform distribution analysis of {@link BrainAnnotation}s on an
 * annotated {@link Tree}.
 *
 * @author Tiago Ferreira
 */
@Plugin(type = Command.class, visible = false, label = "Distribution Analysis of Brain Areas", initializer = "init")
public class BrainAnnotationCmd extends CommonDynamicCmd {

	@Parameter(label = "Type of distribution:", choices = { "Cable Length", "No. of Tips",
			"Cable Length & No.of Tips" })
	private String histogramType;

	@Parameter(required = false, label = "Deepest ontology:",
			description = "<HTML><div WIDTH=400>The Deepest ontology depth to be considered. As a reference, the deepest level for "
					+ "several mouse brain atlases is around 10. Set it to 0 to consider all depths", min ="0")
	private int ontologyDepth;

	@Parameter(required = false, label = "Compartment", choices = { "All", "Axon", "Dendrites" })
	private String compartment;

	@Parameter(required = true)
	private Tree tree;

	private SNTChart hist;
	private String treeLabel;

	@SuppressWarnings("unused")
	private void init() {
		if (tree == null || tree.isEmpty()) {
			cancel("No reconstruction specified.");
			return;
		}
		treeLabel = (tree.getLabel() == null) ? "Reconstruction" : tree.getLabel();
		if (!tree.isAnnotated()) {
			cancel(treeLabel + " has no neuropil labels.");
			return;
		}
		if (tree.getSWCTypes().size() == 1) {
			resolveInput(compartment);
			compartment = "All";
		}
	}

	@Override
	public void run() {

		if (histogramType == null) {
			cancel("Distribution type was not specified");
			return;
		}

		statusService.showStatus("Retrieving soma annotation...");
		BrainAnnotation somaAnnot;
		String somaLabel = "soma";
		final List<PointInImage> sNodes = tree.getSomaNodes();
		if (sNodes == null) {
			somaAnnot = tree.getRoot().getAnnotation();
			somaLabel = "root";
		} else {
			somaAnnot = sNodes.get(0).getAnnotation();
		}
		if (somaAnnot != null && somaAnnot.getOntologyDepth() > ontologyDepth)
			somaAnnot = somaAnnot.getAncestor(ontologyDepth - somaAnnot.getOntologyDepth());

		if (compartment != null && !compartment.toLowerCase().contains("all")) {
			statusService.showStatus("Retrieving " + compartment + " arbor...");
			tree = tree.subTree(compartment);
			if (tree.isEmpty()) {
				cancel(treeLabel + " does not contain processes tagged as \"" + compartment + "\".");
				return;
			}
		}

		statusService.showStatus("Classifying reconstruction...");
		final TreeStatistics tStats = new TreeStatistics(tree);
		if (histogramType.toLowerCase().contains("length")) {
			hist = tStats.getAnnotatedLengthHistogram(ontologyDepth <= 0 ? Integer.MAX_VALUE : ontologyDepth);
			annotateSoma(hist, somaAnnot, somaLabel);
			hist.annotate("Total cable length: " + tStats.getCableLength());
			hist.show();
		}
		if (histogramType.toLowerCase().contains("tips")) {
			final Set<PointInImage> tips = tStats.getTips();
			final NodeStatistics<?> nStats = new NodeStatistics<>(tips);
			hist = nStats.getAnnotatedHistogram(ontologyDepth <= 0 ? Integer.MAX_VALUE : ontologyDepth);
			annotateSoma(hist, somaAnnot, somaLabel);
			hist.annotate("No. of tips: " + tips.size());
			hist.show();
		}

		statusService.clearStatus();

	}

	private void annotateSoma(final SNTChart hist, final BrainAnnotation sAnnotation, final String sLabel) {
		if (sAnnotation != null)
			hist.annotateCategory(sAnnotation.acronym(), sLabel, "blue");
	}

	public static void main(final String[] args) {
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		final MouseLightLoader loader = new MouseLightLoader("AA0360");
		final HashMap<String, Object> input = new HashMap<>();
		input.put("tree", loader.getTree());
		final CommandService cmdService = ij.context().getService(CommandService.class);
		cmdService.run(BrainAnnotationCmd.class, true, input);
	}
}