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

package sc.fiji.snt.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ij.plugin.filter.MaximumFinder;
import net.imagej.ImageJ;
import sc.fiji.snt.SNTService;
import sc.fiji.snt.SNTUtils;
import sc.fiji.snt.Tree;
import sc.fiji.snt.analysis.sholl.TreeParser;
import sholl.UPoint;
import sholl.math.LinearProfileStats;
import sholl.math.NormalizedProfileStats;

/**
 * Class to retrieve Sholl metrics from a {@link Tree}.
 *
 * @author Tiago Ferreira
 */
public class ShollAnalyzer {

	static final String MEAN = "Mean";
	static final String MEDIAN = "Median";
	static final String MAX = "Max";
	static final String MAX_FITTED = "Max (fitted)";
	static final String MAX_FITTED_RADIUS = "Max (fitted) radius";
	static final String POLY_FIT_DEGREE = "Degree of Polynomial fit";
	static final String N_MAX = "No. maxima";
	static final String CENTROID = "Centroid";
	static final String CENTROID_RADIUS = "Centroid radius";
	static final String SUM = "Sum";
	static final String VARIANCE = "Variance";
	static final String SKEWENESS = "Skeweness";
	static final String KURTOSIS = "Kurtosis";
	static final String N_SECONDARY_MAX = "No. secondary maxima";
	static final String MAX_RADIUS_PREFIX = "Max radius";
	static final String SECONDARY_MAX_PREFIX = "Secondary max";
	static final String SECONDARY_MAX_RADIUS_PREFIX = "Secondary max radius";
	static final String ENCLOSING_RADIUS = "Enclosing radius";
	static final String DECAY = "Decay";
	static final String INTERCEPT = "Intercept";

	static final String[] ALL_FLAGS = { //
			CENTROID, //
			CENTROID_RADIUS, //
			DECAY, //
			ENCLOSING_RADIUS, //
			INTERCEPT, //
			KURTOSIS, //
			MAX, //
			MAX_FITTED, //
			MAX_FITTED_RADIUS, //
			MEAN, //
			MEDIAN, //
			N_MAX, //
			N_SECONDARY_MAX, //
			POLY_FIT_DEGREE, //
			SKEWENESS, //
			SUM, //
			VARIANCE, //
	};

	private final Tree tree;
	private final LinkedHashMap<String, Number> metrics;
	private final ArrayList<Double> maximaRadii;
	private final ArrayList<double[]> secondaryMaxima;;

	private NormalizedProfileStats nStats;
	private LinearProfileStats lStats;
	private final TreeParser parser;

	public ShollAnalyzer(final Tree tree) {
		this.tree = tree;
		parser = new TreeParser(tree);
		parser.setCenter(tree.getRoot());
		parser.setStepSize(0);
		metrics = new LinkedHashMap<>();
		maximaRadii = new ArrayList<>();
		secondaryMaxima = new ArrayList<>();
	}

	public static List<String> getMetrics() {
		return Arrays.stream(ALL_FLAGS).collect(Collectors.toList());
	}

	public Map<String, Number> getSingleValueMetrics() {
		return getSingleValueMetrics(true);
	}

	private Map<String, Number> getSingleValueMetrics(final boolean includeFitting) {

		if (!metrics.isEmpty()) {
			return metrics;
		}

		getLinearStats();
		if (lStats != null) {
			if (includeFitting) {
				final int bestFit = lStats.findBestFit(2, 20, 0.80, -1);
				if (lStats.validFit()) {
					final UPoint fMax = lStats.getCenteredMaximum(true);
					metrics.put(MAX_FITTED, fMax.y);
					metrics.put(MAX_FITTED_RADIUS, fMax.x);
					metrics.put(POLY_FIT_DEGREE, bestFit);
				}
			}
			metrics.put(MEAN, lStats.getMean());
			metrics.put(MEAN, lStats.getMean());
			metrics.put(SUM, lStats.getSum());
			metrics.put(MAX, lStats.getMax());
			metrics.put(N_MAX, getMaximaRadii().size());
			metrics.put(N_SECONDARY_MAX, getSecondaryMaxima().size());
			final UPoint centroid = lStats.getCentroid();
			metrics.put(CENTROID, centroid.y);
			metrics.put(CENTROID_RADIUS, centroid.x);
			metrics.put(MEDIAN, lStats.getMedian());
			metrics.put(KURTOSIS, lStats.getKurtosis());
			metrics.put(SKEWENESS, lStats.getSkewness());
			metrics.put(VARIANCE, lStats.getVariance());
			metrics.put(ENCLOSING_RADIUS, lStats.getEnclosingRadius(1));
		}
		if (includeFitting) {
			getNormStats();
			if (nStats != null && nStats.validFit()) {
				metrics.put(DECAY, nStats.getShollDecay());
				metrics.put(INTERCEPT, nStats.getIntercept());
			}
		}
		return metrics;
	}

	public ArrayList<Double> getMaximaRadii() {

		if (!maximaRadii.isEmpty()) return maximaRadii;
		getLinearStats();
		if (lStats == null) return maximaRadii;
		final ArrayList<UPoint> maximaPoints = lStats.getMaxima();
		for (final UPoint mp : maximaPoints) {
			maximaRadii.add(mp.x);
		}
		return maximaRadii;
	}

	public ArrayList<double[]> getSecondaryMaxima() {

		if (!secondaryMaxima.isEmpty()) return secondaryMaxima;
		getLinearStats();
		if (lStats == null) return secondaryMaxima;

		double[] dataToUse = lStats.getYvalues();
		double variance = lStats.getVariance();
		double absoluteMax = lStats.getMax();
		try {
			if (lStats.validFit() && lStats.getRSquaredOfFit() > 0.90) {
				dataToUse = lStats.getFitYvalues();
				variance = lStats.getVariance(true);
				absoluteMax = lStats.getMax(true);
			}
		} catch (final Exception ignored) {
			SNTUtils.log("Polynomial fit discarded...");
		}
		final int[] allPeakIndices = MaximumFinder.findMaxima(dataToUse, Math.sqrt(variance), false);
		final ArrayList<Integer> filteredIndices = new ArrayList<>();
		for (int i = 0; i < allPeakIndices.length; i++) {
			if (dataToUse[allPeakIndices[i]] < absoluteMax) {
				filteredIndices.add(allPeakIndices[i]);
			}
		}
		final double[] sMaximaRadii = getValues(lStats.getXvalues(), filteredIndices);
		final double[] sMaxima = getValues(dataToUse, filteredIndices);
		for (int i = 0; i < filteredIndices.size(); i++) {
			secondaryMaxima.add(new double[] {sMaximaRadii[i], sMaxima[i]});
		}
		return secondaryMaxima;
	}

	double[] getValues(final double[] array, final ArrayList<Integer> indices) {
		final int size = indices.size();
		final double[] values = new double[size];
		for (int i = 0; i < size; i++)
			values[i] = array[indices.get(i)];
		return values;
	}

	public LinearProfileStats getLinearStats() {
		if (lStats == null) {
			if (!parser.successful())
				parser.parse();
			if (parser.successful())
				lStats = new LinearProfileStats(parser.getProfile());
		}
		return lStats;
	}

	public NormalizedProfileStats getNormStats() {
		if (nStats == null) {
			if (!parser.successful())
				parser.parse();
			if (parser.successful()) {
				nStats = new NormalizedProfileStats(parser.getProfile(),
						(tree.is3D()) ? NormalizedProfileStats.VOLUME : NormalizedProfileStats.AREA);
			}
		}
		return nStats;
	}

	public static void main(final String[] args) throws InterruptedException {
		final ImageJ ij = new ImageJ();
		final SNTService sntService = ij.context().getService(SNTService.class);
		final Tree tree = sntService.demoTrees().get(0);
		final ShollAnalyzer analyzer = new ShollAnalyzer(tree);
		analyzer.getSingleValueMetrics(true).forEach((metric, value) -> {
			System.out.println(metric + ":\t" + value);
		});
		System.out.println("Max occurs at:\t" + String.valueOf(analyzer.getMaximaRadii()));
		analyzer.getSecondaryMaxima().forEach(sMax -> {
			System.out.println("Sec max occur at:\t" + Arrays.toString(sMax));
		});
		analyzer.getLinearStats().getPlot().show();
	}
}